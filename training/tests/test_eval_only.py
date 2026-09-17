"""Evaluation-only approval cannot authorize training exports."""
import json
from pathlib import Path
from unittest.mock import patch
import tempfile
import unittest
from test_pipeline import fixture
from serviceflow_training.core.contracts import validate, export_dataset, normalized

class EvalOnlyTests(unittest.TestCase):
    def test_split_change_rejected(self):
        for split in ('train','validation'):
            r=fixture(split=split);r['evaluation_only']=True
            with self.assertRaisesRegex(ValueError,'evaluation_only'):validate([r])

    def test_export_rejected_even_with_three_splits(self):
        rows=[fixture('a'),fixture('b',split='validation'),fixture('c',split='test')]
        rows[-1]['evaluation_only']=True
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'data.jsonl';p.write_text('\n'.join(json.dumps(r) for r in rows),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'不得用于训练导出'):export_dataset(p,Path(d)/'export')
            self.assertFalse((Path(d)/'export').exists())

    def test_eval_approval_requires_eval_flag(self):
        r=fixture(split='test')
        r.update(review_status='ai_approved',reviewer='Codex',reviewer_kind='ai',review_authorization={'user_instruction':'审核由你完成  然后进行sft','scope':'evaluation_only'},atomic_review=[{'claim':'CHAT'}],audit_notes=['fixture'],heldout_review_kind='ai')
        with self.assertRaisesRegex(ValueError,'不能授权训练'):validate([r])
        r['evaluation_only']=True
        self.assertEqual(validate([r])['total'],1)

    def test_registered_question_blocked_after_flag_removed(self):
        r=fixture(split='train')
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'registry.json'
            p.write_text(json.dumps({'questions':[normalized(r['context']['question'])],'sources':[]}),encoding='utf-8')
            with patch('serviceflow_training.core.contracts.EVALUATION_ONLY_REGISTRY',p):
                with self.assertRaisesRegex(ValueError,'保留评测问题'):validate([r])

    def test_grader_holdout_source_cannot_be_retagged_as_train(self):
        r=fixture(split='train')
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'registry.json'
            p.write_text(json.dumps({'questions':[],'sources':[r['source_id']]}),encoding='utf-8')
            with patch('serviceflow_training.core.contracts.GRADER_HELDOUT_REGISTRY',p):
                with self.assertRaisesRegex(ValueError,'来源不得进入训练'):validate([r])

if __name__=='__main__':unittest.main()
