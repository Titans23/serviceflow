"""Import the reviewed retail-v1 package through existing APIs with local receipts."""
import argparse
import hashlib
from pathlib import Path
import time
import uuid

from eval_v2 import Client, PACKAGE, ROOT, read_json, validate_package, write_json


def multipart(fields, filename, content):
    boundary = 'serviceflow-' + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode('utf-8'))
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: text/markdown\r\n\r\n'.encode('utf-8'))
    parts.extend([content, f'\r\n--{boundary}--\r\n'.encode('ascii')])
    return b''.join(parts), 'multipart/form-data; boundary=' + boundary


def import_package(client, package, receipt_path, allow_cloud=False):
    validate_package(package)
    info = client.request('GET', '/actuator/info')['body'] or {}
    mode = info.get('serviceflow', {}).get('ragMode')
    if mode not in {'demo', 'cloud'} or (mode == 'cloud' and not allow_cloud):
        raise ValueError('Cloud embedding ingestion needs explicit --allow-cloud-ingestion; server mode must be verifiable.')
    receipt = read_json(receipt_path) if receipt_path.exists() else {'baseUrl': client.base_url, 'documents': {}}
    if receipt['baseUrl'] != client.base_url:
        raise ValueError('Import receipt belongs to a different instance')
    manifest = read_json(package / 'manifest.json')
    response = client.request('POST', '/admin/products/import', read_json(package / 'catalog.json'), 'admin')
    if response['status'] != 200:
        raise RuntimeError('Catalog import failed')
    products = {p['sku']: p['id'] for p in response['body']['products']}
    listing = client.request('GET', '/admin/knowledge-documents', principal='admin')
    if listing['status'] != 200:
        raise RuntimeError('Cannot inspect existing knowledge versions')
    existing = {d['title']: d for d in listing['body']}
    for document in manifest['documents']:
        content = (package / document['file']).read_bytes()
        digest = hashlib.sha256(content).hexdigest()
        for sku in document['skus'] or [None]:
            title = document['title'] + (f'/{sku}' if sku else '')
            previous, current = receipt['documents'].get(title), existing.get(title)
            if current:
                if not previous or previous['documentId'] != current['publicId'] or previous['versionId'] != current['latestVersionId']:
                    raise RuntimeError(f'Existing document changed outside this receipt: {title}; inspect it before importing.')
                if previous['sha256'] == digest:
                    print(title, 'UNCHANGED', current['latestStatus'])
                    continue
                path, fields = f"/admin/knowledge-documents/{current['publicId']}/versions", {}
            else:
                path = '/admin/knowledge-documents'
                fields = {'title': title, 'documentType': document['documentType']}
                if sku:
                    fields['productId'] = products[sku]
            body, content_type = multipart(fields, Path(document['file']).name, content)
            result = client.request('POST', path, body, 'admin', content_type)
            if result['status'] != 200:
                raise RuntimeError(f'Knowledge upload failed: {title}')
            listing = client.request('GET', '/admin/knowledge-documents', principal='admin')
            row = next(d for d in listing['body'] if d['title'] == title)
            receipt['documents'][title] = {'documentId': row['publicId'], 'versionId': result['body']['id'], 'sha256': digest}
            write_json(receipt_path, receipt)
            existing[title] = row
            print(title, 'UPLOADED')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:18080/api')
    parser.add_argument('--package', type=Path, default=PACKAGE)
    parser.add_argument('--receipt', type=Path, default=ROOT / 'quality/evaluation/reports/retail-import.json')
    parser.add_argument('--allow-cloud-ingestion', action='store_true')
    parser.add_argument('--wait-seconds', type=int, default=120)
    args = parser.parse_args()
    client = Client(args.base_url)
    receipt = import_package(client, args.package, args.receipt, args.allow_cloud_ingestion)
    deadline = time.monotonic() + args.wait_seconds
    while True:
        rows = client.request('GET', '/admin/knowledge-documents', principal='admin')['body']
        states = {r['publicId']: r for r in rows}
        wanted = [states[d['documentId']] for d in receipt['documents'].values()]
        if all(r['latestStatus'] == 'READY' and r['activeVersionId'] == r['latestVersionId'] for r in wanted):
            print('All package documents are READY. In demo mode only, this does not establish real Milvus ingestion.')
            return
        if any(r['latestStatus'] == 'FAILED' for r in wanted) or time.monotonic() >= deadline:
            raise SystemExit('Ingestion failed or timed out; inspect versions, do not mark this run successful.')
        time.sleep(2)


if __name__ == '__main__':
    main()
