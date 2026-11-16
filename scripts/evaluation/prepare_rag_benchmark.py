"""Build source-checked, versioned evidence labels without calling a model."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

from eval_v2 import ROOT, read_json

PACKAGE = ROOT / 'data/retail-v1.1'
DESTINATION = ROOT / 'quality/evaluation/datasets/rag-retrieval-v1.json'


def source_sections(package):
    sections = {}
    for doc in read_json(package / 'manifest.json')['documents']:
        text = (package / doc['file']).read_text(encoding='utf-8')
        for block in re.split(r'(?m)(?=^## )', text)[1:]:
            heading, _, body = block.partition('\n')
            section = heading.removeprefix('## ').split('：', 1)[0]
            facts = [p.strip() for p in re.split(r'\n\s*\n', body)
                     if p.strip() and not p.strip().startswith(('来源', '核验日期', '#'))]
            key = Path(doc['file']).name + '#' + section
            sections[key] = {'file': doc['file'], 'section': section, 'facts': facts,
                             'title': heading.split('：', 1)[-1], 'skus': doc['skus']}
    return sections


def build():
    sections = source_sections(PACKAGE)
    original = read_json(ROOT / 'quality/evaluation/datasets/serviceflow-eval-v2.json')
    manifest = read_json(PACKAGE / 'manifest.json')
    by_source = {sid: doc for doc in manifest['documents'] for sid in doc['sourceIds']}
    cases = []
    for old in original['cases']:
        if 'expectedEvidence' not in old:
            continue
        evidence = old['expectedEvidence']
        key = Path(by_source[evidence['sourceId']]['file']).name + '#' + evidence['sectionId']
        step = old['steps'][0]
        sku = step['productId'].removeprefix('{{products.').removesuffix('}}')
        cases.append({'id': old['id'], 'question': step['message'], 'sku': sku,
                      'category': 'single_evidence', 'split': 'known_' + old['split'],
                      'sourceGroups': old['sourceGroups'], 'answerable': True,
                      'goldEvidence': [key], 'requiredFacts': sections[key]['facts'],
                      'referenceAnswer': '\n'.join(sections[key]['facts'])})
    # New questions are source-derived challenges, not independently human-labelled holdout.
    questions = {
        'pura80': ('标称屏幕和存储容量都能完整用上吗？空间不够能不能插卡？',
                   '插线和不插线充电各能到多少瓦？要买什么配套设备，功率会不会变？'),
        'pura80-pro': ('屏幕尺寸按什么算，整块矩形都能显示吗？标称容量能全用、能扩容吗？',
                       '想用上最快的有线和无线充电，分别需要什么配件、最高多少瓦？'),
        'mate70': ('屏幕对角线是不是等于完整可视面积？容量是不是全部可用，还能插存储卡吗？',
                   '有线和无线两种充法的最高功率各是多少，用什么套装才能达到？'),
        'mate70-pro-plus': ('介绍一下屏幕的测量口径，以及机身容量、可用空间和存储卡支持情况。',
                            '同一个型号为什么会有不同系统版本？4.3 的设备能否升级到 5.0，怎么确认？'),
        'nova14': ('屏幕有多大、按什么口径测量？有哪些容量，系统会不会占用空间？',
                   '这款机器的充电头要怎么配才能用上最高功率？Type-C 接口的数据协议是什么？'),
        'nova14-pro': ('屏幕尺寸包括圆角吗？存储有哪些规格，实际空间是否与标称相同？',
                       '要用最高功率充电需要什么套装？充电全程都会保持这个功率吗？'),
        'nova14-ultra': ('请说明屏幕尺寸及实际显示区域，以及存储规格与实际可用空间的区别。',
                         '用什么套装才能达到最高充电功率？Type-C 口传数据采用什么协议？'),
        'mate-x6': ('内外两块屏幕分别多大？标准版、典藏版的内存存储怎么配，能插卡吗？',
                    '两个版本的电池容量一样吗？有线无线快充各能到多少瓦，需要哪些配套条件？')}
    unknown_questions = (
        '屏幕摔裂后换一块原厂屏幕具体要多少钱？没有维修报价就说明没有。',
        '官方给出的平均无故障运行时间是多少小时？资料没给就别估计。',
        '资料能否确认这台设备出厂固件的 SHA-256 校验值？请给出具体值。',
        '更新系统之后，官方保证能额外续航多少分钟？资料没有数值就明确说明。',
        '用该手机拍摄 4K 视频满一小时，官方测试的平均机身温度是多少度？',
        '电池完成 1200 次充放电后，官方保证剩余容量百分比是多少？',
        '官方对 Wi-Fi 连续传输时的平均功耗给出了多少瓦？没有数据不要推算。',
        '折叠铰链的官方逐年故障率和统计样本量是多少？资料未给就明确说明。')
    documents = [d for d in manifest['documents'] if d['id'] in questions]
    for index, doc in enumerate(documents):
        filename = Path(doc['file']).name
        for offset in (0, 2):
            pair = doc['sections'][offset:offset + 2]
            keys = [filename + '#' + s['id'] for s in pair]
            facts = [fact for key in keys for fact in sections[key]['facts']]
            cases.append({'id': 'multi-' + doc['id'] + '-' + str(offset),
                          'question': questions[doc['id']][offset // 2],
                          'sku': doc['skus'][0], 'category': 'multi_evidence',
                          'split': 'authored_challenge', 'sourceGroups': doc['sourceIds'],
                          'answerable': True, 'goldEvidence': keys, 'requiredFacts': facts,
                          'referenceAnswer': '\n'.join(facts)})
        cases.append({'id': 'unknown-' + doc['id'],
                      'question': unknown_questions[index],
                      'sku': doc['skus'][0], 'category': 'unanswerable',
                      'split': 'authored_challenge', 'sourceGroups': doc['sourceIds'],
                      'answerable': False, 'goldEvidence': [], 'requiredFacts': [],
                      'referenceAnswer': '现有资料未提供回答该问题所需的信息。'})
    files = ['manifest.json', 'catalog.json'] + [d['file'] for d in manifest['documents']]
    return {'version': 'rag-retrieval-v1', 'package': PACKAGE.relative_to(ROOT).as_posix(),
            'origin': 'project-authored; AI-assisted and source-checked; no independent human annotation',
            'scope': 'fixed-product knowledge QA; reused development/holdout are now known regression',
            'labelUnit': 'source-file + section, deduplicating SKU copies; not chunk-level relevance',
            'answerMetric': 'exact source-paragraph fact coverage and selected-evidence relevance; not general semantic accuracy',
            'sourceHashes': {name: hashlib.sha256((PACKAGE / name).read_bytes()).hexdigest() for name in files},
            'cases': cases}


def validate(suite):
    package = ROOT / suite['package']
    sections = source_sections(package)
    assert all(hashlib.sha256((package / name).read_bytes()).hexdigest() == digest
               for name, digest in suite['sourceHashes'].items()), 'Corpus changed since labels were built'
    ids = set()
    for case in suite['cases']:
        assert case['id'] not in ids, 'Duplicate case'
        ids.add(case['id'])
        assert case['question'].strip() and len(case['question']) <= 4000
        assert len(case['goldEvidence']) == len(set(case['goldEvidence']))
        assert bool(case['goldEvidence']) == case['answerable']
        assert all(key in sections and case['sku'] in sections[key]['skus'] for key in case['goldEvidence'])
        assert case['requiredFacts'] == [f for key in case['goldEvidence'] for f in sections[key]['facts']]
    return {'cases': len(ids), 'categories': dict(Counter(c['category'] for c in suite['cases'])),
            'splits': dict(Counter(c['split'] for c in suite['cases'])), 'evidenceSections': len(sections)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Create labels before any evaluation; does not overwrite')
    args = parser.parse_args()
    if args.write:
        if DESTINATION.exists():
            raise SystemExit('Dataset already exists; review version changes explicitly')
        suite = build()
        validate(suite)
        DESTINATION.write_text(json.dumps(suite, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(validate(read_json(DESTINATION)), ensure_ascii=False))
