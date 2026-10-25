import sys
import tempfile
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from capture_evidence import load_diagnostics
from eval_v2 import write_json


class RagDiagnosticsCaptureTests(unittest.TestCase):
    def test_capture_keeps_rejected_candidates_even_when_final_evidence_is_empty(self):
        trace_id = '12345678-1234-1234-1234-123456789012'
        run = {'results': [{'id': 'screen', 'steps': [{'index': 0, 'events': {'rag_diagnostic': {'traceId': trace_id}, 'rag_evidence': []}}]}]}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            trace = {'traceId': trace_id, 'stages': [
                {'stage': 'initial.retrieval', 'input': [], 'output': [{'chunkId': 'display', 'rank': 1}]},
                {'stage': 'initial.grader', 'input': [{'chunkId': 'display', 'rank': 1}], 'output': []}]}
            write_json(root / (trace_id + '.raw.json'), trace)
            saved, ids = load_diagnostics(run, root)
            self.assertEqual({'display'}, ids)
            self.assertEqual(trace, saved['screen:0'])
            with self.assertRaisesRegex(ValueError, 'other:0'):
                load_diagnostics(run, root, ['screen:0', 'other:0'])
            self.assertEqual({'display'}, load_diagnostics(run, root, ['screen:0'])[1])
            trace['traceId'] = 'mismatch'
            write_json(root / (trace_id + '.raw.json'), trace)
            with self.assertRaises(ValueError): load_diagnostics(run, root)

    def test_missing_or_path_shaped_ids_are_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError): load_diagnostics({'results': []}, Path(temp))
            run = {'results': [{'id': 'screen', 'steps': [{'index': 0, 'events': {'rag_diagnostic': {'traceId': '../secret'}}}]}]}
            with self.assertRaises(ValueError): load_diagnostics(run, Path(temp))

    def test_empty_recall_still_has_a_complete_stage_record(self):
        trace_id = '12345678-1234-1234-1234-123456789012'
        run = {'results': [{'id': 'empty', 'steps': [{'index': 0, 'events': {'rag_diagnostic': {'traceId': trace_id}}}]}]}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_json(root / (trace_id + '.raw.json'), {'traceId': trace_id, 'stages': [{'stage':'initial.retrieval','input':[],'output':[],'reason':'empty_recall'}]})
            saved, ids = load_diagnostics(run, root)
            self.assertEqual(set(), ids)
            self.assertIn('empty:0', saved)


if __name__ == '__main__': unittest.main()
