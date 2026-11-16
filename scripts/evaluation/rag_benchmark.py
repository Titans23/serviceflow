"""Run the five fixed-product RAG configurations through the real application."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import random
import re
import subprocess
import time
import uuid

from eval_v2 import Client, ROOT, read_json, write_json
from prepare_rag_benchmark import DESTINATION, validate
from prepare_rag_benchmark_v2 import validate as validate_v2
from rag_metrics import evidence_key, score, summarize

MODES = ('BM25', 'DENSE', 'HYBRID', 'HYBRID_RERANK', 'HYBRID_RERANK_REWRITE')
TRACE_DIR = ROOT / 'quality/evaluation/reports/rag-benchmark-traces'


def docker(*args, body=None):
    result = subprocess.run(['docker', *args], input=body, text=True, encoding='utf-8',
                            capture_output=True, timeout=45)
    if result.returncode:
        raise RuntimeError('Docker command failed; inspect local services (output not published)')
    return result.stdout


def provider_state(container):
    state = json.loads(docker('inspect', container))[0]
    logs = docker('logs', container)
    counts = [int(n) for n in re.findall(r'provider request admitted: used=(\d+)', logs)]
    if any(marker in logs for marker in ('provider calls stopped:', 'provider budget exhausted:')):
        raise RuntimeError('Provider guard stopped; no automatic paid retry')
    env = dict(item.split('=', 1) for item in state['Config']['Env'])
    required = {'SERVICEFLOW_EVALUATION_PROVIDER_REQUEST_LIMIT': '2400',
                'SERVICEFLOW_EVALUATION_PROVIDER_BUDGET_ENABLED': 'true',
                'SERVICEFLOW_EVALUATION_STOP_ON_PROVIDER_FAILURE': 'true',
                'SERVICEFLOW_EVALUATION_ENABLED': 'true', 'SERVICEFLOW_AI_MODE': 'cloud',
                'SERVICEFLOW_RAG_MODE': 'cloud', 'SERVICEFLOW_REFUND_DISPATCH_ENABLED': 'false'}
    if not all(env.get(k) == v for k, v in required.items()):
        raise ValueError('Isolated evaluation configuration mismatch')
    if logs.count('Evaluation provider budget enabled:') != 1 or state['RestartCount']:
        raise ValueError('Provider counter initialization/restart invalid')
    return {'identity': (state['Id'], state['State']['StartedAt']), 'image': state['Image'],
            'used': max(counts, default=0), 'models': {k: env[k] for k in
                    ('OPENAI_MODEL', 'OPENAI_EMBEDDING_MODEL', 'OPENAI_RERANKER_MODEL')}}


def capture_chunks(ids, container):
    ids = sorted(set(ids))
    if not ids:
        return {}
    if not all(re.fullmatch(r'[a-zA-Z0-9_-]{1,100}', value) for value in ids):
        raise ValueError('Invalid evidence identity')
    rows = {}
    for offset in range(0, len(ids), 100):
        request = {'collectionName': 'serviceflow_chunks', 'filter': 'chunkId in ' + json.dumps(ids[offset:offset+100]),
                   'outputFields': ['chunkId', 'source', 'content', 'productId', 'documentVersionId'],
                   'limit': 100, 'consistencyLevel': 'Strong'}
        response = json.loads(docker('exec', '-i', container, 'curl', '-fsS',
            'http://localhost:19530/v2/vectordb/entities/query', '-H', 'Content-Type: application/json',
            '--data-binary', '@-', body=json.dumps(request)))
        if response.get('code') != 0:
            raise ValueError('Milvus evidence read failed')
        rows.update({row['chunkId']: row for row in response['data']})
    if set(rows) != set(ids):
        raise ValueError('Missing intermediate evidence; cannot score recall')
    return rows


def write_report(output, rows, cases, metadata, stop):
    modes = metadata['modes']
    result = {'metadata': metadata, 'stopReason': stop, 'summary': summarize(rows, cases, modes),
              'perCategory': {category: summarize([r for r in rows if r['caseId'] in
                  {c['id'] for c in cases if c['category'] == category}],
                  [c for c in cases if c['category'] == category], modes)
                  for category in sorted({c['category'] for c in cases})},
              'perSplit': {split: summarize([r for r in rows if r['caseId'] in
                  {c['id'] for c in cases if c['split'] == split}],
                  [c for c in cases if c['split'] == split], modes)
                  for split in sorted({c['split'] for c in cases})},
              'limitations': ['Section-level relevance on fixed-product knowledge QA; not whole-agent routing.',
                  'Known regression and project-authored challenges, no independent human-labelled holdout.',
                  'Answer grading follows the frozen label version; v2 permits explicitly labelled optional facts.',
                  'One measured attempt per case/configuration, balanced order, concurrency one; P95 is not a production SLA.',
                  'Failures/unattempted questions remain in planned quality denominators; latency lists their coverage separately.',
                  'No invented provider token usage or billing; provider request counts are recorded separately.']}
    write_json(output / 'summary.json', result)
    lines = ['# RAG retrieval ablation', '', 'Actual result; see summary.json for scope and failed/unattempted counts.', '',
             '| Mode | Valid/planned | Recall@5 | MRR@5 | Answer pass | P95 ms |',
             '|---|---:|---:|---:|---:|---:|']
    for mode, row in result['summary'].items():
        fmt = lambda value: 'N/A' if value is None else f'{value:.4f}'
        lines.append(f"| {mode} | {row['valid']}/{row['planned']} | {fmt(row['recallAt5'])} | "
                     f"{fmt(row['mrrAt5'])} | {fmt(row['answerPassRate'])} | {fmt(row['p95Ms'])} |")
    lines += ['', 'Stop reason: ' + str(stop), '', *['- ' + note for note in result['limitations']]]
    for split, summaries in result['perSplit'].items():
        lines += ['', '## ' + split, '', '| Mode | Valid/planned | Recall@5 | MRR@5 | Answer pass | P95 ms |',
                  '|---|---:|---:|---:|---:|---:|']
        for mode, row in summaries.items():
            lines.append(f"| {mode} | {row['valid']}/{row['planned']} | {fmt(row['recallAt5'])} | "
                         f"{fmt(row['mrrAt5'])} | {fmt(row['answerPassRate'])} | {fmt(row['p95Ms'])} |")
    (output / 'report.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def run(args):
    if not args.allow_cloud:
        raise SystemExit('Explicit authorization required for the selected frozen suite; shared <=2400 provider requests including ingestion')
    suite = read_json(args.suite)
    validator = validate_v2 if suite['version']=='rag-retrieval-v2' else validate
    inventory = validator(suite)
    cases = suite['cases']
    expected_cases = {'rag-retrieval-v1':104, 'rag-retrieval-v2':144}
    if suite['version'] not in expected_cases or len(cases) != expected_cases[suite['version']]:
        raise ValueError('Recalculate and approve scope when the dataset changes')
    modes = tuple(suite.get('modes', MODES))
    status = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True)
    if status.strip():
        raise ValueError('Commit the reviewed implementation and frozen labels before the real run')
    client = Client('http://127.0.0.1:18080/api')
    client.token('admin')
    client.token('customer')
    documents = client.request('GET', '/admin/knowledge-documents', principal='admin')['body']
    products = client.request('GET', '/products?size=50&page=0', principal='customer')['body']['items']
    ids = {p['sku']: p['id'] for p in products}
    if not all(c['sku'] in ids for c in cases):
        raise ValueError('Import the frozen corpus first')
    before = provider_state(args.server)
    engine = json.loads(docker('info', '--format', '{{json .}}'))
    output = ROOT / 'quality/evaluation/reports' / ('rag-benchmark-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
    output.mkdir(parents=True)
    metadata = {'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                'datasetSha256': hashlib.sha256(args.suite.read_bytes()).hexdigest(),
                'sourceHashes': suite['sourceHashes'], 'inventory': inventory, 'runtime': before,
                'providerRequestLimit': 2400, 'concurrency': 1, 'seed': 20261116,
                'modes': modes, 'suiteVersion': suite['version'],
                'warmupRequests': len(modes), 'measuredRequests': len(cases)*len(modes), 'startedAt': datetime.now().astimezone().isoformat()}
    metadata['environment'] = {'dockerVersion': engine['ServerVersion'], 'cpus': engine['NCPU'],
                               'memoryBytes': engine['MemTotal'], 'runningContainers': engine['ContainersRunning']}
    write_json(output / 'metadata.json', metadata)
    rows, stop = [], None
    shuffled = list(cases)
    random.Random(metadata['seed']).shuffle(shuffled)
    schedule = [(cases[0], mode, True) for mode in modes]
    for index, case in enumerate(shuffled):
        order = modes[index % len(modes):] + modes[:index % len(modes)]
        schedule.extend((case, mode, False) for mode in order)
    print('OUTPUT', output, flush=True)
    for case, mode, warmup in schedule:
        row = {'caseId': case['id'], 'mode': mode, 'warmup': warmup}
        started = time.monotonic()
        try:
            current = provider_state(args.server)
            if current['identity'] != before['identity']:
                raise ValueError('Evaluation process changed')
            started = time.monotonic()
            response = client.request('POST', '/admin/evaluation/rag',
                {'question': case['question'], 'productId': ids[case['sku']], 'mode': mode}, 'admin')
            row['clientMs'] = (time.monotonic() - started) * 1000
            row['httpStatus'] = response['status']
            row['response'] = response['body']
            if response['status'] != 200:
                raise RuntimeError('Evaluation request failed: ' + str(response['status']))
            body = response['body']
            trace_id = body['result']['diagnosticId']
            uuid.UUID(trace_id)
            trace = read_json(args.trace_dir / (trace_id + '.raw.json'))
            candidates = {entry['chunkId'] for stage in trace['stages']
                          for entry in stage['input'] + stage['output']}
            chunks = capture_chunks(candidates, args.milvus)
            if any(c['productId'] != ids[case['sku']] or c['documentVersionId'] not in trace['activeVersionIds']
                   for c in chunks.values()):
                raise ValueError('Evidence scope/version mismatch')
            row.update(response=body, trace=trace, chunks=chunks, score=score(case, body, trace, chunks))
            current = provider_state(args.server)
            row['providerRequestsAfter'] = current['used']
            if current['identity'] != before['identity']:
                raise ValueError('Evaluation process changed')
        except Exception as error:
            row.setdefault('clientMs', (time.monotonic() - started) * 1000)
            row.pop('score', None)
            row['errorType'] = type(error).__name__
            row['errorMessage'] = str(error)  # Local ignored evidence only, never the public summary.
            stop = type(error).__name__ + ': execution stopped; no automatic retry'
        rows.append(row)
        with (output / 'attempts.raw.jsonl').open('a', encoding='utf-8') as file:
            file.write(json.dumps(row, ensure_ascii=False) + '\n')
        write_report(output, rows, cases, metadata, stop)
        print(case['id'], mode, 'WARMUP' if warmup else 'MEASURED',
              'ERROR' if stop else 'ANSWER_PASS=' + str(row['score']['answerPass']), flush=True)
        if stop:
            break
    if not stop:
        if client.request('GET', '/admin/knowledge-documents', principal='admin')['body'] != documents:
            stop = 'Knowledge versions changed'
        validator(suite)
        if hashlib.sha256(args.suite.read_bytes()).hexdigest() != metadata['datasetSha256']:
            stop = 'Dataset changed during execution'
        metadata['runtimeAfter'] = provider_state(args.server)
    metadata['finishedAt'] = datetime.now().astimezone().isoformat()
    write_report(output, rows, cases, metadata, stop)
    if stop:
        raise SystemExit('Stopped with partial evidence: ' + str(output))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', type=Path, default=DESTINATION)
    parser.add_argument('--allow-cloud', action='store_true')
    parser.add_argument('--server', default='serviceflow-rag-bench-server-1')
    parser.add_argument('--milvus', default='serviceflow-rag-bench-milvus-1')
    parser.add_argument('--trace-dir', type=Path, default=TRACE_DIR)
    run(parser.parse_args())
