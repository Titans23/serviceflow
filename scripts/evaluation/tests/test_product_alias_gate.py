from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from product_alias_gate import assess, load_fixture, summarize


class ProductAliasGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases, catalog = load_fixture()
        cls.ids = {row['sku']: index for index, row in enumerate(catalog, 1)}

    def product(self, sku):
        return {'id': self.ids[sku], 'sku': sku}

    def response(self, case):
        rows = []
        for target in case['targets']:
            confirmed = self.product(target['confirmedSku']) if 'confirmedSku' in target else None
            candidates = ([target['semanticSku']] if 'semanticSku' in target else target.get('requiredCandidates', []))
            rows.append({'label': target['mention'], 'confirmed': confirmed,
                         'candidates': [self.product(sku) for sku in candidates]})
        return {'status': 200, 'latencyMs': 5, 'events': {'done': {}, 'comparison_selection_required': {
            'comparisonId': 'comparison', 'targets': rows}}}, rows

    def results_with_misses(self, missed):
        turns = []
        for case in self.cases:
            response, rows = self.response(case)
            for row in rows:
                if row['label'] in missed:
                    row['candidates'] = []
            calls = 1 + sum('semanticSku' in target for target in case['targets'])
            turns.append(assess(case, response, self.ids, calls))
        return turns

    def test_threshold_and_required_storage_variant(self):
        optional = [target['mention'] for target in self.cases[0]['targets'][1:]]
        summary = summarize(self.results_with_misses(optional[:2]), False)
        self.assertEqual(summary['top3Hits'], 18)
        self.assertTrue(summary['passed'])
        self.assertFalse(summarize(self.results_with_misses(optional[:3]), False)['passed'])
        self.assertFalse(summarize(self.results_with_misses(['苹果16 256GB']), False)['passed'])

    def test_rejects_wrong_specs_unknown_identity_and_auto_confirmation(self):
        case = self.cases[0]
        response, rows = self.response(case)
        rows[0]['candidates'].append(self.product('APPLE-IP16-US-128'))
        rows[1]['candidates'][0]['id'] = -1
        rows[2]['confirmed'] = rows[2]['candidates'].pop()
        result = assess(case, response, self.ids, 6)
        for slot in result['slots'][:3]:
            self.assertFalse(slot['top3Hit'])
            self.assertEqual(len(slot['violations']), 1)

    def test_requires_real_reranker_activity_when_classification_fails(self):
        case = next(case for case in self.cases if case.get('invalidClassification'))
        response, _ = self.response(case)
        result = assess(case, response, self.ids, 1)
        self.assertTrue(result['classifierFailureInjected'])
        self.assertEqual(result['violations'], ['unexpected classifier or reranker call count'])
