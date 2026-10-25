import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from eval_v2 import ROOT, read_json, validate_package, validate_suite
from semantic_review import redact, review_input, validate_review


class SemanticReviewTests(unittest.TestCase):
    def test_new_sources_are_disjoint_and_all_held_out(self):
        package = ROOT / 'data/retail-holdout-v2'
        old = read_json(ROOT / 'data/retail-v1/manifest.json')
        new = read_json(package / 'manifest.json')
        self.assertFalse({s['sourceGroup'] for s in old['sources']} & {s['sourceGroup'] for s in new['sources']})
        self.assertEqual(2, validate_package(package)['products'])
        counts = validate_suite(read_json(ROOT / 'quality/evaluation/datasets/semantic-holdout-v2.json'), package)
        self.assertEqual({'development': 0, 'holdout': 14}, counts['splits'])

    def test_redaction_preserves_distinct_orders(self):
        self.assertEqual('[ORDER_1] [ORDER_2] [ORDER_1]', redact('SF202610250001 SF202610250002 SF202610250001'))
        self.assertNotIn('sk-abcdef', redact('Bearer sk-abcdef'))

    def test_reviewer_cannot_invent_evidence_or_turn(self):
        payload = {'references': [{'id': 'source#one'}], 'catalog': [], 'businessEvidence': [], 'turns': [{'step': 0}]}
        review = {'factual': 'unsupported', 'citation': 'unsupported', 'multiTurn': 'not_applicable',
                  'completeness': 'complete', 'findings': [{'turn': 0, 'severity': 'major', 'evidenceIds': ['source#one']}]}
        validate_review(review, payload)
        invalid = copy.deepcopy(review)
        invalid['findings'][0]['evidenceIds'] = ['invented']
        with self.assertRaises(ValueError):
            validate_review(invalid, payload)
        invalid = copy.deepcopy(review)
        invalid['findings'][0]['turn'] = 1
        with self.assertRaises(ValueError):
            validate_review(invalid, payload)

    def test_actual_retrieval_replaces_expected_full_file(self):
        case = read_json(ROOT / 'quality/evaluation/datasets/semantic-holdout-v2.json')['cases'][0]
        result = {'steps': [{'index': 0, 'answer': 'a', 'checks': {}, 'events': {
            'meta': {'citations': ['iphone16-us.md']}, 'rag_evidence': [{'chunkId': 'one', 'source': 'iphone16-us.md'}]}}]}
        payload = review_input(case, result, ROOT / 'data/retail-holdout-v2',
            {'one': {'source': 'iphone16-us.md', 'content': 'only retrieved passage'}})
        self.assertEqual(['only retrieved passage'], [r['text'] for r in payload['references']])
        self.assertEqual([payload['references'][0]['id']], payload['turns'][0]['retrievedEvidenceIds'])
        with self.assertRaises(KeyError):
            review_input(case, result, ROOT / 'data/retail-holdout-v2', {})


if __name__ == '__main__':
    unittest.main()
