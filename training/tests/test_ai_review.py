from copy import deepcopy
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"src"))
from test_pipeline import fixture
from review_fixture import audit
from serviceflow_training.core.contracts import validate


class AiReviewTests(unittest.TestCase):
    def test_authorized_ai_is_explicitly_not_human(self):
        r=audit(fixture("authorized-ai-fixture"),"2026-09-15T00:00:00+00:00")
        self.assertEqual(validate([r])["total"],1)
        self.assertEqual(r["review_status"],"ai_approved")
        for key,value in [("review_authorization",{}),("audit_notes",[]),("heldout_review_kind","human"),("reviewer_kind","human")]:
            bad=deepcopy(r);bad[key]=value
            with self.assertRaises(ValueError): validate([bad])

    def test_draft_does_not_gain_authorization_by_setting_reviewer(self):
        r=fixture("unapproved-ai-fixture")
        r.update(review_status="draft",reviewer="Codex")
        with self.assertRaises(ValueError): validate([r])

if __name__=="__main__": unittest.main()
