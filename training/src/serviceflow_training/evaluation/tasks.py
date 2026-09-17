"""Score saved module predictions. No network or automatic semantic judging."""
import argparse
from collections import Counter
import json
from pathlib import Path
import sys

from serviceflow_training.core.contracts import INTENTS, grader_output, normalized, read_rows, require, sha, validate, write_json


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def score(gold, predictions):
    require(bool(gold), "所选评测集合为空")
    expected = {row["id"]: row for row in gold}
    require(len(expected) == len(gold), "参考答案 ID 重复")
    actual = {}
    for row in predictions:
        require(isinstance(row, dict) and isinstance(row.get("id"), str) and isinstance(row.get("output"), str), "预测必须包含字符串 id/output")
        require(row["id"] not in actual, "预测 ID 重复: " + row["id"])
        actual[row["id"]] = row["output"]
    require(set(actual) == set(expected), f"预测 ID 不一致: missing={sorted(set(expected)-set(actual))}, extra={sorted(set(actual)-set(expected))}")
    intent_pairs, grader_rows, phrase_rows = [], [], []
    for id_, row in expected.items():
        output, target, task = actual[id_], row["messages"][2]["content"], row["task_type"]
        if task == "intent":
            intent_pairs.append((target, output))
        elif task == "grader":
            candidates = row["context"]["candidates"]
            truth = grader_output(target, candidates)
            try:
                value = grader_output(output, candidates)
                grader_rows.append({"valid": True, "gold_sufficient": truth["sufficient"], "claimed_sufficient": value["sufficient"], "exact": value == truth})
            except (ValueError, TypeError, KeyError):
                # Malformed IDs with sufficient=true are still a dangerous claim, not hidden as a refusal.
                try:
                    raw = json.loads(output)
                    claimed = raw.get("sufficient") is True if isinstance(raw, dict) else None
                except ValueError:
                    claimed = None
                grader_rows.append({"valid": False, "gold_sufficient": truth["sufficient"], "claimed_sufficient": claimed, "exact": False})
        else:
            checks = row.get("checks", {})
            required, forbidden = checks.get("required_phrases", []), checks.get("forbidden_phrases", [])
            text = normalized(output)
            phrase_rows.append({"id": id_, "task_type": task,
                "required_count": len(required), "required_hits": sum(normalized(p) in text for p in required),
                "all_required_present": all(normalized(p) in text for p in required) if required else None,
                "forbidden_count": len(forbidden), "forbidden_hits": sum(normalized(p) in text for p in forbidden),
                "thinking_markup": "<think>" in output or "</think>" in output,
                "single_line": bool(output.strip()) and "\n" not in output and "\r" not in output})
    per_class = {}
    for name in INTENTS:
        tp = sum(t == name and p == name for t, p in intent_pairs)
        fp = sum(t != name and p == name for t, p in intent_pairs)
        fn = sum(t == name and p != name for t, p in intent_pairs)
        per_class[name] = {"support": sum(t == name for t, _ in intent_pairs), "f1": 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else 0.0}
    insufficient = [x for x in grader_rows if not x["gold_sufficient"]]
    phrase_count = sum(x["required_count"] for x in phrase_rows)
    return {
        "count": len(gold), "tasks": dict(Counter(x["task_type"] for x in gold)),
        "intent": {"count": len(intent_pairs), "legal_rate": ratio(sum(p in INTENTS for _, p in intent_pairs), len(intent_pairs)),
            "macro_f1_five_classes": sum(x["f1"] for x in per_class.values()) / 5 if intent_pairs else None,
            "per_class": per_class, "all_classes_present": all(x["support"] for x in per_class.values())},
        "grader": {"count": len(grader_rows), "legal_json_and_ids_rate": ratio(sum(x["valid"] for x in grader_rows), len(grader_rows)),
            "exact_rate": ratio(sum(x["exact"] for x in grader_rows), len(grader_rows)),
            "insufficient_gold_count": len(insufficient),
            "false_sufficient_rate": ratio(sum(x["claimed_sufficient"] is True for x in insufficient), len(insufficient)),
            "invalid_count": sum(not x["valid"] for x in grader_rows)},
        "phrase_proxy": {"required_phrases_count": phrase_count, "per_phrase_hit_rate": ratio(sum(x["required_hits"] for x in phrase_rows), phrase_count),
            "rows": phrase_rows},
        "not_measured": ["逐项语义事实正确性", "无依据声明", "正确拒答/过度拒答", "首 Token 延迟", "端到端 P95", "超时率", "部署成本", "业务权限/确认/幂等回归"],
        "note": "短语匹配不是语义事实判断；缺少某类意图时五类 Macro-F1 不适合单独解读。",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gold", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--split", choices=("validation", "test"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    all_rows = read_rows(args.gold)
    validate(all_rows)
    report = score([x for x in all_rows if x["split"] == args.split], read_rows(args.predictions))
    report.update({"split": args.split, "gold_sha256": sha(args.gold), "predictions_sha256": sha(args.predictions)})
    write_json(args.output, report)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        sys.exit(2)
