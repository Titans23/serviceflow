"""Synthetic in-memory fixtures only; never export these as training data."""
import argparse
from copy import deepcopy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from serviceflow_training.core.contracts import (ROOT, PROTECTED, INTENTS, assert_token_parity, export_dataset, grader_output, java_constant,
                      length_summary, prepare, prompt_contract, read_json, sha, validate, write_json)
from serviceflow_training.evaluation.tasks import score


def fixture(id_="fixture-a", task="intent", split="train"):
    question = "合成夹具问题 " + id_
    row = {
        "id": id_, "task_type": task, "source_id": "origin-" + id_, "group_id": "group-" + id_,
        "template_family": "family-" + id_, "split": split, "source_uri": "fixture://" + id_,
        "source_revision": "fixture-v1", "license": "fixture-only", "synthetic": True,
        "review_status": "approved", "reviewer": "UNIT-TEST-FIXTURE-NOT-REAL-REVIEW", "reviewed_at": "2026-09-15",
        "heldout_derivative_reviewed": True, "context": {"question": question}, "snapshot": {"version": "fixture-v1"},
        "messages": [{"role": "system", "content": prompt_contract()["prompts"][task]}, {"role": "user", "content": question},
                     {"role": "assistant", "content": "CHAT" if task == "intent" else "夹具回答"}], "checks": {},
    }
    if task == "grader":
        row["context"]["candidates"] = [{"chunkId": "fixture-chunk", "title": "夹具", "content": "仅用于测试"}]
        row["messages"][1]["content"] = json.dumps({"query": question, "candidates": row["context"]["candidates"]}, ensure_ascii=False)
        row["messages"][2]["content"] = '{"sufficient":false,"rankedChunkIds":[]}'
    if task == "product_answer":
        row["context"]["history"] = [{"role": "USER", "content": question}]
        grounding = '结构化商品事实：\n{"sku":"SYNTHETIC-TEST-ONLY"}\n\n文档证据：\n暂无商品说明文档证据'
        row["snapshot"].update({"facts_version": "fixture-v1", "evidence_version": "none", "grounding_context": grounding})
        row["messages"][1]["content"] = "会话上下文：\nUSER：" + question + "\n\n当前用户问题：\n" + question + "\n\n可信商品上下文：\n" + grounding
    return row


class PipelineTests(unittest.TestCase):
    def test_reads_java_literals_without_executing_code(self):
        text = 'private static final String X = "中文" + "\\n\\\"quote\\\"";'
        self.assertEqual(java_constant(text, "X"), '中文\n"quote"')
        self.assertEqual(set(prompt_contract()["prompts"]), {"intent", "grader", "rewrite", "product_answer", "chat"})

    def test_all_five_task_contracts(self):
        rows = [fixture("fixture-" + task, task) for task in ("intent", "grader", "rewrite", "product_answer", "chat")]
        self.assertEqual(validate(rows)["total"], 5)

    def test_draft_cannot_be_exported(self):
        row = fixture()
        row["review_status"] = "draft"
        with self.assertRaisesRegex(ValueError, "人工审核"):
            validate([row])

    def test_leakage_review_is_required(self):
        row = fixture()
        row["heldout_derivative_reviewed"] = False
        with self.assertRaisesRegex(ValueError, "人工确认"):
            validate([row])

    def test_group_source_and_template_leaks(self):
        for field in ("group_id", "source_id", "template_family"):
            with self.subTest(field=field):
                a, b = fixture(), fixture("fixture-b", split="test")
                b[field] = a[field]
                with self.assertRaisesRegex(ValueError, "跨集合泄漏"):
                    validate([a, b])

    def test_protected_question_embedded_in_training_prompt(self):
        row = fixture()
        q = read_json(PROTECTED)[0]["question"]
        row["context"]["question"] = "请问：" + q
        row["messages"][1]["content"] = "请问：" + q
        with self.assertRaisesRegex(ValueError, "保留评测问题"):
            validate([row])

    def test_same_input_different_ids_rejected(self):
        a, b = fixture(), fixture("fixture-b")
        b["messages"] = deepcopy(a["messages"])
        b["context"] = deepcopy(a["context"])
        with self.assertRaisesRegex(ValueError, "重复模型输入"):
            validate([a, b])

    def test_prompt_drift_is_rejected(self):
        row = fixture()
        row["messages"][0]["content"] += "额外指令"
        with self.assertRaisesRegex(ValueError, "当前 Java"):
            validate([row])

    def test_history_cannot_be_added_to_classifier(self):
        row = fixture()
        row["messages"].insert(1, {"role": "user", "content": "上轮问题"})
        with self.assertRaisesRegex(ValueError, "三条消息"):
            validate([row])

    def test_product_history_must_include_current_user(self):
        row = fixture(task="product_answer")
        row["context"]["history"][-1]["content"] = "不同问题"
        with self.assertRaisesRegex(ValueError, "历史末条"):
            validate([row])

    def test_grader_rejects_invalid_ids_boolean_and_empty_sufficient(self):
        candidates = fixture(task="grader")["context"]["candidates"]
        invalid = [
            '{"sufficient":true,"rankedChunkIds":["invented"]}',
            '{"sufficient":"false","rankedChunkIds":[]}',
            '{"sufficient":true,"rankedChunkIds":[]}',
            '{"sufficient":true,"rankedChunkIds":["fixture-chunk","fixture-chunk"]}',
            '```json\n{"sufficient":false,"rankedChunkIds":[]}\n```',
        ]
        for text in invalid:
            with self.subTest(text=text), self.assertRaises(ValueError):
                grader_output(text, candidates)

    def test_export_keeps_test_out_of_training_registry(self):
        rows = [fixture("fixture-" + split, split=split) for split in ("train", "validation", "test")]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "reviewed.jsonl"
            source.write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in rows), encoding="utf-8")
            export_dataset(source, root / "export")
            info = read_json(root / "export/dataset_info.json")
            self.assertEqual(set(info), {"serviceflow_train", "serviceflow_validation"})
            self.assertEqual(read_json(root / "export/manifest.json")["input_sha256"], sha(source))
            with self.assertRaisesRegex(ValueError, "已存在"):
                export_dataset(source, root / "export")

    def test_length_summary_includes_answer_and_does_not_truncate(self):
        value = length_summary([{"id": "x", "total_tokens": 4097}], 4096)
        self.assertEqual(value["over_limit_fraction"], 1)
        self.assertFalse(value["truncation_performed"])

    def test_empty_thinking_prefix_mismatch_stops_pipeline(self):
        with self.assertRaisesRegex(ValueError, "token 不一致"):
            assert_token_parity([1, 2], [1, 2, 99, 100], "fixture")

    def test_prepare_writes_smoke_config_and_rejects_tampered_export(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "input.jsonl"
            rows = [fixture("fixture-" + split, split=split) for split in ("train", "validation", "test")]
            source.write_text("\n".join(json.dumps(x) for x in rows), encoding="utf-8")
            export_dataset(source, root / "data")
            model = root / "model"
            model.mkdir()
            # Deliberately fake, tiny files. This tests orchestration only, never a model load.
            for name in ("config.json", "tokenizer_config.json", "model.safetensors.index.json", "fixture.safetensors"):
                (model / name).write_text("{}", encoding="utf-8")
            write_json(root / "hashes.json", {x.name: sha(x) for x in model.iterdir()})
            (root / "evidence.txt").write_text("UNIT TEST ONLY", encoding="utf-8")
            runtime = {"python": "fixture", "torch": "fixture", "cuda_build": "fixture", "transformers": "fixture", "datasets": "fixture", "peft": "fixture", "accelerate": "fixture", "llamafactory_commit": "a" * 40, "template_source_sha256": "fixture"}
            lock = read_json(ROOT / "training/configs/environment.lock.example.json")
            lock.update({"status": "VERIFIED", "dataset_version": "fixture", "knowledge_snapshot": "fixture", "baseline_report": str(root / "evidence.txt")})
            lock["base_model"].update({"original_revision": "b" * 40, "download_revision": "pinned-fixture", "local_path": str(model), "files_sha256_manifest": str(root / "hashes.json"), "license_record": str(root / "evidence.txt")})
            lock["training_environment"].update({key: value for key, value in runtime.items() if key != "template_source_sha256"})
            lock["training_environment"]["pip_freeze_file"] = str(root / "evidence.txt")
            lock["inference_environment"] = {key: (str(root / "evidence.txt") if key == "pip_freeze_file" else "fixture") for key in lock["inference_environment"]}
            write_json(root / "lock.json", lock)
            write_json(root / "lengths.json", {"input_sha256": sha(source), "training_runtime": runtime, "contract": prompt_contract(), "count": 3, "cutoff": 4096, "max": 100, "over_limit": [], "serving_token_parity": True, "model_path": str(model), "tokenizer_files_sha256": {"tokenizer_config.json": sha(model / "tokenizer_config.json")}})
            args = argparse.Namespace(input=source, lock=root / "lock.json", length_report=root / "lengths.json", dataset_dir=root / "data", run_dir=root / "smoke", stage="smoke")
            with patch("serviceflow_training.core.contracts.training_runtime", return_value=runtime):
                result = prepare(args)
                self.assertFalse(result["training_started"])
                config = (root / "smoke/train.yaml").read_text(encoding="utf-8")
                self.assertIn("max_steps: 50", config)
                self.assertIn("train_on_prompt: false", config)
                self.assertIn("enable_thinking: false", config)
                (root / "data/train.json").write_text("[]", encoding="utf-8")
                args.run_dir = root / "next-run"
                with self.assertRaisesRegex(ValueError, "导出数据被修改"):
                    prepare(args)

    def test_unresolved_lock_cannot_prepare_training(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "input.jsonl"
            source.write_text(json.dumps(fixture()), encoding="utf-8")
            write_json(root / "lengths.json", {})
            args = argparse.Namespace(input=source, lock=ROOT / "training/configs/environment.lock.example.json", length_report=root / "lengths.json")
            with self.assertRaisesRegex(ValueError, "实验锁未完成"):
                prepare(args)


class ScoringTests(unittest.TestCase):
    def test_missing_prediction_is_not_silently_excluded(self):
        with self.assertRaisesRegex(ValueError, "missing"):
            score([fixture()], [])

    def test_duplicate_or_extra_prediction_rejected(self):
        row = fixture()
        prediction = {"id": row["id"], "output": "CHAT"}
        with self.assertRaisesRegex(ValueError, "重复"):
            score([row], [prediction, prediction])
        with self.assertRaisesRegex(ValueError, "extra"):
            score([row], [prediction, {"id": "extra", "output": "CHAT"}])

    def test_five_class_macro_f1_and_illegal_label(self):
        rows, predictions = [], []
        for index, intent in enumerate(INTENTS):
            row = fixture("label-" + str(index), split="test")
            row["messages"][2]["content"] = intent
            rows.append(row)
            predictions.append({"id": row["id"], "output": intent})
        self.assertEqual(score(rows, predictions)["intent"]["macro_f1_five_classes"], 1)
        predictions[0]["output"] = "CHAT，因为这是聊天"
        self.assertEqual(score(rows, predictions)["intent"]["legal_rate"], 0.8)

    def test_invented_id_with_true_is_still_false_sufficiency(self):
        row = fixture(task="grader", split="test")
        report = score([row], [{"id": row["id"], "output": '{"sufficient":true,"rankedChunkIds":["invented"]}'}])
        self.assertEqual(report["grader"]["false_sufficient_rate"], 1)
        self.assertEqual(report["grader"]["invalid_count"], 1)

    def test_required_facts_are_per_item_not_any_match(self):
        row = fixture(task="product_answer", split="test")
        row["checks"] = {"required_phrases": ["限定条件", "停止使用"]}
        report = score([row], [{"id": row["id"], "output": "限定条件"}])
        self.assertEqual(report["phrase_proxy"]["per_phrase_hit_rate"], 0.5)
        self.assertFalse(report["phrase_proxy"]["rows"][0]["all_required_present"])

    def test_unmeasured_rates_are_null_not_zero(self):
        row = fixture(task="chat", split="test")
        report = score([row], [{"id": row["id"], "output": "你好"}])
        self.assertIsNone(report["grader"]["false_sufficient_rate"])
        self.assertIsNone(report["phrase_proxy"]["per_phrase_hit_rate"])


if __name__ == "__main__":
    unittest.main()
