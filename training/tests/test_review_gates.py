from copy import deepcopy
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"src"))
from serviceflow_training.core.contracts import validate, read_json, HELDOUT_REGISTRY
from serviceflow_training.data.reviews import apply
from serviceflow_training.evaluation.client import request_case, percentile
from test_pipeline import fixture


def draft(split="train"):
    r=fixture("pilot-unit-only",split=split)
    r.update(review_status="draft",reviewer="",reviewed_at="",heldout_derivative_reviewed=False)
    return r


class PilotTests(unittest.TestCase):
    def test_draft_inspection_does_not_make_it_trainable(self):
        r=draft()
        validate([r],require_review=False)
        self.assertEqual(r["review_status"],"draft")
        with self.assertRaisesRegex(ValueError,"人工审核"): validate([r])

    def test_draft_cannot_claim_human_reviewer(self):
        r=draft(); r["reviewer"]="fictional"
        with self.assertRaisesRegex(ValueError,"虚构"): validate([r],require_review=False)

    def test_new_baseline_question_and_family_are_excluded(self):
        held=read_json(HELDOUT_REGISTRY)["cases"][0]
        r=draft(); r["context"]["question"]=held["normalized_question"]; r["messages"][1]["content"]=held["normalized_question"]
        with self.assertRaisesRegex(ValueError,"保留评测"): validate([r],require_review=False)
        r=draft(); r["group_id"]=held["group_id"]
        with self.assertRaisesRegex(ValueError,"独立基线"): validate([r],require_review=False)

    def test_pending_review_does_not_auto_approve(self):
        r=draft(); out,excluded=apply([r],[{"id":r["id"],"decision":"pending"}])
        self.assertEqual(out,[]); self.assertEqual(len(excluded),1)

    def test_ai_reviewer_and_subset_test_deletion_rejected(self):
        r=draft()
        d={"id":r["id"],"decision":"approve","reviewer":"Codex","reviewed_at":"2026-09-15T18:00:00+08:00","heldout_derivative_reviewed":True,"corrected_output":None}
        with self.assertRaisesRegex(ValueError,"真实人工"): apply([r],[d])
        r=draft("test"); d.update(id=r["id"],decision="reject",reviewer="UNIT-TEST-NOT-REAL-REVIEW",notes="fixture")
        with self.assertRaisesRegex(ValueError,"不能从固定基线"): apply([r],[d])

    def test_partial_test_approval_is_rejected(self):
        a=draft("test"); b=deepcopy(a)
        for k in ("id","source_id","group_id","template_family"): b[k]+="-b"
        b["context"]["question"]+="乙"; b["messages"][1]["content"]+="乙"
        decisions=[{"id":a["id"],"decision":"approve","reviewer":"UNIT-TEST-NOT-REAL-REVIEW","reviewed_at":"2026-09-15T18:00:00+08:00","heldout_derivative_reviewed":True,"corrected_output":None},{"id":b["id"],"decision":"pending"}]
        with self.assertRaisesRegex(ValueError,"完整审核"): apply([a,b],decisions)

    def test_revised_illegal_grader_target_rejected(self):
        r=fixture("review-grader-fixture",task="grader")
        r.update(review_status="draft",reviewer="",reviewed_at="",heldout_derivative_reviewed=False)
        d={"id":r["id"],"decision":"revise","reviewer":"UNIT-TEST-NOT-REAL-REVIEW","reviewed_at":"2026-09-15T18:00:00+08:00","heldout_derivative_reviewed":True,"corrected_output":'{"sufficient":true,"rankedChunkIds":["invented"]}'}
        with self.assertRaisesRegex(ValueError,"候选外"): apply([r],[d])

    def test_stream_missing_done_is_retained_as_failure(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self,*args): return None
            def __iter__(self): return iter([b'data: {"choices":[{"delta":{"content":"CHAT"},"finish_reason":"stop"}]}\n'])
        r=draft()
        with patch("serviceflow_training.evaluation.client.urlopen",return_value=Response()): result=request_case("http://localhost","test-key",r,64)
        self.assertEqual(result["status"],"error")
        self.assertEqual(result["output"],"CHAT")
        self.assertFalse(result["sse_done"])

    def test_percentile_uses_nearest_rank(self):
        self.assertEqual(percentile(list(range(1,101)),.95),95)
        self.assertIsNone(percentile([],.95))


if __name__=="__main__": unittest.main()
