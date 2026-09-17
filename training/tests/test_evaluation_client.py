"""Shared evaluator protocol checks; no server or GPU required."""
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from serviceflow_training.evaluation.client import request_case, percentile


class EvaluationClientTests(unittest.TestCase):
    def request(self, lines):
        class Response:
            def __enter__(self): return iter(lines)
            def __exit__(self, *args): pass
        row = {'id': 'fixture', 'task_type': 'grader', 'messages': [{}, {}, {}]}
        with patch('serviceflow_training.evaluation.client.urlopen', return_value=Response()) as call:
            result = request_case('http://localhost', 'fixture', row, 256)
        payload = json.loads(call.call_args.args[0].data)
        self.assertEqual(len(payload['messages']), 2)
        self.assertEqual(call.call_args.kwargs['timeout'], 20)
        self.assertEqual(payload['temperature'], 0.1)
        return result

    def test_complete_stream(self):
        result = self.request([b'data: {"choices":[{"delta":{"content":"{}"},"finish_reason":"stop"}]}\n', b'data: [DONE]\n'])
        self.assertEqual(result['status'], 'ok')
        self.assertEqual(result['output'], '{}')

    def test_missing_done_is_failure(self):
        self.assertEqual(self.request([])['status'], 'error')

    def test_nearest_rank_and_missing_latency(self):
        self.assertIsNone(percentile([], 0.95))
        self.assertEqual(percentile([3, 1, 2], 0.95), 3)
