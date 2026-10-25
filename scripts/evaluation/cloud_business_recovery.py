"""One bounded real-model batch against synthetic orders in the isolated Cloud stack."""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid

from eval_v2 import Client, ROOT, read_json, write_json
from prepare_v2_fixtures import prepare, sql_string

PLAN = ROOT / 'quality/evaluation/datasets/cloud-business-recovery-v1.json'
COMPOSE = ['compose', '--env-file', str(ROOT / '.env'), '-p', 'serviceflow-cloud-eval',
           '-f', str(ROOT / 'deploy/evaluation-cloud-compose.yml'),
           '-f', str(ROOT / 'deploy/evaluation-provider-budget.yml'),
           '-f', str(ROOT / 'deploy/evaluation-cloud-refund.yml')]


def budget_count(lines):
    admitted = [int(value) for line in lines if 'request admitted:' in line
                for value in re.findall(r'used=(\d+)', line)]
    if admitted != list(range(1, len(admitted) + 1)):
        raise RuntimeError('Non-contiguous provider counter or restarted process')
    return len(admitted)


def verify_runtime(container, expected, lines):
    environment = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
    required = {
        'SERVICEFLOW_AI_MODE': 'cloud', 'SERVICEFLOW_RAG_MODE': 'cloud',
        'SERVICEFLOW_EVALUATION_ENABLED': 'true', 'SERVICEFLOW_REFUND_SANDBOX_ENABLED': 'true',
        'SERVICEFLOW_EVALUATION_PROVIDER_BUDGET_ENABLED': 'true',
        'SERVICEFLOW_EVALUATION_PROVIDER_REQUEST_LIMIT': '27',
        'SPRING_AI_RETRY_MAX_ATTEMPTS': '1', 'RESILIENCE4J_RETRY_INSTANCES_CHAT_MAX_ATTEMPTS': '1',
        'RESILIENCE4J_RETRY_INSTANCES_EMBEDDING_MAX_ATTEMPTS': '1',
        'RESILIENCE4J_RETRY_INSTANCES_RERANKER_MAX_ATTEMPTS': '1',
        'SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP': 'false',
        'SPRING_RABBITMQ_LISTENER_DIRECT_AUTO_STARTUP': 'false',
        'JAVA_TOOL_OPTIONS': '-Djdk.httpclient.redirects.retrylimit=1',
    }
    if any(environment.get(k) != v for k, v in required.items()):
        raise RuntimeError('Cloud sandbox/budget/single-attempt configuration mismatch')
    # Compare credentials in memory, never serialize or report their values.
    if any(environment.get(k) != str(v) for k, v in expected.items() if k.startswith('OPENAI_')):
        raise RuntimeError('Running provider configuration differs from project .env Compose configuration')
    if container['RestartCount'] != 0 or container['HostConfig']['RestartPolicy']['Name'] != 'no':
        raise RuntimeError('Evaluation requires one process without restart')
    if sum('budget enabled:' in line for line in lines) != 1 or budget_count(lines) != 0:
        raise RuntimeError('Use a fresh, unused budget process; do not reset or reuse a paid run')


def drop_committed_response(call):
    """Consume one real upstream result and close the downstream socket without a response."""
    upstream = []
    class Drop(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass
        def do_POST(self):
            self.rfile.read(int(self.headers['Content-Length']))
            upstream.append(call())
            self.connection.shutdown(socket.SHUT_RDWR)
            self.connection.close()
    proxy = ThreadingHTTPServer(('127.0.0.1', 0), Drop)
    worker = threading.Thread(target=proxy.serve_forever, daemon=True)
    worker.start()
    lost = False
    started = time.monotonic()
    try:
        request = urllib.request.Request(f'http://127.0.0.1:{proxy.server_port}/confirm', data=b'{}', method='POST')
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=30) as response:
            response.read()
    except (OSError, urllib.error.URLError):
        lost = True
    finally:
        proxy.shutdown(); proxy.server_close(); worker.join(timeout=5)
    return upstream, lost, round((time.monotonic() - started) * 1000)


class SafetyFailure(AssertionError):
    pass


class BusinessRun:
    def __init__(self, docker, output):
        self.docker = docker
        self.output = output
        self.plan = read_json(PLAN)
        self.client = Client('http://127.0.0.1:18080/api')
        self.results = []
        self.records = []
        self.checks = []
        self.case = None

    def command(self, *args, stdin=None):
        result = subprocess.run([self.docker, *args], input=stdin, capture_output=True,
                                text=True, encoding='utf-8', timeout=60, check=True)
        return result.stdout.strip()

    def sql(self, query):
        return self.command('exec', '-i', 'serviceflow-cloud-eval-mysql-1', 'mysql',
                            '--default-character-set=utf8mb4', '-userviceflow', '-peval-only-password',
                            '-N', '-B', 'serviceflow_eval_cloud_v1', stdin=query)

    def budget(self):
        logs = self.command('logs', 'serviceflow-cloud-eval-server-1')
        lines = [line for line in logs.splitlines() if 'Evaluation provider' in line]
        (self.output / 'provider-budget.log').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        used = budget_count(lines)
        if used > 27:
            raise SafetyFailure('Provider cap exceeded')
        return used

    def check(self, name, condition, safety=False):
        self.checks.append({'name': name, 'passed': bool(condition)})
        write_json(self.output / 'checks.raw.json', self.results + [{'id': self.case, 'checks': self.checks}])
        if not condition:
            raise (SafetyFailure if safety else AssertionError)(name)

    def request(self, label, method, path, body=None, role='customer'):
        before = self.budget()
        started = time.monotonic()
        record = {'case': self.case, 'label': label, 'method': method, 'path': path,
                  'body': body, 'role': role, 'providerBefore': before}
        write_json(self.output / 'inflight-request.raw.json', record)
        try:
            result = self.client.request(method, path, body, role)
            record['response'] = result
            return result
        except Exception as failure:
            record['errorType'] = type(failure).__name__
            raise
        finally:
            record['elapsedMs'] = round((time.monotonic() - started) * 1000)
            try:
                record['providerAfter'] = self.budget()
            except Exception as failure:
                record['providerAfter'] = None
                record['budgetReadErrorType'] = type(failure).__name__
            self.records.append(record)
            with (self.output / 'attempts.raw.jsonl').open('a', encoding='utf-8') as file:
                file.write(json.dumps(record, ensure_ascii=False) + '\n')

    def session(self, role='customer'):
        response = self.request('create-session', 'POST', '/chat/sessions', {'title': 'Synthetic business verification'}, role)
        self.check('session created', response['status'] == 200)
        return response['body']['publicId']

    def chat(self, session, number, role='customer', request_id=None):
        message = self.definition['messages'][number].format(**self.variables)
        request_id = request_id or str(uuid.uuid4())
        response = self.request('chat-' + str(number), 'POST', f'/chat/sessions/{session}/messages/stream',
                                {'message': message, 'clientRequestId': request_id}, role)
        return response, request_id

    def state(self, order=None):
        order = order or self.variables['orderNo']
        if not re.fullmatch(r'SF\d+', order):
            raise ValueError('Only synthetic decimal order numbers are allowed')
        row = self.sql("SELECT o.status,o.version,p.refund_status,"
                       "(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),"
                       "(SELECT COUNT(*) FROM refund_request r WHERE r.order_id=o.id),"
                       "COALESCE((SELECT r.amount FROM refund_request r WHERE r.order_id=o.id),0),"
                       "(SELECT COUNT(*) FROM refund_event e JOIN refund_request r ON r.id=e.refund_id WHERE r.order_id=o.id) "
                       "FROM customer_order o JOIN payment p ON p.order_id=o.id WHERE o.order_no=" + sql_string(order) + ';')
        return dict(zip(('order', 'version', 'refund', 'operations', 'refundRequests', 'refundAmount', 'events'), row.split('\t')))

    def untouched(self, order=None):
        return self.state(order) == dict(order='PROCESSING', version='0', refund='NONE', operations='0', refundRequests='0', refundAmount='0.00', events='0')

    def action(self, response, session, order=None):
        action = response['events'].get('action_required', {})
        self.check('model routed to ORDER_QUERY', response['events'].get('meta', {}).get('intent') == 'ORDER_QUERY')
        self.check('pending confirmation received', action.get('actionType') == 'CANCEL_ORDER' and 'done' in response['events'])
        identifier = action['actionId']; uuid.UUID(identifier)
        stored = json.loads(self.command('exec', 'serviceflow-cloud-eval-redis-1', 'redis-cli', 'GET', 'chat:action:' + identifier))
        token_payload = self.client.token('customer').split('.')[1]
        claims = json.loads(base64.urlsafe_b64decode(token_payload + '=' * (-len(token_payload) % 4)))
        expected = {'actionType': 'CANCEL_ORDER', 'subject': claims['sub'], 'customerId': claims['customerId'],
                    'sessionId': session, 'orderNo': order or self.variables['orderNo']}
        self.check('pending tool arguments bound to authenticated owner/session/order', all(stored.get(k) == v for k, v in expected.items()), True)
        self.check('client-visible order matches bound order', action.get('orderNo') == expected['orderNo'], True)
        self.check('pending action has zero business effects', self.untouched(expected['orderNo']), True)
        write_json(self.output / (self.case + '-tool-arguments.raw.json'), {'expected': expected, 'actual': {k: stored.get(k) for k in expected}})
        return identifier

    def confirm(self, action, role='customer', decision='CONFIRM', extra=None):
        return self.request('confirm-' + decision, 'POST', f'/chat/actions/{action}/confirm', {'decision': decision, **(extra or {})}, role)

    def result(self, action, role='customer'):
        return self.request('read-result', 'GET', f'/chat/actions/{action}/result', role=role)

    def committed(self, action, order=None):
        state = self.state(order)
        self.check('exactly one committed cancellation and equal-amount refund', state == dict(order='CANCELLED', version='1', refund='PROCESSING', operations='1', refundRequests='1', refundAmount='99.00', events='0'), True)
        operation = self.sql('SELECT a.request_id FROM order_operation a JOIN customer_order o ON o.id=a.order_id WHERE o.order_no=' + sql_string(order or self.variables['orderNo']) + ';')
        self.check('durable operation requestId equals confirmed action', operation == action, True)

    def evict(self, action):
        uuid.UUID(action)
        self.check('evict only this synthetic action key', self.command('exec', 'serviceflow-cloud-eval-redis-1', 'redis-cli', 'DEL', 'chat:action:' + action) == '1')

    def happy_replay(self):
        session = self.session()
        queried, _ = self.chat(session, 0)
        self.check('first query resolves expected order', queried['events'].get('order', {}).get('orderNo') == self.variables['orderNo'])
        pending, request_id = self.chat(session, 1)
        action = self.action(pending, session)
        before = self.budget()
        replay, _ = self.chat(session, 1, request_id=request_id)
        self.check('duplicate chat is replayed without another provider call', replay['events'].get('done', {}).get('replayed') is True and self.budget() == before)
        with ThreadPoolExecutor(max_workers=2) as pool:
            # Network calls do not mutate shared record lists; collect after joining.
            responses = list(pool.map(lambda _: self.client.request('POST', f'/chat/actions/{action}/confirm', {'decision': 'CONFIRM'}, 'customer'), range(2)))
        write_json(self.output / 'concurrent-confirmations.raw.json', responses)
        write_json(self.output / 'concurrent-confirmation-accounting.json', {
            'httpAttempts': 2, 'latencyMs': [r['latencyMs'] for r in responses],
            'providerBefore': before, 'providerAfter': self.budget()})
        self.check('concurrent confirmations return same action/result', all(r['events'].get('done', {}).get('actionId') == action and r['events']['done'].get('resultCode') == 'CANCELLED' for r in responses))
        self.committed(action)
        progress, _ = self.chat(session, 2)
        self.check('follow-up resolves same order and processing refund', progress['events'].get('order', {}).get('orderNo') == self.variables['orderNo'] and progress['events']['order'].get('refund', {}).get('status') == 'PROCESSING')
        callback = {'eventId': self.variables['eventId'], 'attemptNo': 1, 'outcome': 'SUCCEEDED', 'amount': 99, 'reason': ''}
        path = f'/admin/refunds/{action}/sandbox-events'
        applied = self.request('sandbox-success', 'POST', path, callback, 'admin')
        duplicate = self.request('sandbox-duplicate', 'POST', path, callback, 'admin')
        self.check('sandbox callback applied once and duplicate replayed', applied['body'].get('disposition') == 'APPLIED' and duplicate['body'].get('replayed') is True)
        final, _ = self.chat(session, 3)
        self.check('follow-up sees final refund on correct order', final['events'].get('order', {}).get('orderNo') == self.variables['orderNo'] and final['events']['order'].get('refund', {}).get('status') == 'SUCCEEDED')
        state = self.state()
        self.check('refund callback has one event and no duplicate operation', state['events'] == state['operations'] == state['refundRequests'] == '1' and state['refund'] == 'REFUNDED', True)

    def order_switch(self):
        session = self.session()
        for index, key in enumerate(('orderNo', 'secondOrderNo')):
            response, _ = self.chat(session, index)
            self.check('query selects ' + key, response['events'].get('order', {}).get('orderNo') == self.variables[key])
        pending, _ = self.chat(session, 2)
        action = self.action(pending, session, self.variables['secondOrderNo'])
        response = self.confirm(action, extra={'orderNo': self.variables['orderNo'], 'customerId': -1, 'amount': 0.01})
        self.check('confirmation uses server-bound action', response['events'].get('done', {}).get('resultCode') == 'CANCELLED')
        self.committed(action, self.variables['secondOrderNo'])
        self.check('injected previous order/amount cannot redirect effects', self.untouched(), True)

    def permissions(self):
        session = self.session()
        pending, _ = self.chat(session, 0)
        action = self.action(pending, session)
        before = self.budget()
        foreign_session = self.request('other-owner-session', 'POST', f'/chat/sessions/{session}/messages/stream', {'message': '取消订单 ' + self.variables['orderNo'], 'clientRequestId': str(uuid.uuid4())}, 'other')
        self.check('foreign session denied before model', denied(foreign_session) and self.budget() == before)
        other = self.session('other')
        response, _ = self.chat(other, 1, 'other')
        self.check('other customer cannot select owner order', denied(response) and 'action_required' not in response['events'])
        guest = self.session('guest')
        response, _ = self.chat(guest, 2, 'guest')
        self.check('guest receives login boundary without order/action', 'order' not in response['events'] and 'action_required' not in response['events'] and '登录' in response['answer'])
        before = self.budget()
        for role in ('other', 'guest', 'admin'):
            self.check(role + ' cannot confirm owner action', denied(self.confirm(action, role)))
            self.check(role + ' cannot read owner result', self.result(action, role)['status'] in (403, 404))
        self.check('permission checks do not call model', self.budget() == before)
        self.check('unauthorized attempts have zero side effects', self.untouched(), True)

    def rejected(self):
        session = self.session(); pending, _ = self.chat(session, 0)
        action = self.action(pending, session)
        self.check('explicit rejection completes', 'done' in self.confirm(action, decision='REJECT')['events'])
        self.check('rejected action cannot execute or recover a result', denied(self.confirm(action)) and self.result(action)['status'] == 404)
        self.check('rejection has zero side effects', self.untouched(), True)

    def expired(self):
        session = self.session(); pending, _ = self.chat(session, 0)
        action = self.action(pending, session); self.evict(action)
        self.check('missing action remains unknown and non-executable', self.result(action)['status'] == 404 and denied(self.confirm(action)))
        self.check('expired action has zero side effects', self.untouched(), True)

    def lost_response(self):
        session = self.session(); query, _ = self.chat(session, 0)
        self.check('initial order resolved', query['events'].get('order', {}).get('orderNo') == self.variables['orderNo'])
        pending, _ = self.chat(session, 1); action = self.action(pending, session)
        upstream, lost, elapsed = drop_committed_response(lambda: self.confirm(action))
        write_json(self.output / 'response-loss.raw.json', {'clientReceivedNoResponse': lost, 'elapsedMs': elapsed, 'upstream': upstream})
        self.check('real upstream committed but downstream received no response', lost and len(upstream) == 1 and upstream[0]['events'].get('done', {}).get('resultCode') == 'CANCELLED')
        self.committed(action); self.evict(action)
        state = self.state(); before = self.budget()
        for _ in range(2):
            response = self.result(action)
            self.check('read-only recovery returns committed owner order', response['status'] == 200 and response['body']['operation']['requestId'] == action and response['body']['order']['orderNo'] == self.variables['orderNo'])
        self.check('missing action cannot be reconfirmed', denied(self.confirm(action)))
        self.check('recovery leaves model budget and business effects unchanged', self.budget() == before and self.state() == state, True)

    def run(self):
        container = json.loads(self.command('inspect', 'serviceflow-cloud-eval-server-1'))[0]
        clean = {k: v for k, v in os.environ.items() if not k.startswith('OPENAI_')}
        expanded = json.loads(subprocess.check_output([self.docker, *COMPOSE, 'config', '--format', 'json'], env=clean, text=True, stderr=subprocess.PIPE))
        logs = self.command('logs', 'serviceflow-cloud-eval-server-1')
        lines = [line for line in logs.splitlines() if 'Evaluation provider' in line]
        verify_runtime(container, expanded['services']['server']['environment'], lines)
        for service in ('server', 'mysql', 'redis'):
            self.checks = []; self.case = 'preflight'
            self.check('isolated project ' + service, self.command('inspect', 'serviceflow-cloud-eval-' + service + '-1', '--format', '{{index .Config.Labels "com.docker.compose.project"}}') == 'serviceflow-cloud-eval', True)
        if self.sql('SELECT DATABASE();') != 'serviceflow_eval_cloud_v1':
            raise RuntimeError('Wrong database')
        info = self.client.request('GET', '/actuator/info')['body']['serviceflow']
        if info.get('aiMode') != 'cloud' or str(info.get('refundSandboxEnabled')).lower() != 'true':
            raise RuntimeError('Cloud sandbox API preflight failed')
        before = {'containerId': container['Id'], 'imageId': container['Image'], 'startedAt': container['State']['StartedAt'],
                  'restartCount': container['RestartCount'], 'codeCommit': self.command_git('rev-parse', 'HEAD'),
                  'planSha256': hashlib.sha256(PLAN.read_bytes()).hexdigest(),
                  'models': {k: v for k, v in expanded['services']['server']['environment'].items() if k in ('OPENAI_MODEL', 'OPENAI_EMBEDDING_MODEL', 'OPENAI_RERANKER_MODEL')}, 'providerBefore': 0}
        write_json(self.output / 'runtime-before.json', before)
        run_id = datetime.now().strftime('%Y%m%d%H%M%S') + f'{secrets.randbelow(10000):04}'
        suite = {'cases': [dict(case, fixture={'owner': 'customer', 'orderStatus': 'PROCESSING', 'paymentStatus': 'PAID', 'secondOrder': case.get('secondOrder', False)}) for case in self.plan['cases']]}
        sql, fixtures = prepare(suite, run_id)
        (self.output / 'fixtures.sql').write_text(sql, encoding='utf-8')
        write_json(self.output / 'fixtures.json', fixtures)
        self.sql(sql)
        self.client.usernames = fixtures['principals']
        for role in ('customer', 'other', 'guest', 'admin'):
            self.client.token(role)
        stopped = False
        for definition in self.plan['cases']:
            self.case = definition['id']; self.checks = []; self.definition = definition
            self.variables = fixtures['cases'][self.case]
            started = time.monotonic(); provider_before = self.budget()
            result = {'id': self.case, 'status': 'not_attempted' if stopped else 'running'}
            if not stopped:
                try:
                    getattr(self, self.case.replace('-', '_'))()
                    result['status'] = 'passed'
                except Exception as failure:
                    result.update(status='failed', errorType=type(failure).__name__)
                    if isinstance(failure, SafetyFailure) or not isinstance(failure, AssertionError):
                        stopped = True
                result.update(checks=list(self.checks), elapsedMs=round((time.monotonic()-started)*1000), providerBefore=provider_before)
                try:
                    result.update(providerAfter=self.budget(), finalOrderState=self.state())
                    if definition.get('secondOrder'):
                        result['secondOrderState'] = self.state(self.variables['secondOrderNo'])
                except Exception as failure:
                    result.update(status='failed', evidenceReadErrorType=type(failure).__name__)
                    stopped = True
            self.results.append(result)
            write_json(self.output / 'results.raw.json', self.results)
            print(self.case, result['status'], flush=True)
        current = json.loads(self.command('inspect', before['containerId']))[0]
        same = current['State']['StartedAt'] == before['startedAt'] and current['RestartCount'] == 0
        summary = {**before, 'caseResults': self.results, 'providerAttempts': self.budget(), 'providerLimit': 27,
                   'sameProcess': same, 'tokenUsage': None, 'currencyCost': None,
                   'usageNote': 'Provider usage not retained by these adapters; no billing query performed.',
                   'plannedCases': 6, 'passed': sum(r['status'] == 'passed' for r in self.results),
                   'failed': sum(r['status'] == 'failed' for r in self.results),
                   'notAttempted': sum(r['status'] == 'not_attempted' for r in self.results),
                   'newChatAttempts': sum(r['label'].startswith('chat-') and not r.get('response', {}).get('events', {}).get('done', {}).get('replayed') for r in self.records),
                   'scope': 'real cloud classification, deterministic server-bound tools, synthetic orders and refund sandbox; development verification',
                   'serverRestarted': False, 'rawResponsesPublished': False}
        write_json(self.output / 'summary.json', summary)
        if not same:
            raise SafetyFailure('Evaluation process changed')
        return summary

    @staticmethod
    def command_git(*args):
        return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def denied(response):
    error = response['events'].get('error', {})
    return response['status'] in (403, 404) or (
        error.get('code') in ('FORBIDDEN', 'NOT_FOUND') and error.get('retryable') is False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--docker', default='docker')
    parser.add_argument('--allow-cloud', action='store_true')
    parser.add_argument('--allow-isolated-faults', action='store_true')
    args = parser.parse_args()
    if not args.allow_cloud or not args.allow_isolated_faults:
        parser.error('Explicit cloud and synthetic-fault opt-ins required; fixed scope: 6 cases, 14 new chat turns, 27 provider attempts')
    output = ROOT / 'quality/evaluation/reports' / ('cloud-business-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
    output.mkdir(parents=True, exist_ok=False)
    print('RUN', output, flush=True)
    summary = BusinessRun(args.docker, output).run()
    print('RESULT', json.dumps({k: summary[k] for k in ('plannedCases', 'passed', 'failed', 'notAttempted', 'providerAttempts', 'sameProcess')}), flush=True)
    raise SystemExit(0 if summary['passed'] == 6 else 1)


if __name__ == '__main__':
    main()
