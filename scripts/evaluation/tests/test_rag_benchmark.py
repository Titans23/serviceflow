import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_rag_benchmark import build, validate, DESTINATION
from eval_v2 import read_json
from rag_metrics import evidence_key, percentile, retrieval_metrics, score, summarize


class RagBenchmarkTest(unittest.TestCase):
    def test_labels_exist_in_frozen_source_and_are_not_all_business_cases(self):
        suite = build()
        self.assertEqual(suite, read_json(DESTINATION))
        result = validate(suite)
        self.assertEqual(result['categories'], {'single_evidence': 80, 'multi_evidence': 16, 'unanswerable': 8})
        suite['cases'][0]['goldEvidence'] = ['made-up.md#section']
        with self.assertRaises(AssertionError):
            validate(suite)

    def test_recall_and_hit_differ_and_duplicates_do_not_inflate_recall(self):
        result = retrieval_metrics(['a', 'b'], ['x', 'a', 'a', 'y', 'z', 'b'])
        self.assertEqual(result, {'recallAt5': .5, 'hitAt5': 1, 'mrrAt5': .5})
        self.assertIsNone(retrieval_metrics([], ['x']))
        self.assertEqual(retrieval_metrics(['a'], [])['recallAt5'], 0)

    def test_missing_evidence_identity_is_not_a_false_hit(self):
        with self.assertRaises(ValueError):
            evidence_key({'source': 'x.md', 'content': 'no heading'})
        self.assertEqual(evidence_key({'source': 'x.md', 'content': '# source\n## usb：接口\nfact'}), 'x.md#usb')

    def test_answer_quality_checks_missing_irrelevant_facts_and_abstention(self):
        case = {'answerable': True, 'goldEvidence': ['x.md#a'], 'requiredFacts': ['correct fact']}
        response = {'result': {'degraded': False}, 'selection': {'factIds': [0], 'insufficient': False},
                    'factContext': {'facts': [{'source': 'x.md', 'text': 'correct fact'},
                                             {'source': 'x.md', 'text': 'unrelated fact'}]}}
        trace = {'stages': [{'stage': 'initial.rerank', 'input': [{'chunkId': 'x'}], 'output': [{'chunkId': 'x'}], 'elapsedMs': 1}]}
        chunks = {'x': {'source': 'x.md', 'content': '## a：title\ncorrect fact'}}
        self.assertTrue(score(case, response, trace, chunks)['answerPass'])
        response['selection']['factIds'] = [0, 1]
        self.assertFalse(score(case, response, trace, chunks)['answerPass'])
        response['selection'] = {'factIds': [], 'insufficient': True}
        self.assertFalse(score(case, response, trace, chunks)['answerPass'])
        unknown = {'answerable': False, 'goldEvidence': [], 'requiredFacts': []}
        self.assertTrue(score(unknown, response, trace, chunks)['answerPass'])

    def test_failed_and_unattempted_cases_stay_in_denominator(self):
        cases = [{'id': 'a', 'answerable': True}, {'id': 'b', 'answerable': True}, {'id': 'c', 'answerable': False}]
        scored = {'retrieval': {'recallAt5': 1, 'hitAt5': 1, 'mrrAt5': .5}, 'factCoverage': 1,
                  'answerPass': True, 'insufficient': False, 'rewriteTriggered': False, 'returnedCandidateCount': 8}
        rows = [{'caseId': 'a', 'mode': 'DENSE', 'clientMs': 10, 'score': scored},
                {'caseId': 'b', 'mode': 'DENSE', 'clientMs': 100, 'errorType': 'TimeoutError'}]
        result = summarize(rows, cases, ['DENSE'])['DENSE']
        self.assertEqual(result['recallAt5'], .5)
        self.assertEqual(result['answerPassRate'], 1 / 3)
        self.assertEqual((result['failed'], result['notAttempted']), (1, 1))
        self.assertEqual(result['p95Ms'], 10)
        self.assertEqual(result['allAttemptsP95Ms'], 100)
        with self.assertRaises(ValueError):
            summarize(rows + [copy.deepcopy(rows[0])], cases, ['DENSE'])

    def test_percentile_uses_nearest_rank(self):
        self.assertEqual(percentile(list(range(1, 101)), .95), 95)


if __name__ == '__main__':
    unittest.main()
