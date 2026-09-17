"""Offline reviewed-data gates and LLaMA-Factory configuration preparation."""
import argparse
from copy import deepcopy
from collections import Counter
import hashlib
import importlib.metadata
import inspect
import json
from pathlib import Path
import re
import subprocess
import sys
import unicodedata

ROOT = Path(__file__).resolve().parents[4]
SERVER = ROOT / "apps/serviceflow-server/src/main/java/com/serviceflow"
PROTECTED = ROOT / "quality/evaluation/datasets/serviceflow-eval-200.json"
HELDOUT_REGISTRY = ROOT / "training/data/heldout-registry.json"
EVALUATION_ONLY_REGISTRY = ROOT / "training/data/evaluation-only-registry.json"
GRADER_HELDOUT_REGISTRY = ROOT / "training/data/grader-specialist-heldout-registry.json"
TASKS = {"intent", "grader", "rewrite", "product_answer", "chat"}
INTENTS = ("CHAT", "PRODUCT_QUERY", "KNOWLEDGE_QUERY", "ORDER_QUERY", "COMPLAINT")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def read_rows(path):
    rows = []
    for line_number, line in enumerate(Path(path).read_text(encoding="utf-8-sig").splitlines(), 1):
        if line.strip():
            try:
                rows.append(json.loads(line))
            except ValueError as error:
                raise ValueError(f"JSONL line {line_number}: {error}") from error
    require(rows, "数据文件为空")
    return rows


def java_constant(text, name):
    literal = r'"(?:\\.|[^"\\])*"'
    match = re.search(r'\b' + re.escape(name) + r'\s*=\s*(' + literal + r'(?:\s*\+\s*' + literal + r')*)\s*;', text)
    require(match, f"Java 提示词常量未找到: {name}")
    return "".join(json.loads(value) for value in re.findall(literal, match.group(1)))


def prompt_contract():
    gateway = SERVER / "ai/CloudAiGateway.java"
    chat = SERVER / "service/impl/ChatServiceImpl.java"
    workflow = SERVER / "agent/CustomerWorkflow.java"
    gateway_text, chat_text = gateway.read_text(encoding="utf-8"), chat.read_text(encoding="utf-8")
    plain = "你是 ServiceFlow 客服，只回答售前商品和售后服务范围的问题。"
    require(json.dumps(plain, ensure_ascii=False) in chat_text, "普通聊天提示词已变化，需要更新输入契约")
    return {
        "prompts": {
            "intent": java_constant(gateway_text, "INTENT_SYSTEM_PROMPT"),
            "grader": java_constant(gateway_text, "EVIDENCE_SYSTEM_PROMPT"),
            "rewrite": java_constant(gateway_text, "REWRITE_SYSTEM_PROMPT"),
            "product_answer": java_constant(chat_text, "PRODUCT_SYSTEM_PROMPT"),
            "chat": plain,
        },
        "java_sha256": {str(p.relative_to(ROOT)).replace("\\", "/"): sha(p) for p in (gateway, chat, workflow)},
        "heldout_registry_sha256": sha(HELDOUT_REGISTRY) if HELDOUT_REGISTRY.exists() else "none",
    }


def normalized(text):
    return re.sub(r"[\W_]+", "", unicodedata.normalize("NFKC", text).casefold())


def grader_output(text, candidates):
    value = json.loads(text)
    require(isinstance(value, dict) and set(value) == {"sufficient", "rankedChunkIds"}, "Grader 必须且只能有 sufficient/rankedChunkIds")
    require(type(value["sufficient"]) is bool, "sufficient 必须是布尔值")
    ids = value["rankedChunkIds"]
    require(isinstance(ids, list) and all(isinstance(x, str) for x in ids), "rankedChunkIds 必须为字符串数组")
    require(len(ids) <= 5 and len(set(ids)) == len(ids), "rankedChunkIds 超过五条或重复")
    require(set(ids) <= {x["chunkId"] for x in candidates}, "Grader 包含候选外 chunkId")
    require(not value["sufficient"] or bool(ids), "证据充分时必须有合法 chunkId")
    return value


def validate(rows, protected_path=PROTECTED, require_review=True):
    contract = prompt_contract()
    protected = read_json(protected_path)
    protected_ids = {row["id"] for row in protected}
    questions = {normalized(row["question"]) for row in protected}
    heldout = read_json(HELDOUT_REGISTRY) if HELDOUT_REGISTRY.exists() else {"cases": []}
    heldout_questions = {x["normalized_question"] for x in heldout["cases"]}
    heldout_sources = {x[field] for x in heldout["cases"] for field in ("source_id", "group_id", "template_family")}
    exclusion = read_json(EVALUATION_ONLY_REGISTRY) if EVALUATION_ONLY_REGISTRY.exists() else {}
    heldout_questions.update(exclusion.get("questions", []))
    heldout_sources.update(exclusion.get("sources", []))
    grader_exclusion = read_json(GRADER_HELDOUT_REGISTRY) if GRADER_HELDOUT_REGISTRY.exists() else {}
    heldout_questions.update(grader_exclusion.get("questions", []))
    heldout_sources.update(grader_exclusion.get("sources", []))
    ids, seen_inputs, ownership = set(), set(), {}
    for number, row in enumerate(rows, 1):
        require(isinstance(row, dict), f"第 {number} 行必须为对象")
        label = str(row.get("id", number))
        for field in ("id", "source_id", "group_id", "template_family", "source_uri", "source_revision", "license"):
            require(isinstance(row.get(field), str) and row[field].strip(), f"{label}: 缺少 {field}")
        require(row["id"] not in ids, f"重复 ID: {label}")
        ids.add(row["id"])
        require(row.get("task_type") in TASKS, f"{label}: 未知任务")
        require(row.get("split") in {"train", "validation", "test"}, f"{label}: 非法 split")
        if row.get("evaluation_only"):
            require(row["split"] == "test", f"{label}: evaluation_only 不得进入训练或验证")
        if row.get("review_status") == "ai_approved":
            require(row.get("reviewer_kind") == "ai" and row.get("reviewer") == "Codex", f"{label}: AI 审核身份不完整")
            authorization = row.get("review_authorization", {})
            require(authorization.get("user_instruction") == "审核由你完成  然后进行sft", f"{label}: 缺少用户授权 AI 审核")
            require(authorization.get("scope") in {"local_pilot_sft_not_production", "business_candidate_sft_local_integration", "evaluation_only"}, f"{label}: AI 审核范围不符")
            if authorization.get("scope") == "evaluation_only":
                require(row.get("evaluation_only") is True and row["split"] == "test", f"{label}: 评测审核不能授权训练")
                require(bool(row.get("atomic_review")), f"{label}: 缺少逐项评测审核")
            if authorization.get("scope") == "business_candidate_sft_local_integration":
                require(authorization.get("implementation_instruction") == "帮我进行正式训练和接入", f"{label}: 缺少本轮实施授权")
                require(isinstance(row.get("atomic_review"), list) and bool(row["atomic_review"]), f"{label}: 缺少逐项审核")
            require(isinstance(row.get("reviewed_at"), str) and row["reviewed_at"].strip(), f"{label}: 缺少审核时间")
            require(row.get("heldout_derivative_reviewed") is True and row.get("heldout_review_kind") == "ai", f"{label}: 缺少 AI 来源审查")
            require(isinstance(row.get("audit_notes"), list) and all(isinstance(n,str) and n.strip() for n in row["audit_notes"]) and row["audit_notes"], f"{label}: 缺少逐条审核依据")
        elif require_review or row.get("review_status") == "approved":
            require(row.get("review_status") == "approved", f"{label}: 未经人工审核")
            require(all(isinstance(row.get(k), str) and row[k].strip() for k in ("reviewer", "reviewed_at")), f"{label}: 缺少真实审核记录")
            require(row.get("heldout_derivative_reviewed") is True, f"{label}: 必须人工确认不是保留评测的改写")
        else:
            require(row.get("review_status") == "draft" and row.get("reviewer") == "" and row.get("reviewed_at") == "" and row.get("heldout_derivative_reviewed") is False, f"{label}: 草稿不能带有虚构人工审核声明")
        require(type(row.get("synthetic")) is bool, f"{label}: synthetic 必须显式标记")
        for field in ("source_id", "group_id", "template_family"):
            key = (field, row[field])
            require(key not in ownership or ownership[key] == row["split"], f"{label}: {field} 跨集合泄漏")
            ownership[key] = row["split"]
        require(row["source_id"] not in protected_ids and row["group_id"] not in protected_ids, f"{label}: 保留评测来源")
        messages, context = row.get("messages"), row.get("context")
        require(isinstance(messages, list) and len(messages) == 3, f"{label}: 必须为 system/user/assistant 三条消息")
        require(all(isinstance(x, dict) for x in messages), f"{label}: message 非对象")
        require([x.get("role") for x in messages] == ["system", "user", "assistant"], f"{label}: 消息角色不匹配线上接口")
        require(all(isinstance(x.get("content"), str) and x["content"].strip() for x in messages), f"{label}: 空消息")
        require(messages[0]["content"] == contract["prompts"][row["task_type"]], f"{label}: system 与当前 Java 不一致")
        require(isinstance(context, dict) and isinstance(context.get("question"), str) and context["question"].strip(), f"{label}: 缺少原始 question")
        question = context["question"]
        if row["split"] != "test":
            question_text, user_text = normalized(question), normalized(messages[1]["content"])
            require(not any(q and (q in question_text or q in user_text) for q in questions | heldout_questions), f"{label}: 包含保留评测问题")
            require(not any(row[k] in heldout_sources for k in ("source_id", "group_id", "template_family")), f"{label}: 独立基线来源不得进入训练或验证集")
        identity = (row["task_type"], normalized(messages[1]["content"]))
        require(identity not in seen_inputs, f"{label}: 重复模型输入")
        seen_inputs.add(identity)
        user, target = messages[1]["content"], messages[2]["content"]
        require("<think>" not in target and "</think>" not in target, f"{label}: 目标不得手工携带思考标签；交给固定模板")
        task = row["task_type"]
        snapshot = row.get("snapshot")
        require(isinstance(snapshot, dict) and isinstance(snapshot.get("version"), str) and snapshot["version"], f"{label}: 缺少快照版本")
        if task == "grader":
            candidates = context.get("candidates")
            require(isinstance(candidates, list), f"{label}: candidates 必须为数组")
            require(all(isinstance(x, dict) and set(x) == {"chunkId", "title", "content"} and all(isinstance(v, str) and v.strip() for v in x.values()) for x in candidates), f"{label}: candidate 字段错误")
            require(len({x["chunkId"] for x in candidates}) == len(candidates), f"{label}: 候选 ID 重复")
            require(json.loads(user) == {"query": question, "candidates": candidates}, f"{label}: Grader user 不匹配 query/candidates")
            grader_output(target, candidates)
        elif task == "product_answer":
            history = context.get("history")
            require(isinstance(history, list) and 1 <= len(history) <= 8, f"{label}: 必须保留实际最近 1–8 条历史")
            require(all(isinstance(x, dict) and x.get("role") in {"USER", "ASSISTANT"} and isinstance(x.get("content"), str) for x in history), f"{label}: history 格式错误")
            require(history[-1] == {"role": "USER", "content": question}, f"{label}: 线上先 append USER，历史末条应是本轮问题")
            grounding = snapshot.get("grounding_context")
            require(isinstance(grounding, str) and grounding.startswith("结构化商品事实：\n") and "\n\n文档证据：\n" in grounding, f"{label}: 缺少原样 grounding_context")
            require(snapshot.get("facts_version") and snapshot.get("evidence_version"), f"{label}: 必须记录事实和证据版本")
            expected = "会话上下文：\n" + "".join(x["role"] + "：" + x["content"] + "\n" for x in history)
            expected += "\n当前用户问题：\n" + question + "\n\n可信商品上下文：\n" + grounding
            require(user == expected, f"{label}: 商品 user 不匹配当前 Java 序列化")
        else:
            require(user == question, f"{label}: 此任务线上只接收当前问题")
            if task == "intent":
                require(target in INTENTS, f"{label}: 非法意图枚举")
            if task == "rewrite":
                require("\n" not in target and "\r" not in target, f"{label}: 改写必须为单行")
        checks = row.get("checks", {})
        require(isinstance(checks, dict), f"{label}: checks 必须为对象")
        for field in ("required_phrases", "forbidden_phrases"):
            require(isinstance(checks.get(field, []), list) and all(isinstance(x, str) and x.strip() for x in checks.get(field, [])), f"{label}: 非法 {field}")
    return {"total": len(rows), "by_split": dict(Counter(x["split"] for x in rows)), "by_task": dict(Counter(x["task_type"] for x in rows)), "contract": contract}


def export_dataset(input_path, output):
    rows = read_rows(input_path)
    summary = validate(rows)
    require(not any(r.get("evaluation_only") for r in rows), "独立评测数据不得用于训练导出")
    require({x["split"] for x in rows} == {"train", "validation", "test"}, "导出需同时提供独立 train/validation/test")
    require(not output.exists(), "导出目录已存在；使用新的数据版本目录")
    output.mkdir(parents=True)
    info = {}
    for split in ("train", "validation", "test"):
        filename = split + ".json"
        write_json(output / filename, [{"messages": x["messages"]} for x in rows if x["split"] == split])
        # Held-out test stays out of LLaMA-Factory registration entirely.
        if split != "test":
            info["serviceflow_" + split] = {
                "file_name": filename, "formatting": "sharegpt", "columns": {"messages": "messages"},
                "tags": {"role_tag": "role", "content_tag": "content", "user_tag": "user", "assistant_tag": "assistant", "system_tag": "system"}}
    write_json(output / "dataset_info.json", info)
    write_json(output / "manifest.json", {"input_sha256": sha(input_path), "protected_eval_sha256": sha(PROTECTED), **summary,
        "files_sha256": {name: sha(output / name) for name in ("train.json", "validation.json", "test.json", "dataset_info.json")}})
    return summary


def length_summary(lengths, cutoff):
    require(bool(lengths), "没有长度记录")
    values = sorted(x["total_tokens"] for x in lengths)
    over = [x for x in lengths if x["total_tokens"] > cutoff]
    return {"count": len(values), "max": max(values), "p50": values[int((len(values)-1)*0.5)], "p95": values[int((len(values)-1)*0.95)],
        "cutoff": cutoff, "over_limit": over, "over_limit_fraction": len(over)/len(values), "truncation_performed": False}


def training_runtime():
    import llamafactory.data.template as template_module
    import torch
    template_source = Path(inspect.getfile(template_module)).resolve()
    result = subprocess.run(["git", "-C", str(template_source.parent), "rev-parse", "HEAD"], capture_output=True, text=True, timeout=10)
    require(result.returncode == 0 and re.fullmatch(r"[0-9a-f]{40}", result.stdout.strip()), "请从固定 commit 的 LLaMA-Factory checkout 安装；未取得框架 commit")
    dirty = subprocess.run(["git", "-C", str(template_source.parent), "status", "--porcelain", "--untracked-files=no"], capture_output=True, text=True, timeout=10)
    require(dirty.returncode == 0 and not dirty.stdout.strip(), "LLaMA-Factory checkout 有未提交修改，不能声明为固定 commit")
    return {"python": sys.version.split()[0], "torch": torch.__version__, "cuda_build": torch.version.cuda,
        **{name: importlib.metadata.version(name) for name in ("transformers", "datasets", "peft", "accelerate")},
        "llamafactory_commit": result.stdout.strip(), "template_source_sha256": sha(template_source)}


def assert_token_parity(source, serving, sample_id):
    require(source == serving, f"{sample_id}: 训练和推理模板 token 不一致，停止而不是隐式替换模板")


def measure_lengths(input_path, model_path, output):
    rows = read_rows(input_path)
    summary = validate(rows)
    require(model_path.is_dir(), "必须指定已下载的本地模型路径")
    # Imports deliberately deferred. No framework installation or network fallback.
    from transformers import AutoTokenizer
    from llamafactory.data.template import TEMPLATES
    runtime = training_runtime()
    tokenizer = AutoTokenizer.from_pretrained(str(model_path), local_files_only=True, trust_remote_code=False)
    template = deepcopy(TEMPLATES["qwen3"])
    template.enable_thinking = False
    template.fix_special_tokens(tokenizer)
    lengths = []
    for row in rows:
        system, user, assistant = [x["content"] for x in row["messages"]]
        source, target = template.encode_oneturn(tokenizer, [{"role": "user", "content": user}, {"role": "assistant", "content": assistant}], system=system)
        serving = tokenizer.apply_chat_template(row["messages"][:2], tokenize=True, add_generation_prompt=True, enable_thinking=False)
        assert_token_parity(source, serving, row["id"])
        require(bool(target), f"{row['id']}: 目标 token 为空")
        lengths.append({"id": row["id"], "input_tokens": len(source), "output_tokens": len(target), "total_tokens": len(source) + len(target) + int(template.efficient_eos)})
    report = {**length_summary(lengths, 4096), "input_sha256": sha(input_path), "model_path": str(model_path.resolve()),
        "template": "qwen3", "serving_token_parity": True, "contract": summary["contract"],
        "tokenizer_files_sha256": tokenizer_hashes(model_path), "training_runtime": runtime, "lengths": lengths}
    write_json(output, report)
    require(not report["over_limit"], "存在超长样本，报告已保存；请人工修改后重验，不会截断")
    return report


def tokenizer_hashes(model):
    files = [x for x in model.iterdir() if x.is_file() and (x.name.startswith("tokenizer") or x.name in {"special_tokens_map.json", "chat_template.jinja", "added_tokens.json"})]
    require(files, "没有本地 tokenizer 文件")
    return {x.name: sha(x) for x in sorted(files)}


def resolved(value):
    if isinstance(value, dict):
        return bool(value) and all(resolved(x) for x in value.values())
    return value is not None and value != "" and value not in ("UNRESOLVED", "main", "master", "latest")


def prepare(args):
    from serviceflow_training.core.hashing import sha as file_sha
    rows = read_rows(args.input)
    summary = validate(rows)
    require(not any(r.get("evaluation_only") for r in rows), "独立评测数据不得准备训练")
    lock, lengths = read_json(args.lock), read_json(args.length_report)
    require(resolved(lock) and lock.get("status") == "VERIFIED", "实验锁未完成：需实际固定版本并标记 VERIFIED")
    if any(r.get("review_status") == "ai_approved" for r in rows):
        scopes = {r.get("review_authorization", {}).get("scope") for r in rows if r.get("review_status") == "ai_approved"}
        expected_scope = "ai-reviewed-business-candidate" if "business_candidate_sft_local_integration" in scopes else "ai-reviewed-local-pilot"
        require(lock.get("review_scope") == expected_scope, "实验锁与本轮 AI 审核范围不一致")
    require(lock.get("template") == "qwen3" and lock.get("enable_thinking") is False, "仅接受非思考模板")
    require(type(lock.get("seed")) is int, "seed 必须是整数")
    require(lock["base_model"]["original_id"] == "Qwen/Qwen3-8B", "本阶段基座固定为 Qwen3-8B")
    require(re.fullmatch(r"[0-9a-f]{40}", lock["base_model"]["original_revision"]), "原始模型 revision 必须为固定完整 commit")
    require(re.fullmatch(r"[0-9a-f]{40}", lock["training_environment"]["llamafactory_commit"]), "框架必须固定完整 commit")
    runtime = training_runtime()
    require(lengths.get("training_runtime") == runtime, "训练运行库或模板在测长后发生变化，必须重验")
    require(all(lock["training_environment"].get(key) == value for key, value in runtime.items() if key != "template_source_sha256"), "当前训练环境与实验锁不一致")
    model = Path(lock["base_model"]["local_path"])
    require(model.is_absolute() and model.is_dir(), "基座本地绝对路径不存在")
    hashes = read_json(lock["base_model"]["files_sha256_manifest"])
    require(isinstance(hashes, dict) and hashes and any(name.endswith(".safetensors") for name in hashes), "必须提供基座权重 SHA-256 清单")
    required_files = {str(x.relative_to(model)).replace("\\", "/") for x in model.rglob("*.safetensors")}
    required_files |= set(tokenizer_hashes(model)) | {"config.json", "model.safetensors.index.json"}
    require(required_files <= set(hashes), "模型校验清单必须覆盖全部权重分片、索引、配置和 tokenizer")
    for name, expected in hashes.items():
        path = (model / name).resolve()
        require(path.is_relative_to(model.resolve()) and path.is_file(), "模型清单路径无效")
        require(file_sha(path) == expected, f"模型文件校验失败: {name}")
    require(Path(lock["baseline_report"]).is_file(), "缺少原始模型基线报告")
    for path in (lock["base_model"]["license_record"], lock["training_environment"]["pip_freeze_file"], lock["inference_environment"]["pip_freeze_file"]):
        require(Path(path).is_file(), f"锁文件引用记录不存在: {path}")
    digest = sha(args.input)
    manifest = read_json(args.dataset_dir / "manifest.json")
    require(manifest["input_sha256"] == digest == lengths["input_sha256"], "审核数据、导出数据、长度报告不是同一版本")
    require(manifest["protected_eval_sha256"] == sha(PROTECTED), "保留评测集发生变化")
    require(manifest["contract"] == summary["contract"] == lengths["contract"], "Java 输入契约已变化，必须重新导出与测长")
    require(lengths.get("count") == len(rows) and lengths.get("cutoff") == 4096 and lengths.get("max", 99999) <= 4096 and not lengths.get("over_limit") and lengths.get("serving_token_parity") is True, "长度检查未通过")
    require(Path(lengths["model_path"]).resolve() == model.resolve() and lengths["tokenizer_files_sha256"] == tokenizer_hashes(model), "tokenizer/模型与测长报告不一致")
    require(set(manifest["files_sha256"]) == {"train.json", "validation.json", "test.json", "dataset_info.json"}, "导出清单不完整")
    for name, expected in manifest["files_sha256"].items():
        require(sha(args.dataset_dir / name) == expected, f"导出数据被修改: {name}")
    require(not args.run_dir.exists(), "实验目录已存在，避免覆盖 checkpoint")
    config = (ROOT / "training/configs/sft.yaml.template").read_text(encoding="utf-8")
    replacements = {"__MODEL_PATH__": str(model.resolve()), "__DATASET_DIR__": str(args.dataset_dir.resolve()), "__OUTPUT_DIR__": str((args.run_dir / "checkpoints").resolve())}
    for key, value in replacements.items():
        config = config.replace(key, json.dumps(value))
    config = config.replace("__MAX_STEPS__", "50" if args.stage == "smoke" else "-1").replace("__SEED__", str(lock["seed"]))
    args.run_dir.mkdir(parents=True)
    (args.run_dir / "train.yaml").write_text(config, encoding="utf-8")
    write_json(args.run_dir / "run-manifest.json", {"stage": args.stage, "input_sha256": digest, "lock": lock, "lock_sha256": sha(args.lock), "length_report_sha256": sha(args.length_report), "contract": summary["contract"], "training_started": False})
    return {"config": str(args.run_dir / "train.yaml"), "training_started": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    subs.add_parser("prompts")
    for name in ("inspect", "validate", "export", "lengths", "prepare"):
        sub = subs.add_parser(name)
        sub.add_argument("--input", required=True, type=Path)
        if name in {"export", "lengths"}:
            sub.add_argument("--output", required=True, type=Path)
        if name == "lengths":
            sub.add_argument("--model", required=True, type=Path)
        if name == "prepare":
            for option in ("dataset-dir", "lock", "length-report", "run-dir"):
                sub.add_argument("--" + option, required=True, type=Path)
            sub.add_argument("--stage", choices=("smoke", "full"), required=True)
    args = parser.parse_args()
    if args.command == "prompts":
        result = prompt_contract()
    elif args.command in {"inspect", "validate"}:
        result = validate(read_rows(args.input), require_review=args.command == "validate")
    elif args.command == "export":
        result = export_dataset(args.input, args.output)
    elif args.command == "lengths":
        result = measure_lengths(args.input, args.model, args.output)
    else:
        result = prepare(args)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        main()
    except (ValueError, OSError, KeyError, ImportError, TypeError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        sys.exit(2)
