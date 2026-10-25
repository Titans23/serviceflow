"""Save actual immutable chunk evidence from the isolated cloud evaluation Milvus (read-only)."""
import argparse
import json
from pathlib import Path
import re
import subprocess
from eval_v2 import ROOT, read_json, write_json


def load_diagnostics(run, directory, required_steps=()):
    records, ids = {}, set()
    for case in run['results']:
        for step in case['steps']:
            reference = step.get('events', {}).get('rag_diagnostic')
            if not reference:
                continue
            trace_id = reference.get('traceId', '')
            if not isinstance(trace_id, str) or not re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', trace_id):
                raise ValueError('Invalid diagnostic ID')
            trace = read_json(directory / (trace_id + '.raw.json'))
            if trace.get('traceId') != trace_id or not isinstance(trace.get('stages'), list):
                raise ValueError('Diagnostic record does not match the request')
            records[f"{case['id']}:{step['index']}"] = trace
            for stage in trace['stages']:
                for item in stage.get('input', []) + stage.get('output', []):
                    chunk = item['chunkId']
                    if not isinstance(chunk, str) or not re.fullmatch(r'[a-zA-Z0-9_-]{1,100}', chunk):
                        raise ValueError('Invalid diagnostic chunk ID')
                    ids.add(chunk)
    missing = set(required_steps) - records.keys()
    if missing:
        raise ValueError('Missing required RAG trace steps: ' + ', '.join(sorted(missing)))
    if not records:
        raise ValueError('No evaluation-stage traces found; do not claim complete diagnostics')
    return records, ids


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--docker', default='docker')
    parser.add_argument('--trace-directory', type=Path, help='Ignored directory mounted by the cloud evaluation profile')
    parser.add_argument('--require-trace-steps', nargs='+', default=[], help='Explicit required CASE_ID:STEP_INDEX entries; other non-RAG steps may omit a trace')
    args = parser.parse_args()
    if not args.run.resolve().is_relative_to((ROOT / 'quality/evaluation/reports').resolve()):
        parser.error('Use an ignored local evaluation run')
    data = read_json(args.run)
    ids = {item['chunkId'] for case in data['results'] for step in case['steps']
           for item in step.get('events', {}).get('rag_evidence', [])}
    diagnostics = None
    if args.trace_directory:
        if not args.trace_directory.resolve().is_relative_to((ROOT / 'quality/evaluation/reports').resolve()):
            parser.error('Diagnostic files must remain in ignored reports')
        try:
            diagnostics, candidates = load_diagnostics(data, args.trace_directory, args.require_trace_steps)
        except (ValueError, FileNotFoundError) as error:
            write_json(args.run.parent / 'diagnostic-coverage.json', {
                'status': 'incomplete', 'requiredSteps': args.require_trace_steps, 'errorType': type(error).__name__,
                'reason': str(error) if isinstance(error, ValueError) else 'Required diagnostic file unavailable'})
            raise
        write_json(args.run.parent / 'diagnostic-coverage.json', {
            'status': 'complete_for_required_steps', 'requiredSteps': args.require_trace_steps,
            'recordedSteps': list(diagnostics), 'missingSteps': []})
        ids.update(candidates)
    elif args.require_trace_steps:
        parser.error('--require-trace-steps requires --trace-directory')
    if (not ids and diagnostics is None) or not all(re.fullmatch(r'[a-zA-Z0-9_-]{1,100}', i) for i in ids):
        parser.error('Missing or invalid chunk trace')
    records = {}
    for offset in range(0, len(ids), 100):
        batch = sorted(ids)[offset:offset + 100]
        body = {'collectionName': 'serviceflow_chunks', 'filter': 'chunkId in ' + json.dumps(batch),
                'outputFields': ['chunkId', 'source', 'content'], 'limit': 100, 'consistencyLevel': 'Strong'}
        response = subprocess.run([args.docker, 'exec', '-i', 'serviceflow-cloud-eval-milvus-1',
            'curl', '-fsS', 'http://localhost:19530/v2/vectordb/entities/query',
            '-H', 'Content-Type: application/json', '--data-binary', '@-'],
            input=json.dumps(body), capture_output=True, text=True, encoding='utf-8', check=True)
        result = json.loads(response.stdout)
        if result.get('code') != 0:
            raise ValueError('Milvus query failed')
        records.update({row['chunkId']: row for row in result['data']})
    if records.keys() != ids:
        raise ValueError('Not all retrieved chunks still exist; cannot claim complete evidence')
    write_json(args.run.parent / 'evidence.raw.json', records)
    if diagnostics is not None:
        write_json(args.run.parent / 'rag-diagnostics.raw.json', diagnostics)
    print('Captured', len(records), 'unique retrieved chunks')


if __name__ == '__main__':
    main()
