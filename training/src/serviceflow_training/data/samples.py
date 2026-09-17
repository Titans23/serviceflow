"""Shared sample serialization; no legacy dataset generation entrypoint."""
import json
from serviceflow_training.core.contracts import prompt_contract
VERSION = "pilot-v1"  # Preserve frozen sample serialization provenance.
PROMPTS = prompt_contract()["prompts"]

def row(task, q, answer, split, family, *, candidates=None, facts=None, evidence="暂无商品说明文档证据", history=None, required=(), forbidden=(), source=None):
    source = source or ("authored-synthetic://" + split + "/" + family)
    r = {"id": f"{split}-{task}-{family}", "task_type": task, "source_id": source,
         "group_id": split + ":" + family, "template_family": split + ":" + family,
         "split": split, "source_uri": source, "source_revision": VERSION,
         "license": "Project-authored scenario; source snapshot retained; reuse requires review",
         "synthetic": True, "review_status": "draft", "reviewer": "", "reviewed_at": "",
         "heldout_derivative_reviewed": False, "context": {"question": q},
         "snapshot": {"version": VERSION}, "checks": {"required_phrases": list(required), "forbidden_phrases": list(forbidden)},
         "label_provenance": "agent-authored reference, NOT human verified", "messages": []}
    user = q
    if task == "grader":
        r["context"]["candidates"] = candidates or []
        user = json.dumps({"query": q, "candidates": candidates or []}, ensure_ascii=False, separators=(",", ":"))
    if task == "product_answer":
        h = list(history or []) + [{"role": "USER", "content": q}]
        r["context"]["history"] = h
        ground = "结构化商品事实：\n" + json.dumps(facts, ensure_ascii=False, separators=(",", ":")) + "\n\n文档证据：\n" + evidence
        r["snapshot"].update(facts_version=VERSION + ":" + family, evidence_version=VERSION + ":" + family, grounding_context=ground)
        user = "会话上下文：\n" + "".join(x["role"] + "：" + x["content"] + "\n" for x in h) + "\n当前用户问题：\n" + q + "\n\n可信商品上下文：\n" + ground
    r["messages"] = [{"role": "system", "content": PROMPTS[task]}, {"role": "user", "content": user}, {"role": "assistant", "content": answer}]
    return r

def grader(q, texts, selected, split, family, *, sufficient=None):
    candidates = [{"chunkId": f"{family}-c{i+1}", "title": title, "content": content} for i, (title, content) in enumerate(texts)]
    if sufficient is None:
        sufficient = bool(selected)
    if not isinstance(sufficient, bool) or (sufficient and not selected):
        raise ValueError("充分性必须为布尔值，充分证据必须包含支持 ID")
    return row("grader", q, json.dumps({"sufficient": sufficient, "rankedChunkIds": [candidates[i]["chunkId"] for i in selected]}, ensure_ascii=False, separators=(",", ":")), split, family, candidates=candidates)

def fake_product(name, specs=None, price=3100, status="ON_SALE"):
    return {"id": 90001, "sku": "SYNTHETIC-" + name, "name": name, "brand": "合成测试品牌", "model": name, "category": "phone", "specs": specs or {}, "listPrice": price, "saleStatus": status}

def write_rows(path, rows):
    path.write_text("".join(json.dumps(x,ensure_ascii=False)+"\n" for x in rows),encoding="utf-8")
