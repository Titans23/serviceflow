"""Real HTTP response-loss, Redis action eviction and server restart against isolated Demo only."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import secrets
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid

from eval_v2 import Client, ROOT, write_json
from prepare_v2_fixtures import prepare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--docker', default='docker')
    parser.add_argument('--baseline', action='store_true', help='Record the pre-fix missing-result behavior only')
    parser.add_argument('--allow-isolated-faults', action='store_true')
    args = parser.parse_args()
    if not args.allow_isolated_faults:
        parser.error('Explicit opt-in required: adds synthetic fixtures, evicts only their action keys and restarts eval server')
    client = Client('http://127.0.0.1:18080/api')
    info = client.request('GET', '/actuator/info')['body']['serviceflow']
    assert info['aiMode'] == info['ragMode'] == 'demo'
    assert str(info['evaluationEnabled']).lower() == str(info['refundSandboxEnabled']).lower() == 'true'
    def docker(*command, stdin=None):
        result = subprocess.run([args.docker, *command], input=stdin, text=True, encoding='utf-8',
                                capture_output=True, timeout=150, check=True)
        return result.stdout.strip()
    for service in ('server', 'mysql', 'redis'):
        label = docker('inspect', f'serviceflow-eval-{service}-1', '--format', '{{index .Config.Labels "com.docker.compose.project"}}')
        assert label == 'serviceflow-eval'
    def sql(query):
        return docker('exec', '-i', 'serviceflow-eval-mysql-1', 'mysql', '--default-character-set=utf8mb4',
                      '-userviceflow', '-peval-only-password', '-N', '-B', 'serviceflow_eval_v2', stdin=query)
    assert sql('SELECT DATABASE();') == 'serviceflow_eval_v2'
    run_id = datetime.now().strftime('%Y%m%d%H%M%S') + f'{secrets.randbelow(10000):04}'
    output = ROOT / 'quality/evaluation/reports' / ('recovery-' + run_id)
    output.mkdir(parents=True, exist_ok=False)
    ids = ['lost-response'] if args.baseline else ['lost-response', 'concurrent', 'expired', 'rejected', 'unconfirmed', 'not-sent',
        'refresh-committed', 'refresh-unknown', 'refresh-account', 'refresh-storage']
    fixture_sql, receipt = prepare({'cases': [{'id': name, 'fixture': {
        'owner': 'customer', 'orderStatus': 'PROCESSING', 'paymentStatus': 'PAID'}} for name in ids]}, run_id)
    (output / 'fixtures.sql').write_text(fixture_sql, encoding='utf-8')
    write_json(output / 'fixtures.json', receipt)
    sql(fixture_sql)
    client.usernames = receipt['principals']
    for role in ('customer', 'other', 'guest', 'admin'):
        client.token(role)
    checks = []
    def check(name, condition):
        checks.append({'name': name, 'passed': bool(condition)})
        write_json(output / 'checks.json', checks)
        assert condition, name
        print(name, 'PASS', flush=True)
    def state(name):
        number = receipt['cases'][name]['orderNo']
        # Only generated decimal order numbers enter SQL; credentials and response bodies are not logged.
        assert number[2:].isdigit()
        columns = sql("SELECT o.status,o.version,p.status,p.refund_status,o.total_amount,"
            "(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),"
            "(SELECT COUNT(*) FROM refund_request r WHERE r.order_id=o.id),"
            "COALESCE((SELECT r.amount FROM refund_request r WHERE r.order_id=o.id),0),"
            "(SELECT COUNT(*) FROM refund_event e JOIN refund_request r ON r.id=e.refund_id WHERE r.order_id=o.id) "
            "FROM customer_order o JOIN payment p ON p.order_id=o.id WHERE o.order_no='" + number + "';").split('\t')
        return dict(zip(['order','version','payment','refund','amount','operations','refundRequests','refundAmount','events'],columns))
    def pending(name):
        session = client.request('POST', '/chat/sessions', {'title':'Synthetic recovery verification'}, 'customer')['body']['publicId']
        result = client.request('POST', f'/chat/sessions/{session}/messages/stream', {
            'message':'取消订单 ' + receipt['cases'][name]['orderNo'], 'clientRequestId':str(uuid.uuid4())}, 'customer')
        action = result['events']['action_required']['actionId']
        uuid.UUID(action)
        check(name + ': confirmation required, no side effect', state(name)['operations'] == '0' and state(name)['refundRequests'] == '0')
        return action
    def confirm(action, role='customer', decision='CONFIRM'):
        return client.request('POST', f'/chat/actions/{action}/confirm', {'decision':decision}, role)
    def result(action, role='customer'):
        return client.request('GET', f'/chat/actions/{action}/result', principal=role)
    def evict(action):
        check('evict only synthetic action', docker('exec','serviceflow-eval-redis-1','redis-cli','DEL','chat:action:'+action) == '1')
    action = pending('lost-response')
    write_json(output / 'actions.raw.json', {'lost-response': action})
    # This proxy consumes the upstream confirmation, then closes without returning any response to its client.
    upstream = []
    class DropResponse(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass
        def do_POST(self):
            self.rfile.read(int(self.headers['Content-Length']))
            upstream.append(confirm(action))
            self.connection.shutdown(socket.SHUT_RDWR)
            self.connection.close()
    proxy = ThreadingHTTPServer(('127.0.0.1',0), DropResponse)
    worker = threading.Thread(target=proxy.serve_forever, daemon=True)
    worker.start()
    lost = False
    try:
        req = urllib.request.Request(f'http://127.0.0.1:{proxy.server_port}/confirm',data=b'{}',method='POST')
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=20) as response:
            response.read()
    except (OSError, urllib.error.URLError):
        lost = True
    finally:
        proxy.shutdown(); proxy.server_close(); worker.join(timeout=5)
    check('HTTP client received no confirmation response', lost)
    check('upstream committed cancellation', upstream[0]['events'].get('done',{}).get('resultCode') == 'CANCELLED')
    committed = state('lost-response')
    check('one cancellation and one equal-amount refund', committed == {
        'order':'CANCELLED','version':'1','payment':'PAID','refund':'PROCESSING','amount':'99.00',
        'operations':'1','refundRequests':'1','refundAmount':'99.00','events':'0'})
    evict(action)
    before = docker('inspect','serviceflow-eval-server-1','--format','{{.State.StartedAt}}')
    docker('compose','-p','serviceflow-eval','-f',str(ROOT/'deploy/evaluation-compose.yml'),'restart','server')
    deadline = time.monotonic()+150
    while True:
        try:
            ready = client.request('GET','/actuator/health/readiness')['body'].get('status') == 'UP'
        except Exception:
            ready = False
        if ready: break
        if time.monotonic()>deadline: raise TimeoutError('isolated server restart')
        time.sleep(2)
    check('server process restarted', before != docker('inspect','serviceflow-eval-server-1','--format','{{.State.StartedAt}}'))
    recovered = result(action)
    if args.baseline:
        check('baseline has no durable result route', recovered['status'] in [404, 500])
        check('baseline duplicate confirmation cannot recover result', 'error' in confirm(action)['events'])
        check('committed business state survives', state('lost-response') == committed)
    else:
        check('owner recovers committed operation and current state', recovered['status']==200
            and recovered['body']['operation']['resultCode']=='CANCELLED'
            and recovered['body']['order']['refund']['amount']==99.0)
        for role, expected in [('other',404),('guest',403),('admin',403)]:
            check(role + ': cannot read recovered result', result(action,role)['status']==expected)
        check('read recovery repeats without side effects', result(action)['body']==recovered['body'] and state('lost-response')==committed)
        for role in ('other','guest','admin'):
            attempt=confirm(action,role)
            check(role + ': cannot execute missing action', attempt['status'] in [403,404] or 'error' in attempt['events'])
        check('unauthorized attempts changed nothing', state('lost-response')==committed)
        for name in ('unconfirmed','expired','rejected'):
            key=pending(name)
            if name=='expired': evict(key)
            if name=='rejected': confirm(key,decision='REJECT')
            check(name+': read cannot initiate execution', result(key)['status']==404 and state(name)['operations']=='0')
            if name!='unconfirmed':
                check(name+': stale confirmation is not executable', 'error' in confirm(key)['events'] and state(name)['operations']=='0')
        concurrent=pending('concurrent')
        check('other customer cannot confirm pending action', 'error' in confirm(concurrent,'other')['events'] and state('concurrent')['operations']=='0')
        with ThreadPoolExecutor(max_workers=2) as pool:
            responses=list(pool.map(lambda _:confirm(concurrent),range(2)))
        check('concurrent same-action confirmations return one result', all(r['events'].get('done',{}).get('resultCode')=='CANCELLED' for r in responses))
        check('concurrent calls create only one operation/refund', state('concurrent')==committed)
        callback={'eventId':str(uuid.uuid4()),'attemptNo':1,'outcome':'SUCCEEDED','amount':99,'reason':''}
        path=f'/admin/refunds/{action}/sandbox-events'
        check('sandbox refund completes', client.request('POST',path,callback,'admin')['status']==200)
        check('duplicate callback remains idempotent', client.request('POST',path,callback,'admin')['body']['replayed'])
        final=state('lost-response')
        check('recovery reflects final refund without re-executing', result(action)['body']['order']['refund']['status']=='SUCCEEDED'
              and final=={**committed,'refund':'REFUNDED','events':'1'})
    summary={'runId':run_id,'baseline':args.baseline,'mode':'demo','cloudCalls':0,'realServices':['HTTP','MySQL','Redis'],
             'faults':['drop HTTP response after upstream commit','delete exact synthetic Redis action key','restart eval server'],
             'checks':checks,'states':{name:state(name) for name in ids},'resultHttpStatus': recovered['status'],
             'paymentScope':'sandbox; no real funds'}
    write_json(output/'summary.json',summary)
    print('REPORT',output/'summary.json',flush=True)


if __name__ == '__main__':
    main()
