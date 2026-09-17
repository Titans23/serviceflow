import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from serviceflow_training.evaluation.diagnostics import diagnose
from test_pipeline import fixture


class DiagnosticsTests(unittest.TestCase):
    def test_partial_support_is_not_an_empty_refusal(self):
        r=fixture('partial','grader'); r['messages'][2]['content']='{"sufficient":false,"rankedChunkIds":["fixture-chunk"]}'
        d=diagnose([r],[{'id':r['id'],'output':'{"sufficient":false,"rankedChunkIds":[]}'}])
        self.assertEqual(d['correct_sufficiency'],1)
        self.assertEqual(d['partial_exact'],0)
        self.assertEqual(d['missed_support_ids'],1)
        self.assertEqual(d['reference_id_recall'],0)

    def test_invalid_invented_id_is_not_hidden(self):
        r=fixture('invented','grader')
        d=diagnose([r],[{'id':r['id'],'output':'{"sufficient":false,"rankedChunkIds":["outside"]}'}])
        self.assertEqual(d['invalid'],1)
        self.assertEqual(d['extraneous_ids'],1)
        self.assertEqual(d['empty_reference_with_selected_ids'],1)
        self.assertEqual(d['reference_id_precision'],0)

    def test_malformed_json_counts_as_invalid(self):
        r=fixture('malformed','grader')
        d=diagnose([r],[{'id':r['id'],'output':'not JSON'}])
        self.assertEqual(d['invalid'],1)
        self.assertEqual(d['correct_sufficiency'],0)
        self.assertEqual(d['correct_support_set'],0)


if __name__=='__main__':unittest.main()
