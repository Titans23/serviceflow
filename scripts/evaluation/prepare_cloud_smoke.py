"""Prepare public-only smoke inputs; does not call providers or modify a database."""
import copy
import hashlib
import shutil

from eval_v2 import PACKAGE, ROOT, SUITE, read_json, validate_package, validate_suite, write_json

CASE_IDS = [
    'knowledge-pura80-screen', 'knowledge-pura80-wired', 'knowledge-pura80-water',
    'knowledge-nova14-screen', 'knowledge-nova14-wired', 'knowledge-nova14-battery',
    'policy-v2-1', 'policy-v2-2', 'context-huawei-pura80-12-256',
]
SKUS = {'HUAWEI-PURA80-12-256', 'HUAWEI-NOVA14-256'}
OUTPUT = ROOT / 'quality/evaluation/reports/cloud-smoke-inputs'


def prepare():
    suite = read_json(SUITE)
    by_id = {c['id']: c for c in suite['cases']}
    suite['cases'] = [by_id[key] for key in CASE_IDS]
    assert all(c['split'] == 'development' and not c.get('fixture') for c in suite['cases'])
    for case in suite['cases']:
        for step in case['steps']:
            step['expect'].setdefault('json', {})['events.meta.degraded'] = False
            if case['id'].startswith('policy-'):
                step['expect']['citations'] = ['huawei-phone-warranty-v1.md']
    suite['name'] = 'cloud-smoke-v1'
    suite['sourceSuiteSha256'] = hashlib.sha256(SUITE.read_bytes()).hexdigest()
    report = validate_suite(suite)
    assert report['chatTurns'] == 10 and report['cases'] == 9
    manifest = copy.deepcopy(read_json(PACKAGE / 'manifest.json'))
    selected = []
    for document in manifest['documents']:
        document['skus'] = [s for s in document['skus'] if s in SKUS]
        if document['skus'] or document['documentType'] == 'POLICY':
            selected.append(document)
            target = OUTPUT / 'package' / document['file']
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(PACKAGE / document['file'], target)
    manifest['documents'] = selected
    source_ids = {sid for d in selected for sid in d['sourceIds']}
    manifest['sources'] = [s for s in manifest['sources'] if s['id'] in source_ids]
    manifest['version'] = 'retail-v1-cloud-smoke'
    write_json(OUTPUT / 'package/manifest.json', manifest)
    write_json(OUTPUT / 'package/catalog.json', {
        'products': [p for p in read_json(PACKAGE / 'catalog.json')['products'] if p['sku'] in SKUS]})
    write_json(OUTPUT / 'suite.json', suite)
    followup = copy.deepcopy(suite)
    followup['name'] = 'cloud-smoke-price-followup'
    followup['cases'] = [c for c in followup['cases'] if c['id'] == 'context-huawei-pura80-12-256']
    for step in followup['cases'][0]['steps']:
        step['expect']['textNone'] = ['listPrice', 'null', 'JSON', '结构化商品事实']
    write_json(OUTPUT / 'price-followup.json', followup)
    print({'suite': report, 'package': validate_package(OUTPUT / 'package'), 'output': str(OUTPUT)})


if __name__ == '__main__':
    prepare()
