"""Evaluate the real Qwen reranker with a frozen, locally classified comparison suite."""

import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import re
import subprocess
import uuid

from eval_v2 import Client, ROOT, read_json, write_json

FIXTURE = ROOT / 'quality/evaluation/datasets/product-alias-gate-v1.json'
PROJECT = 'serviceflow-product-alias-eval'
# V2 replaces the three initial catalog rows. Import updates the overlapping SKUs in place.
BOOTSTRAP_SKUS = {'HUAWEI-PURA80-12-256', 'HUAWEI-PURA80PRO-12-256', 'HUAWEI-PURA80ULTRA-16-512'}
REQUIRED_SKU = 'APPLE-IP16-US-256'
REQUIRED_MENTION = '苹果16 256GB'
BUDGET = 36


def sha256(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def question(case):
    return '对比这 5 款手机：' + '、'.join(row['mention'] for row in case['targets'])


def load_fixture():
    fixture = read_json(FIXTURE)
    products = [fixture['catalogAddition']]
    for source, digest in fixture['catalogSources'].items():
        path = ROOT / source
        if sha256(path) != digest:
            raise ValueError(f'Catalog checksum changed: {source}')
        products.extend(read_json(path)['products'])
    by_sku = {row['sku']: row for row in products}
    cases = fixture['cases']
    if (len(by_sku) != len(products) or len(BOOTSTRAP_SKUS | by_sku.keys()) > 50 or len(cases) != 6
            or len({case['id'] for case in cases}) != 6 or len({question(case) for case in cases}) != 6
            or any(len(case['targets']) != 5 for case in cases)):
        raise ValueError('Expected a unique catalog of at most 50 SKUs and six five-slot cases')
    semantic = []
    for case in cases:
        for target in case['targets']:
            keys = target.keys() & {'semanticSku', 'confirmedSku', 'requiredCandidates'}
            if len(keys) != 1 or not 0 < len(target['mention']) <= 120:
                raise ValueError('Each nonempty mention needs one expected outcome')
            if 'semanticSku' in target:
                semantic.append(target)
                if not set([target['semanticSku'], *target.get('otherCompatibleSkus', [])]) <= by_sku.keys():
                    raise ValueError('Semantic SKU is outside the frozen catalog')
            elif 'confirmedSku' in target and target['confirmedSku'] not in by_sku:
                raise ValueError('Confirmed SKU is outside the frozen catalog')
            elif not set(target.get('requiredCandidates', [])) <= by_sku.keys():
                raise ValueError('Candidate SKU is outside the frozen catalog')
    forced = [case for case in cases if case.get('invalidClassification')]
    if (len(semantic) != 20 or len({row['mention'] for row in semantic}) != 20
            or len({by_sku[row['semanticSku']]['brand'] for row in semantic}) < 3
            or sum(all('semanticSku' in row for row in case['targets']) for case in cases) != 4
            or not any(row['mention'] == REQUIRED_MENTION and row['semanticSku'] == REQUIRED_SKU for row in semantic)
            or len(forced) != 1 or not all('semanticSku' in row for row in forced[0]['targets'])):
        raise ValueError('Semantic coverage or controlled classifier failure is missing')
    return cases, products


def classifier(cases):
    by_question = {question(case): case for case in cases}

    class FixtureClassifier(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            self.send_response(200 if self.path == '/health' else 404)
            self.end_headers()

        def do_POST(self):
            if self.path != '/v1/chat/completions':
                self.send_error(404)
                return
            try:
                request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                content = next(row['content'] for row in reversed(request['messages']) if row['role'] == 'user')
                if isinstance(content, list):
                    content = ''.join(row['text'] for row in content if row['type'] == 'text')
                case = by_question[json.loads(content)['message']]
            except (KeyError, ValueError, TypeError, StopIteration):
                self.send_error(400, 'Unknown classifier fixture')
                return

            # Complete the HTTP stream so the provider guard distinguishes a parse failure from an outage.
            if case.get('invalidClassification'):
                routing = {'intent': 'PRODUCT_ASK', 'comparison': False, 'targets': []}
            else:
                targets = [{'reference': 'NEW', 'mention': row['mention']} for row in case['targets']]
                routing = {'intent': 'PRODUCT_QUERY', 'comparison': True, 'targets': targets}
            chunk = {'id': 'fixture-classifier', 'object': 'chat.completion.chunk', 'created': 0,
                     'model': 'fixture-classifier', 'choices': [{'index': 0, 'finish_reason': 'stop',
                         'delta': {'role': 'assistant', 'content': json.dumps(routing, ensure_ascii=False)}}]}
            body = ('data: ' + json.dumps(chunk, ensure_ascii=False) + '\n\ndata: [DONE]\n\n').encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    return FixtureClassifier


def assess(case, response, catalog_ids, provider_calls):
    events = response['events']
    pending = events.get('comparison_selection_required', {})
    rows = pending.get('targets', [])
    violations = []
    if (response['status'] != 200 or 'done' not in events or 'error' in events
            or not pending.get('comparisonId') or 'product_comparison' in events or len(rows) != 5):
        violations.append('comparison response did not complete with five pending-list positions')
    if provider_calls != 1 + sum('semanticSku' in target for target in case['targets']):
        violations.append('unexpected classifier or reranker call count')
    slots = []
    for index, target in enumerate(case['targets']):
        row = rows[index] if index < len(rows) else {}
        confirmed = row.get('confirmed')
        candidates = row.get('candidates', [])
        products = candidates + ([confirmed] if confirmed else [])
        known_skus = [product['sku'] for product in products if catalog_ids.get(product.get('sku')) is not None
                      and catalog_ids[product['sku']] == product.get('id')]
        errors = []
        if row.get('label') != target['mention'] or row.get('skipped'):
            errors.append('slot label or state changed')
        if len(known_skus) != len(products) or len(known_skus) != len(set(known_skus)):
            errors.append('duplicate or unknown catalog identity')
        if 'semanticSku' in target:
            allowed = {target['semanticSku'], *target.get('otherCompatibleSkus', [])}
            if confirmed:
                errors.append('semantic match was auto-confirmed')
            if len(candidates) > 3 or set(known_skus) - allowed:
                errors.append('candidate exceeds Top 3 or conflicts with the target specification')
            hit = target['semanticSku'] in known_skus[:3] and not errors
        elif 'confirmedSku' in target:
            hit = None
            if not confirmed or known_skus != [target['confirmedSku']] or candidates:
                errors.append('exact catalog match was not confirmed')
        else:
            hit = None
            if confirmed or set(known_skus) != set(target['requiredCandidates']):
                errors.append('ambiguous or unknown target did not keep the expected candidates')
        slots.append({'index': index, 'mention': target['mention'], 'knownSkus': known_skus,
                      'top3Hit': hit, 'violations': errors})
    return {'id': case['id'], 'classifierFailureInjected': case.get('invalidClassification', False),
            'providerCalls': provider_calls, 'latencyMs': response['latencyMs'], 'violations': violations, 'slots': slots}


def docker(*args):
    result = subprocess.run(['docker', *args], capture_output=True, encoding='utf-8', timeout=45)
    if result.returncode:
        raise RuntimeError('Docker check failed; command output withheld')
    return result.stdout


def provider_state(container):
    state = json.loads(docker('inspect', container))[0]
    config = state['Config']
    settings = dict(item.split('=', 1) for item in config['Env'])
    required = {'MYSQL_DATABASE': 'serviceflow_eval_product_alias', 'SERVICEFLOW_AI_MODE': 'cloud',
                'SERVICEFLOW_RAG_MODE': 'demo', 'SERVICEFLOW_EVALUATION_ENABLED': 'true',
                'SERVICEFLOW_EVALUATION_PROVIDER_BUDGET_ENABLED': 'true',
                'SERVICEFLOW_EVALUATION_PROVIDER_REQUEST_LIMIT': str(BUDGET),
                'SERVICEFLOW_EVALUATION_STOP_ON_PROVIDER_FAILURE': 'true',
                'SERVICEFLOW_PRODUCT_COMPARISON_SEMANTIC_FALLBACK_ENABLED': 'true',
                'SERVICEFLOW_REFUND_DISPATCH_ENABLED': 'false', 'SPRING_AI_MODEL_EMBEDDING': 'none',
                'OPENAI_BASE_URL': 'http://product-alias-classifier:8000',
                'OPENAI_RERANKER_BASE_URL': 'https://dashscope.aliyuncs.com/api/v1',
                'OPENAI_RERANKER_MODEL': 'qwen3-rerank'}
    if (config['Labels'].get('com.docker.compose.project') != PROJECT
            or config['Labels'].get('com.docker.compose.service') != 'server' or state['RestartCount']
            or state['NetworkSettings']['Ports'].get('8080/tcp') != [{'HostIp': '127.0.0.1', 'HostPort': '18080'}]
            or any(settings.get(key) != value for key, value in required.items())
            or settings.get('OPENAI_RERANKER_API_KEY') in {None, '', 'disabled'}):
        raise ValueError('Expected the isolated loopback product alias stack with its Qwen gate configured')
    logs = docker('logs', state['Id'])
    if logs.count(f'Evaluation provider budget enabled: limit={BUDGET},') != 1:
        raise ValueError('Expected exactly one provider budget initialization')
    used = max((int(count) for count in re.findall(r'provider request admitted: used=(\d+)', logs)), default=0)
    stopped = any(marker in logs for marker in ('provider calls stopped:', 'provider budget exhausted:'))
    return (state['Id'], state['State']['StartedAt'], state['Image']), used, stopped


def import_catalog(client, products):
    def catalog():
        response = client.request('GET', '/products?size=50', principal='guest')
        if response['status'] != 200 or response['body']['total'] != len(response['body']['items']):
            raise ValueError('Could not read the complete isolated catalog')
        return {row['sku']: row for row in response['body']['items']}

    if set(catalog()) != BOOTSTRAP_SKUS:
        raise ValueError('A fresh isolated catalog is required')
    imported = client.request('POST', '/admin/products/import', {'products': products}, 'admin')
    if imported['status'] != 200 or imported['body']['imported'] != len(products):
        raise ValueError('Frozen catalog import failed')
    rows = catalog()
    if set(rows) != BOOTSTRAP_SKUS | {item['sku'] for item in products} or any(
            rows[item['sku']].get(key) != item[key] for item in products for key in item):
        raise ValueError('Stored catalog differs from freeze')
    return {sku: row['id'] for sku, row in rows.items()}


def summarize(turns, stopped):
    slots = [slot for turn in turns for slot in turn['slots']]
    hits = sum(slot['top3Hit'] is True for slot in slots)
    passed = (len(turns) == 6 and not stopped and hits >= 18
              and any(slot['mention'] == REQUIRED_MENTION and slot['top3Hit'] is True for slot in slots)
              and not any(row['violations'] for row in turns + slots))
    return {'passed': passed, 'top3Hits': hits, 'semanticSlots': 20}


def run(cases, products, container):
    identity, used, stopped = provider_state(container)
    if used or stopped:
        raise ValueError('The evaluation provider budget must be unused')
    client = Client('http://127.0.0.1:18080/api')
    catalog_ids = import_catalog(client, products)
    turns = []
    for case in cases:
        session = client.request('POST', '/chat/sessions', {'title': '商品语义评测'}, 'guest')
        if session['status'] != 200:
            raise RuntimeError('Could not create isolated comparison session')
        response = client.request('POST', f"/chat/sessions/{session['body']['publicId']}/messages/stream", {
            'message': question(case), 'clientRequestId': str(uuid.uuid4())}, 'guest')
        current, count, stopped = provider_state(container)
        if current != identity or count < used or count > BUDGET:
            raise RuntimeError('Provider process or budget changed during the run')
        turns.append(assess(case, response, catalog_ids, count - used))
        used = count
        if stopped or turns[-1]['violations']:
            break

    summary = summarize(turns, stopped)
    report = ROOT / f'quality/evaluation/reports/product-alias-gate-{uuid.uuid4().hex}.json'
    write_json(report, {**summary, 'fixtureSha256': sha256(FIXTURE), 'serverImage': identity[2],
                        'reranker': 'qwen3-rerank',
                        'providerRequests': used, 'providerStopped': stopped, 'turns': turns})
    print(f"Product alias gate: {summary['top3Hits']}/20 Top 3, {used}/{BUDGET} requests, {report.relative_to(ROOT)}")
    return 0 if summary['passed'] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('validate')
    stub = commands.add_parser('serve-classifier')
    stub.add_argument('--host', default='127.0.0.1')
    stub.add_argument('--port', default=8000, type=int)
    real = commands.add_parser('run')
    real.add_argument('--server', required=True)
    real.add_argument('--allow-cloud', action='store_true')
    args = parser.parse_args()
    if args.command == 'run' and not args.allow_cloud:
        parser.error('Real Qwen evaluation requires explicit data and cost authorization and --allow-cloud')
    cases, products = load_fixture()
    if args.command == 'serve-classifier':
        with ThreadingHTTPServer((args.host, args.port), classifier(cases)) as server:
            server.serve_forever()
    elif args.command == 'run':
        try:
            return run(cases, products, args.server)
        except (OSError, KeyError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
            # Network and parser exceptions can include raw upstream response content.
            parser.exit(1, f'Evaluation stopped ({type(error).__name__}); inspect the isolated stack locally.\n')
    else:
        total = len(BOOTSTRAP_SKUS | {item['sku'] for item in products})
        print(f'Validated 6 five-slot cases and {total} catalog SKUs')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
