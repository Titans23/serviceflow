"""Versioned local scenario evaluation. No model calls during validation or unit tests."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
SUITE = ROOT / 'quality/evaluation/datasets/serviceflow-eval-v2.json'
PACKAGE = ROOT / 'data/retail-v1'
INTENTS = {'CHAT', 'PRODUCT_QUERY', 'KNOWLEDGE_QUERY', 'ORDER_QUERY', 'COMPLAINT'}


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def validate_package(package=PACKAGE):
    manifest = read_json(package / 'manifest.json')
    products = read_json(package / 'catalog.json')['products']
    sources = {s['id']: s for s in manifest['sources']}
    assert len(sources) == len(manifest['sources']), 'duplicate source ID'
    skus = {p['sku'] for p in products}
    assert len(skus) == len(products), 'duplicate SKU'
    for source in sources.values():
        url = urllib.parse.urlparse(source['url'])
        assert (url.scheme == 'https' and url.hostname in {
            'consumer.huawei.com', 'support.apple.com', 'images.samsung.com', 'www.samsung.com'
        } and not url.username and not url.password and not url.port), 'unexpected source'
        assert re.fullmatch(r'\d{4}-\d{2}-\d{2}', source['checkedAt'])
        assert source['rights'] and source['review'] and source['sourceGroup']
    for product in products:
        assert product['specs']['source_id'] in sources
        assert product['listPrice'] is None or product['listPrice'] >= 0
        assert product['saleStatus'] in {'ON_SALE', 'OFF_SALE', 'DISCONTINUED'}
        source = sources[product['specs']['source_id']]
        assert product['specs']['source_url'] == source['url']
        assert product['specs']['source_checked_at'] == source['checkedAt']
    covered = set()
    sections = 0
    document_ids = set()
    for document in manifest['documents']:
        assert document['id'] not in document_ids, 'duplicate document ID'
        document_ids.add(document['id'])
        file = (package / document['file']).resolve()
        assert file.is_relative_to(package.resolve()), 'document escapes package'
        content = file.read_text(encoding='utf-8')
        assert set(document['skus']) <= skus
        covered.update(document['skus'])
        for source_id in document['sourceIds']:
            assert sources[source_id]['url'] in content
        ids = [s['id'] for s in document['sections']]
        assert len(set(ids)) == len(ids)
        for section in document['sections']:
            assert f"## {section['id']}：" in content
            assert section['sourceSection'] in content
        sections += len(ids)
    assert covered == skus, 'each SKU needs a knowledge document'
    return {'version': manifest['version'], 'products': len(products),
            'sources': len(sources), 'documents': len(document_ids), 'sections': sections}


def validate_suite(suite, package=PACKAGE):
    assert suite['version'] == 'serviceflow-eval-v2'
    assert suite['origin'] == 'project-authored', 'never label authored cases as user traffic'
    manifest = read_json(package / 'manifest.json')
    source_groups = {s['id']: s['sourceGroup'] for s in manifest['sources']}
    evidence_keys = {(sid, section['id']) for doc in manifest['documents']
                     for sid in doc['sourceIds'] for section in doc['sections']}
    ids, inputs, groups = set(), set(), {}
    chat_turns = multi = assertions = 0
    for case in suite['cases']:
        assert case['id'] not in ids, 'duplicate case ID'
        ids.add(case['id'])
        assert case['split'] in {'development', 'holdout'}
        assert case['principal'] in {'guest', 'customer', 'other', 'admin'}
        if 'expectedEvidence' in case:
            evidence = case['expectedEvidence']
            assert (evidence['sourceId'], evidence['sectionId']) in evidence_keys, 'unknown evidence section'
            assert source_groups[evidence['sourceId']] in case['sourceGroups'], 'evidence source missing from split group'
            assert case.get('referenceAnswer'), 'evidence case needs a reference answer'
        for group in case['sourceGroups']:
            assert groups.setdefault(group, case['split']) == case['split'], 'source group crosses splits'
        assert case['steps'], 'empty scenario'
        signature = json.dumps([case['principal'], case['steps']], ensure_ascii=False, sort_keys=True)
        assert signature not in inputs, 'duplicate scenario input and assertions'
        inputs.add(signature)
        turns = 0
        for step in case['steps']:
            assert step['type'] in {'chat', 'confirm', 'http'}
            expected = step['expect']
            assert expected, 'unasserted step'
            if 'intent' in expected:
                assert expected['intent'] in INTENTS
            if step['type'] == 'chat':
                assert step['message'].strip()
                turns += 1
                assert 'done' in expected.get('events', []) or 'error' in expected.get('events', [])
            if step['type'] == 'confirm':
                assert step['decision'] in {'CONFIRM', 'REJECT'}
            if step['type'] == 'http':
                assert step.get('method', 'GET') in {'GET', 'POST', 'PUT'}
                assert step['path'].startswith('/') and '://' not in step['path']
            for group in expected.get('textAll', []):
                assert isinstance(group, list) and group and all(isinstance(x, str) and x for x in group)
            assertions += len(expected)
        chat_turns += turns
        multi += turns > 1
    assert ids
    return {'cases': len(ids), 'chatTurns': chat_turns, 'multiTurnCases': multi,
            'assertionGroups': assertions, 'splits': {s: sum(c['split'] == s for c in suite['cases'])
                                                   for s in ('development', 'holdout')}}


def parse_sse(text):
    events = []
    # SSE data can span multiple lines. A token remains text even if it looks like JSON.
    for block in re.split(r'\r?\n\r?\n', text):
        name, data = 'message', []
        for line in block.splitlines():
            if line.startswith('event:'):
                name = line[6:].lstrip(' ')
            elif line.startswith('data:'):
                data.append(line[5:].removeprefix(' '))
        if data:
            value = '\n'.join(data)
            events.append((name, value if name == 'token' else json.loads(value)))
    return events


class Client:
    def __init__(self, base_url):
        parsed = urllib.parse.urlparse(base_url)
        if parsed.scheme != 'http' or parsed.hostname not in {'localhost', '127.0.0.1', '::1'}:
            raise ValueError('Use a loopback HTTP evaluation instance, not a production endpoint.')
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError('Invalid base URL')
        self.base_url = base_url.rstrip('/')
        self.tokens = {}
        self.usernames = {}
        # Local requests must not inherit a system HTTP proxy or forward credentials on redirects.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs):
                return None
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(self, method, path, body=None, principal=None, content_type='application/json'):
        if not path.startswith('/') or path.startswith('//') or '://' in path:
            raise ValueError('Invalid API path')
        headers = {'Content-Type': content_type}
        if principal:
            headers['Authorization'] = 'Bearer ' + self.token(principal)
        data = body if isinstance(body, bytes) else None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        started = time.monotonic()
        try:
            response = self.opener.open(request, timeout=125)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read().decode('utf-8')
            is_sse = 'text/event-stream' in response.headers.get('Content-Type', '')
            events = parse_sse(raw) if is_sse else []
            return {'status': response.code, 'body': None if is_sse or not raw else json.loads(raw),
                    'events': dict(events), 'eventNames': [name for name, _ in events],
                    'answer': ''.join(value for name, value in events if name == 'token'),
                    'latencyMs': round((time.monotonic() - started) * 1000)}

    def token(self, principal):
        if principal not in self.tokens:
            if principal == 'guest':
                result = self.request('POST', '/auth/guest')
            else:
                prefix = 'SERVICEFLOW_EVAL_' + principal.upper()
                default_user = self.usernames.get(principal, {'customer': 'customer', 'admin': 'admin'}.get(principal, ''))
                default_password = {'customer': 'Customer123!', 'other': 'Customer123!', 'admin': 'Admin123!'}.get(principal, '')
                result = self.request('POST', '/auth/login', {
                    'username': os.getenv(prefix + '_USERNAME', default_user),
                    'password': os.getenv(prefix + '_PASSWORD', default_password)})
            if result['status'] != 200:
                raise RuntimeError(f'Authentication failed for role {principal}; no credential was logged.')
            self.tokens[principal] = result['body']['accessToken']
        return self.tokens[principal]


def field(value, path):
    for part in path.split('.'):
        value = value[int(part)] if isinstance(value, list) else value[part]
    return value


def expand(value, variables):
    if isinstance(value, str):
        match = re.fullmatch(r'\{\{([^{}]+)}}', value)
        if match:
            return field(variables, match[1])
        return re.sub(r'\{\{([^{}]+)}}', lambda m: str(field(variables, m[1])), value)
    if isinstance(value, list):
        return [expand(x, variables) for x in value]
    if isinstance(value, dict):
        return {k: expand(v, variables) for k, v in value.items()}
    return value


def check(result, expected):
    checks = {'httpStatus': result['status'] == expected.get('status', 200)}
    if 'events' in expected:
        checks['events'] = all(x in result['eventNames'] for x in expected['events'])
        if 'error' not in expected['events']:
            checks['noUnexpectedError'] = 'error' not in result['eventNames']
    if 'absentEvents' in expected:
        checks['absentEvents'] = not any(x in result['eventNames'] for x in expected['absentEvents'])
    if 'intent' in expected:
        checks['intent'] = result['events'].get('meta', {}).get('intent') == expected['intent']
    if 'products' in expected:
        checks['products'] = set(result['events'].get('meta', {}).get('productIds', [])) == set(expected['products'])
    if 'citations' in expected:
        checks['documentCitations'] = set(expected['citations']) <= set(result['events'].get('meta', {}).get('citations', []))
    normalize = lambda s: re.sub(r'[\s*_`#]', '', s).casefold()
    answer = normalize(result['answer'])
    if 'textAll' in expected:
        checks['phraseCoverage'] = all(any(normalize(term) in answer for term in group) for group in expected['textAll'])
    if 'textNone' in expected:
        checks['forbiddenPhrases'] = all(normalize(term) not in answer for term in expected['textNone'])
    for path, wanted in expected.get('json', {}).items():
        try:
            checks['json:' + path] = field(result, path) == wanted
        except (KeyError, IndexError, TypeError, ValueError):
            checks['json:' + path] = False
    return checks


def run_case(client, case, variables):
    variables = dict(variables)
    steps = []
    for index, original in enumerate(case['steps']):
        try:
            step = expand(original, variables)
            role = step.get('principal', case['principal'])
            if step['type'] == 'chat':
                if 'sessionId' not in variables:
                    session = client.request('POST', '/chat/sessions', {'title': '评测 ' + case['id']}, role)
                    if session['status'] != 200:
                        raise RuntimeError('Could not create evaluation session')
                    variables['sessionId'] = session['body']['publicId']
                request_id = variables.setdefault(step.get('requestKey', f'request-{index}'), str(uuid.uuid4()))
                result = client.request('POST', f"/chat/sessions/{variables['sessionId']}/messages/stream", {
                    'message': step['message'], 'clientRequestId': request_id,
                    'pageContext': {'productId': step['productId']} if 'productId' in step else None}, role)
            elif step['type'] == 'confirm':
                action = field(variables, step.get('actionRef', 'action'))
                result = client.request('POST', f"/chat/actions/{action['actionId']}/confirm", {'decision': step['decision']}, role)
            else:
                result = client.request(step.get('method', 'GET'), step['path'], step.get('body'), role)
            if 'action_required' in result['events']:
                variables['action'] = result['events']['action_required']
            for name, path in step.get('capture', {}).items():
                variables[name] = field(result, path)
            checks = check(result, step['expect'])
            steps.append({'index': index, 'checks': checks, 'passed': all(checks.values()), **result})
        except Exception as error:
            # Do not serialize network request objects, credentials or arbitrary exception messages.
            steps.append({'index': index, 'passed': False, 'errorType': type(error).__name__, 'checks': {'execution': False}})
            break
    return {'id': case['id'], 'split': case['split'], 'passed': len(steps) == len(case['steps']) and all(s['passed'] for s in steps),
            'steps': steps, 'semanticReview': 'pending' if case.get('referenceAnswer') else 'not_applicable'}


def summarize(results):
    counts = {}
    for result in results:
        for step in result['steps']:
            for name, passed in step['checks'].items():
                item = counts.setdefault(name, {'passed': 0, 'total': 0})
                item['total'] += 1
                item['passed'] += bool(passed)
    return {'cases': len(results), 'passed': sum(r['passed'] for r in results), 'checks': counts,
            'semanticAccuracy': None, 'note': '规则/业务断言；短语覆盖不是事实正确率，引用文件指标不是 chunk 召回指标。'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['validate', 'run'])
    parser.add_argument('--suite', type=Path, default=SUITE)
    parser.add_argument('--package', type=Path, default=PACKAGE)
    parser.add_argument('--base-url', default='http://127.0.0.1:18080/api')
    parser.add_argument('--fixtures', type=Path)
    parser.add_argument('--split', choices=['development', 'holdout', 'all'], default='development')
    parser.add_argument('--mode', choices=['demo', 'cloud'], default='demo')
    parser.add_argument('--allow-cloud', action='store_true')
    parser.add_argument('--max-chat-turns', type=int, default=0)
    parser.add_argument('--workers', type=int, choices=[1, 2, 3], default=1)
    parser.add_argument('--case-ids', nargs='+', help='Rerun only these IDs within the selected split')
    parser.add_argument('--output', type=Path, default=ROOT / 'quality/evaluation/reports')
    args = parser.parse_args()
    if args.command == 'run' and not args.output.resolve().is_relative_to((ROOT / 'quality/evaluation/reports').resolve()):
        parser.error('Raw evaluation outputs must stay under the ignored quality/evaluation/reports directory.')
    suite = read_json(args.suite)
    inventory = {'package': validate_package(args.package), 'suite': validate_suite(suite, args.package)}
    if args.command == 'validate':
        print(json.dumps(inventory, ensure_ascii=False, indent=2))
        return
    cases = [c for c in suite['cases'] if args.split == 'all' or c['split'] == args.split]
    if args.case_ids:
        missing_ids = set(args.case_ids) - {c['id'] for c in cases}
        if missing_ids:
            parser.error('Case IDs not in selected split: ' + ', '.join(sorted(missing_ids)))
        cases = [c for c in cases if c['id'] in args.case_ids]
    turns = sum(s['type'] == 'chat' for c in cases for s in c['steps'])
    if args.mode == 'cloud' and (not args.allow_cloud or args.max_chat_turns < turns):
        parser.error(f'Cloud evaluation requires explicit --allow-cloud and --max-chat-turns >= {turns}. Each turn can call several models.')
    client = Client(args.base_url)
    info = client.request('GET', '/actuator/info')
    modes = (info['body'] or {}).get('serviceflow', {})
    if str(modes.get('evaluationEnabled')).lower() != 'true':
        parser.error('Use an explicitly isolated instance with SERVICEFLOW_EVALUATION_ENABLED=true.')
    if suite.get('requiresSandbox') and str(modes.get('refundSandboxEnabled')).lower() != 'true':
        parser.error('This suite requires SERVICEFLOW_REFUND_SANDBOX_ENABLED=true on the isolated instance.')
    if modes.get('aiMode') != args.mode or modes.get('ragMode') != args.mode:
        parser.error('Server AI/RAG mode could not be verified or differs from --mode.')
    fixtures = read_json(args.fixtures) if args.fixtures else {'cases': {}}
    missing = [c['id'] for c in cases if c.get('fixture') and c['id'] not in fixtures['cases']]
    if missing:
        parser.error('Missing scenario fixtures: ' + ', '.join(missing))
    client.usernames = fixtures.get('principals', {})
    products = {}
    page = 0
    while True:
        response = client.request('GET', f'/products?size=50&page={page}', principal='customer')
        if response['status'] != 200:
            parser.error('Cannot load catalog')
        body = response['body']
        products.update({p['sku']: p['id'] for p in body['items']})
        if (page + 1) * 50 >= body['total']:
            break
        page += 1
    run_id = time.strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8]
    output = args.output / run_id
    output.mkdir(parents=True, exist_ok=False)
    print('RUN', run_id, 'cases', len(cases), 'turns', turns, 'workers', args.workers, flush=True)
    # Authenticate before parallel execution; every scenario still owns its session and fixtures.
    roles = {s.get('principal', c['principal']) for c in cases for s in c['steps']}
    for role in sorted(roles):
        client.token(role)
    results = []
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        pending = [executor.submit(run_case, client, case, {'products': products, **fixtures['cases'].get(case['id'], {})}) for case in cases]
        for future in as_completed(pending):
            result = future.result()
            results.append(result)
            # Preserve paid work after each completed scenario, even if the run is interrupted.
            with (output / 'completed.raw.jsonl').open('a', encoding='utf-8') as checkpoint:
                checkpoint.write(json.dumps(result, ensure_ascii=False) + '\n')
            print(len(results), '/', len(cases), result['id'], 'PASS' if result['passed'] else 'FAIL', flush=True)
    order = {c['id']: i for i, c in enumerate(cases)}
    results.sort(key=lambda r: order[r['id']])
    files = ['manifest.json', 'catalog.json'] + [d['file'] for d in read_json(args.package / 'manifest.json')['documents']]
    package_hashes = {name: hashlib.sha256((args.package / name).read_bytes()).hexdigest() for name in files}
    metadata = {'runId': run_id, 'mode': args.mode, 'split': args.split, 'suiteSha256': hashlib.sha256(args.suite.read_bytes()).hexdigest(),
                'packageFileSha256': package_hashes,
                'inventory': inventory, 'chatTurns': turns, 'workers': args.workers, 'caseIds': [c['id'] for c in cases], 'results': results}
    # Full responses stay in the ignored local reports directory, never in public summary files.
    write_json(output / 'responses.raw.json', metadata)
    summary = summarize(results)
    write_json(output / 'summary.json', {k: v for k, v in metadata.items() if k != 'results'} | summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise SystemExit(0 if summary['passed'] == summary['cases'] else 1)


if __name__ == '__main__':
    main()
