"""Save actual immutable chunk evidence from the isolated cloud evaluation Milvus (read-only)."""
import argparse
import json
from pathlib import Path
import re
import subprocess
from eval_v2 import ROOT, read_json, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--docker', default='docker')
    args = parser.parse_args()
    if not args.run.resolve().is_relative_to((ROOT / 'quality/evaluation/reports').resolve()):
        parser.error('Use an ignored local evaluation run')
    data = read_json(args.run)
    ids = {item['chunkId'] for case in data['results'] for step in case['steps']
           for item in step.get('events', {}).get('rag_evidence', [])}
    if not ids or not all(re.fullmatch(r'[a-zA-Z0-9_-]{1,100}', i) for i in ids):
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
    print('Captured', len(records), 'unique retrieved chunks')


if __name__ == '__main__':
    main()
