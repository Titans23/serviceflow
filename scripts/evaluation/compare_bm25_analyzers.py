"""Read-only, local BM25 comparison on identical source text; no provider calls."""
import argparse
import json
from pathlib import Path

from eval_v2 import ROOT, read_json
from rag_benchmark import docker
from rag_metrics import evidence_key, retrieval_metrics
from prepare_rag_benchmark_v2 import DESTINATION, validate


def milvus(container, path, body):
    response = json.loads(docker('exec', '-i', container, 'curl', '-fsS',
        'http://localhost:19530/v2/vectordb/' + path, '-H', 'Content-Type: application/json',
        '--data-binary', '@-', body=json.dumps(body)))
    if response.get('code') != 0:
        raise ValueError('Local Milvus comparison failed')
    return response['data']


def compare(args):
    suite=read_json(DESTINATION); validate(suite)
    if args.output.exists(): raise ValueError('Use a new output path; preserve earlier results')
    manifest=read_json(ROOT/suite['package']/'manifest.json')
    receipts=[read_json(args.old_receipt),read_json(args.new_receipt)]
    containers=[args.old_milvus,args.new_milvus]
    versions={}; fingerprints={}
    for sku in {c['sku'] for c in suite['cases']}:
        doc=next(d for d in manifest['documents'] if sku in d['skus'])
        title=doc['title']+'/'+sku
        snapshots=[]
        for i,container in enumerate(containers):
            version=receipts[i]['documents'][title]['versionId']
            chunks=milvus(container,'entities/query',{'collectionName':'serviceflow_chunks',
                'filter':f'documentVersionId == {int(version)}','outputFields':['source','content','productId'],
                'limit':100,'consistencyLevel':'Strong'})
            assert len(chunks)==len(doc['sections']) and len({c['productId'] for c in chunks})==1
            snapshots.append({evidence_key(c):c['content'] for c in chunks})
            versions[sku,i]=(version,chunks[0]['productId'])
        assert snapshots[0]==snapshots[1], 'The two analyzers must see identical text'
        fingerprints[sku]=len(snapshots[0])
    schemas=[milvus(container,'collections/describe',{'collectionName':'serviceflow_chunks'})
             for container in containers]
    analyzers=[]
    for schema in schemas:
        field=next(f for f in schema['fields'] if f['name']=='content')
        params={p['key']:p['value'] for p in field['params']}
        analyzers.append(json.loads(params.get('analyzer_params','{"type":"standard"}')))
    assert analyzers==[{'type':'standard'},{'type':'chinese'}]
    rows=[]
    for case in suite['cases']:
        row={'caseId':case['id'],'split':case['split'],'answerable':case['answerable']}
        for i,name in enumerate(['standard','chinese']):
            version,product=versions[case['sku'],i]
            found=milvus(containers[i],'entities/search',{'collectionName':'serviceflow_chunks',
                'annsField':'sparseVector','data':[case['question']],'limit':5,'consistencyLevel':'Strong',
                'filter':f'documentType == "PRODUCT_MANUAL" and documentVersionId in [{int(version)}] and productId in [{int(product)}]',
                'outputFields':['source','content']})
            ranked=[evidence_key(c) for c in found]
            row[name]={'rankedEvidence':ranked,'metrics':retrieval_metrics(case['goldEvidence'],ranked)}
        rows.append(row)
    summaries={}
    for split in ['all','revised_regression','frozen_question_holdout']:
        selected=[r for r in rows if r['answerable'] and (split=='all' or r['split']==split)]
        summaries[split]={name:{key:sum(r[name]['metrics'][key] for r in selected)/len(selected)
            for key in ['recallAt5','hitAt5','mrrAt5']} for name in ['standard','chinese']}
        summaries[split]['answerableQuestions']=len(selected)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps({'providerRequests':0,'analyzers':analyzers,'sourceSectionsPerSku':fingerprints,
        'summary':summaries,'cases':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(summaries,ensure_ascii=False))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--old-milvus',default='serviceflow-rag-bench-milvus-1')
    p.add_argument('--new-milvus',default='serviceflow-rag-v2-milvus-1')
    p.add_argument('--old-receipt',type=Path,default=ROOT/'quality/evaluation/reports/rag-benchmark-import.json')
    p.add_argument('--new-receipt',type=Path,default=ROOT/'quality/evaluation/reports/rag-v2-import.json')
    p.add_argument('--output',type=Path,required=True)
    compare(p.parse_args())
