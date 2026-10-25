import copy
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import threading

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import eval_v2
from eval_v2 import Client, PACKAGE, SUITE, check, expand, parse_sse, read_json, run_case, summarize, validate_package, validate_suite
from prepare_v2_fixtures import prepare
from import_retail import import_package


def response(events=(), body=None, status=200):
    return {'status': status, 'body': body, 'events': dict(events), 'eventNames': [x[0] for x in events],
            'answer': ''.join(x[1] for x in events if x[0] == 'token'), 'latencyMs': 1}


class ScenarioClient:
    def __init__(self):
        self.calls = []

    def request(self, method, path, body=None, principal=None):
        self.calls.append((method, path, body, principal))
        if path == '/chat/sessions':
            return response(body={'publicId': 'session-one'})
        if path.endswith('/confirm'):
            return response([('token', '已取消'), ('done', {'resultCode': 'CANCELLED'})])
        if method == 'GET':
            return response(body={'status': 'CANCELLED', 'payment': {'refundStatus': 'PROCESSING'}})
        return response([('meta', {'intent': 'ORDER_QUERY'}), ('token', '请确认'),
                         ('action_required', {'actionId': 'action-one'}), ('done', {})])


class EvaluationTests(unittest.TestCase):
    def test_parallel_cli_preserves_suite_order_and_checkpoints_each_completed_case(self):
        class LocalClient:
            def __init__(self, base_url):
                self.usernames = {}
            def token(self, role):
                return 'test-token'
            def request(self, method, path, **kwargs):
                if path == '/actuator/info':
                    return {'body': {'serviceflow': {'evaluationEnabled': True, 'aiMode': 'demo', 'ragMode': 'demo'}}}
                return {'status': 200, 'body': {'items': [], 'total': 0}}

        barrier = threading.Barrier(2)
        def scenario(client, case, variables):
            barrier.wait(timeout=5)
            return {'id': case['id'], 'split': 'development', 'passed': True,
                    'steps': [{'checks': {'httpStatus': True}}]}

        suite = {'version': 'serviceflow-eval-v2', 'origin': 'project-authored', 'cases': [
            {'id': name, 'principal': 'customer', 'split': 'development', 'sourceGroups': [],
             'steps': [{'type': 'chat', 'message': name, 'expect': {'events': ['done']}}]}
            for name in ['first', 'second']]}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            suite_path = root / 'suite.json'
            suite_path.write_text(json.dumps(suite), encoding='utf-8')
            output = root / 'quality/evaluation/reports'
            args = ['eval_v2', 'run', '--suite', str(suite_path), '--output', str(output), '--workers', '2']
            with patch.object(eval_v2, 'ROOT', root), patch.object(eval_v2, 'Client', LocalClient), \
                    patch.object(eval_v2, 'run_case', side_effect=scenario), patch.object(sys, 'argv', args), \
                    contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit) as status:
                eval_v2.main()
            self.assertEqual(status.exception.code, 0)
            run = next(output.iterdir())
            saved = read_json(run / 'responses.raw.json')
            self.assertEqual([r['id'] for r in saved['results']], ['first', 'second'])
            self.assertEqual(saved['workers'], 2)
            self.assertEqual(len((run / 'completed.raw.jsonl').read_text(encoding='utf-8').splitlines()), 2)

    def test_source_package_has_verified_records_and_no_fabricated_price(self):
        report = validate_package()
        self.assertEqual(report['products'], 23)
        self.assertEqual(report['sections'], 88)

    def test_new_suite_covers_real_multi_turn_execution(self):
        report = validate_suite(read_json(SUITE))
        self.assertEqual(report['cases'], 200)
        self.assertGreaterEqual(report['multiTurnCases'], 30)

    def test_duplicate_scenarios_are_rejected(self):
        suite = read_json(SUITE)
        duplicate = copy.deepcopy(suite['cases'][0])
        duplicate['id'] += '-duplicate'
        suite['cases'].append(duplicate)
        with self.assertRaisesRegex(AssertionError, 'duplicate scenario'):
            validate_suite(suite)

    def test_source_family_cannot_cross_splits(self):
        suite = read_json(SUITE)
        suite['cases'][0]['split'] = 'holdout'
        with self.assertRaisesRegex(AssertionError, 'crosses splits'):
            validate_suite(suite)

    def test_expected_evidence_must_resolve_to_a_source_section(self):
        suite = read_json(SUITE)
        suite['cases'][0]['expectedEvidence']['sectionId'] = 'invented-section'
        with self.assertRaisesRegex(AssertionError, 'unknown evidence section'):
            validate_suite(suite)

    def test_multiline_utf8_sse_preserves_tokens(self):
        events = parse_sse(': heartbeat\r\nevent: token\r\ndata: 第一行\r\ndata: 第二行\r\n\r\nevent: token\ndata: true\n\nevent: done\ndata: {"ok":true}\n\n')
        self.assertEqual(events, [('token', '第一行\n第二行'), ('token', 'true'), ('done', {'ok': True})])

    def test_missing_done_is_a_failure_even_if_text_matches(self):
        result = response([('token', '订单已取消')])
        self.assertFalse(all(check(result, {'events': ['token', 'done'], 'textAll': [['已取消']]}).values()))

    def test_partial_fact_coverage_no_longer_passes(self):
        result = response([('token', '停止充电')])
        self.assertFalse(check(result, {'textAll': [['停止充电'], ['授权服务中心']]} )['phraseCoverage'])

    def test_explicit_contradiction_fails_even_when_keyword_is_present(self):
        result = response([('token', '不需要停止充电，也不用送授权服务中心')])
        checks = check(result, {'textAll': [['停止充电'], ['授权服务中心']], 'textNone': ['不需要停止充电', '不用送']})
        self.assertFalse(all(checks.values()))

    def test_extra_wrong_product_is_not_counted_as_correct(self):
        result = response([('meta', {'productIds': [1, 2]})])
        self.assertFalse(check(result, {'products': [1]})['products'])

    def test_unexpected_error_after_done_fails(self):
        result = response([('done', {}), ('error', {'message': 'failed'})])
        self.assertFalse(check(result, {'events': ['done']})['noUnexpectedError'])

    def test_business_state_assertion_requires_the_actual_value(self):
        result = response(body={'status': 'PROCESSING'})
        self.assertFalse(check(result, {'json': {'body.status': 'CANCELLED'}})['json:body.status'])

    def test_metrics_use_only_applicable_denominators(self):
        report = summarize([{'passed': True, 'steps': [{'checks': {'intent': True}}]},
                            {'passed': False, 'steps': [{'checks': {'products': False}}]}])
        self.assertEqual(report['checks']['products'], {'passed': 0, 'total': 1})
        self.assertIsNone(report['semanticAccuracy'])

    def test_same_session_confirm_capture_and_final_query(self):
        client = ScenarioClient()
        case = next(c for c in read_json(SUITE)['cases'] if c['id'] == 'order-reference')
        # First turn's order event is normally returned by the server; use a focused scenario here.
        case = copy.deepcopy(case)
        case['steps'][0]['expect']['events'] = ['token', 'done']
        result = run_case(client, case, {'orderNo': 'SF123456789012'})
        self.assertTrue(result['passed'])
        self.assertEqual(sum(path == '/chat/sessions' for _, path, _, _ in client.calls), 1)
        chat_calls = [path for _, path, _, _ in client.calls if path.endswith('/stream')]
        self.assertEqual(chat_calls, ['/chat/sessions/session-one/messages/stream'] * 2)
        self.assertIn(('POST', '/chat/actions/action-one/confirm', {'decision': 'CONFIRM'}, 'customer'), client.calls)
        self.assertEqual(client.calls[-1][1], '/orders/SF123456789012')

    def test_retry_reuses_client_request_id(self):
        case = next(c for c in read_json(SUITE)['cases'] if c['id'] == 'complaint-retry')
        client = ScenarioClient()
        run_case(client, case, {})
        ids = [body['clientRequestId'] for _, path, body, _ in client.calls if path.endswith('/stream')]
        self.assertEqual(len(ids), 2)
        self.assertEqual(ids[0], ids[1])

    def test_missing_action_fails_without_confirming_anything(self):
        client = ScenarioClient()
        result = run_case(client, {'id': 'test', 'split': 'development', 'principal': 'customer',
                                   'steps': [{'type': 'confirm', 'decision': 'CONFIRM', 'expect': {'events': ['done']}}]}, {})
        self.assertFalse(result['passed'])
        self.assertEqual(client.calls, [])

    def test_http_status_errors_are_not_swallowed(self):
        self.assertFalse(check(response(status=503), {'status': 200})['httpStatus'])

    def test_variable_substitution_preserves_numeric_json_type(self):
        self.assertEqual(expand({'id': '{{products.A}}', 'message': '查询 {{orderNo}}'}, {'products': {'A': 7}, 'orderNo': 'SF1'}),
                         {'id': 7, 'message': '查询 SF1'})

    def test_external_or_credential_bearing_base_urls_are_rejected(self):
        for url in ['https://production.example/api', 'http://user:password@localhost/api', 'http://localhost/api?token=secret']:
            with self.assertRaises(ValueError):
                Client(url)

    def test_fixture_generator_is_additive_and_database_guarded(self):
        sql, receipt = prepare(read_json(SUITE), '202610230000001234')
        self.assertIn("LEFT(DATABASE(), CHAR_LENGTH('serviceflow_eval_')) = 'serviceflow_eval_'", sql)
        self.assertNotIn('DELETE ', sql)
        self.assertNotIn('UPDATE ', sql)
        self.assertIn('START TRANSACTION', sql)
        self.assertNotEqual(receipt['principals']['customer'], receipt['principals']['other'])
        orders = [v for values in receipt['cases'].values() for key, v in values.items() if key.endswith('OrderNo') or key == 'orderNo']
        self.assertEqual(len(set(orders)), len(orders))

    def test_fixture_run_id_cannot_inject_sql(self):
        with self.assertRaises(ValueError):
            prepare(read_json(SUITE), "'; DROP TABLE product;")

    def test_import_refuses_paid_ingestion_before_any_mutation(self):
        class CloudClient:
            def request(self, method, path, *args):
                self.called = (method, path)
                return {'body': {'serviceflow': {'ragMode': 'cloud'}}}
        client = CloudClient()
        from eval_v2 import PACKAGE
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, 'Cloud embedding'):
                import_package(client, PACKAGE, Path(temp) / 'receipt.json')
        self.assertEqual(client.called, ('GET', '/actuator/info'))

    def test_refund_suite_covers_ten_live_scenarios_and_stable_event_keys(self):
        suite = read_json(SUITE.parent / 'refund-sandbox-v1.json')
        self.assertEqual(validate_suite(suite)['cases'], 10)
        sql, receipt = prepare(suite, '202610240000001234')
        self.assertEqual(len(receipt['cases']), 10)
        self.assertIn('secondOrderNo', receipt['cases']['refund-event-bound-to-request'])
        self.assertEqual(prepare(suite, '202610240000001234'), (sql, receipt))

    def test_import_receipt_for_another_instance_fails_before_any_write(self):
        class DemoClient:
            base_url = 'http://127.0.0.1:18080/api'

            def request(self, method, path, *args):
                self.called = (method, path)
                return {'body': {'serviceflow': {'ragMode': 'demo'}}}
        client = DemoClient()
        with tempfile.TemporaryDirectory() as temp:
            receipt = Path(temp) / 'receipt.json'
            receipt.write_text(json.dumps({'baseUrl': 'http://localhost:8080/api'}), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'different instance'):
                import_package(client, PACKAGE, receipt)
        self.assertEqual(client.called, ('GET', '/actuator/info'))


if __name__ == '__main__':
    unittest.main()
