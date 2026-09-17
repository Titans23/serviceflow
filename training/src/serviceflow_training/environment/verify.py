"""Validate installed runtimes and downloaded assets without starting training."""
import argparse
from copy import deepcopy
import hashlib
from importlib.metadata import version
import json
from pathlib import Path
import platform
import subprocess

ROOT = Path(__file__).resolve().parents[4]
REPORT = ROOT / "runtime-data/training/environment-verification"
MODEL = ROOT / "runtime-data/training/models/Qwen3-8B"


def save(name, data):
    REPORT.mkdir(parents=True, exist_ok=True)
    (REPORT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(data, ensure_ascii=False, indent=2))


def runtime(kind):
    import torch
    assert torch.cuda.is_available() and torch.cuda.is_bf16_supported()
    a = torch.ones((256, 256), device="cuda", dtype=torch.bfloat16)
    b = a @ a
    torch.cuda.synchronize()
    assert torch.all(b == 256).item()
    data = {"python": platform.python_version(), "torch": torch.__version__, "cuda_build": torch.version.cuda,
            "gpu": torch.cuda.get_device_name(), "bf16_matmul": "PASS", "transformers": version("transformers")}
    if kind == "train":
        from serviceflow_training.core.contracts import training_runtime
        data.update(training_runtime())
        from transformers import AutoTokenizer
        from llamafactory.data.template import TEMPLATES
        tokenizer = AutoTokenizer.from_pretrained(str(MODEL), local_files_only=True)
        template = deepcopy(TEMPLATES["qwen3"])
        template.enable_thinking = False
        template.fix_special_tokens(tokenizer)
        cases = [
            ("你是客服。", "你好", "你好，请问需要什么帮助？"),
            ("仅返回意图枚举。", "Mate 60 有货吗？", "PRODUCT_QUERY"),
            ("判断证据，仅返回 JSON。", 'query: 退货条件\ncandidates: []', '{"sufficient":false,"rankedChunkIds":[]}'),
            ("改写成一句检索查询。", "不是 Pro 版本，支持快充吗？", "非 Pro 版本支持的快充规格"),
            ("依据事实回答。", "历史对话：USER: 手机\n当前问题：多少钱？\nMySQL 事实：价格未知\n文档证据：无", "暂无可核实的价格。"),
        ]
        for system, user, answer in cases:
            messages = [{"role": "user", "content": user}, {"role": "assistant", "content": answer}]
            prompt, target = template.encode_oneturn(tokenizer, messages, system=system)
            expected = tokenizer.apply_chat_template([{"role": "system", "content": system}, messages[0]], tokenize=True, add_generation_prompt=True, enable_thinking=False)
            assert prompt == expected, "Training/inference prompt token mismatch"
            assert tokenizer.decode(target) == answer + tokenizer.eos_token + "\n"
        data["nonthinking_template_cases_passed"] = len(cases)
        template_path = ROOT / "runtime-data/training/qwen3-nonthinking.jinja"
        original_template = AutoTokenizer.from_pretrained(str(MODEL), local_files_only=True).chat_template
        template_path.write_text("{% set enable_thinking = false %}" + original_template, encoding="utf-8")
        for system, user, _ in cases:
            messages = [{"role": "system", "content": system}, {"role": "user", "content": user}]
            expected = tokenizer.apply_chat_template(messages, tokenize=True, add_generation_prompt=True, enable_thinking=False)
            actual = tokenizer.apply_chat_template(messages, chat_template=template_path.read_text(), tokenize=True, add_generation_prompt=True, enable_thinking=True)
            assert actual == expected
        data["serving_template"] = str(template_path)
        data["serving_template_sha256"] = hashlib.sha256(template_path.read_bytes()).hexdigest()
    else:
        import vllm
        import vllm._C
        data["vllm"] = vllm.__version__
        data["vllm_cuda_extension_import"] = "PASS"
    save(kind + "-runtime.json", data)


def assets():
    from serviceflow_training.core.contracts import read_json, sha, require
    raw = ROOT / "local-datasets/serviceflow/raw/grader-specialist-exp-01"
    manifest = read_json(raw / "manifest.json")
    data = {"sources_verified": 0, "scope": "final_grader_sources_and_base_model"}
    for item in manifest["sources"]:
        folder = raw / item["id"]
        require(folder.resolve().parent == raw.resolve(), "Invalid source path")
        for name, field in [("source.html", "html_sha256"), ("source.txt", "text_sha256")]:
            require(sha(folder / name) == item[field], "Source changed: " + item["id"])
        data["sources_verified"] += 1
    metadata = json.loads((ROOT / "runtime-data/training/assets/huggingface-model-metadata.json").read_text(encoding="utf-8"))
    parity = {}
    for entry in metadata["siblings"]:
        name = entry["rfilename"]
        if name == ".gitattributes" or name.endswith(".safetensors"):
            continue
        content = (MODEL / name).read_bytes()
        sha = entry.get("lfs", {}).get("sha256")
        actual = hashlib.sha256(content).hexdigest() if sha else hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()
        parity[name] = actual == (sha or entry["blobId"])
        if name not in {"README.md", "LICENSE"}:
            assert parity[name], "Model core file differs from original: " + name
    data["model_small_file_original_parity"] = parity
    save("assets-validation.json", data)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("train", "infer", "assets"))
    args = parser.parse_args()
    assets() if args.kind == "assets" else runtime(args.kind)
