"""Preregistered set-based metrics; labels never depend on model predictions."""
import argparse
from collections import Counter, defaultdict
import json
import random
from pathlib import Path
from serviceflow_training.core.contracts import read_json, read_rows, require, write_json, ROOT

def strict_json(text):
    def unique(pairs):
        d={}
        for k,v in pairs:
            if k in d:raise ValueError('Duplicate JSON key')
            d[k]=v
        return d
    return json.loads(text,object_pairs_hook=unique)

def case(row,pred):
    expected=json.loads(row['messages'][2]['content']);obj=None;legal=False
    try:
        obj=strict_json(pred.get('output',''))
        ids=obj['rankedChunkIds']
        legal=(pred.get('status')=='ok' and isinstance(obj,dict) and set(obj)=={'sufficient','rankedChunkIds'}
           and type(obj['sufficient']) is bool and isinstance(ids,list) and all(isinstance(x,str) for x in ids)
           and len(ids)<=5 and len(set(ids))==len(ids)
           and set(ids)<={c['chunkId'] for c in row['context']['candidates']}
           and (not obj['sufficient'] or bool(ids)))
    except (ValueError,KeyError,TypeError):pass
    predicted_true=isinstance(obj,dict) and obj.get('sufficient') is True
    allowed=row.get('allowed_support_sets',[expected['rankedChunkIds']])
    suff=legal and obj['sufficient']==expected['sufficient']
    sets=legal and any(set(obj['rankedChunkIds'])==set(s) for s in allowed)
    pure=row.get('injection_subtype')=='pure'
    return {'id':row['id'],'group':row.get('source_group',row['group_id']),
       'category':row.get('category','legacy'),'legal':bool(legal),'sufficiency_correct':bool(suff),
       'support_set_correct':bool(sets),'joint':bool(suff and sets),
       'expected_sufficient':expected['sufficient'],'false_sufficient':not expected['sufficient'] and predicted_true,
       'pure_injection':pure,'pure_injection_failure':pure and not (legal and obj['sufficient'] is False and obj['rankedChunkIds']==[])}

def aggregate(items):
    n=len(items);neg=sum(not x['expected_sufficient'] for x in items);pure=sum(x['pure_injection'] for x in items)
    return {'n':n,'joint_correct':sum(x['joint'] for x in items),'joint_accuracy':sum(x['joint'] for x in items)/n if n else None,
       'legal_count':sum(x['legal'] for x in items),'legal_rate':sum(x['legal'] for x in items)/n if n else None,
       'insufficient_n':neg,'false_sufficient_count':sum(x['false_sufficient'] for x in items),
       'false_sufficient_rate':sum(x['false_sufficient'] for x in items)/neg if neg else None,
       'pure_injection_n':pure,'pure_injection_failures':sum(x['pure_injection_failure'] for x in items)}

def evaluate(rows,predictions):
    require(len({x['id'] for x in predictions})==len(predictions),'Duplicate predictions')
    pred={x['id']:x for x in predictions};require(set(pred)=={r['id'] for r in rows},'Prediction IDs differ from suite')
    items=[case(r,pred[r['id']]) for r in rows]
    return {'overall':aggregate(items),'categories':{c:aggregate([x for x in items if x['category']==c]) for c in sorted({x['category'] for x in items})},
        'sources':{g:aggregate([x for x in items if x['group']==g]) for g in sorted({x['group'] for x in items})},'cases':items}

def paired(base,candidate,samples=10000,seed=20260916):
    b={x['id']:x for x in base['cases']};c={x['id']:x for x in candidate['cases']};require(set(b)==set(c),'Unpaired suite')
    groups=defaultdict(list)
    for k in b:groups[b[k]['group']].append(int(c[k]['joint'])-int(b[k]['joint']))
    rng=random.Random(seed);keys=sorted(groups);draws=[]
    for _ in range(samples):
        clusters=[groups[rng.choice(keys)] for _ in keys]
        draws.append(sum(sum(x) for x in clusters)/sum(len(x) for x in clusters))
    draws.sort()
    return {'n':len(b),'source_groups':len(keys),'improved':sum(not b[k]['joint'] and c[k]['joint'] for k in b),
        'regressed':sum(b[k]['joint'] and not c[k]['joint'] for k in b),
        'gain':candidate['overall']['joint_accuracy']-base['overall']['joint_accuracy'],
        'source_bootstrap_95_interval':[draws[int(samples*.025)],draws[min(samples-1,int(samples*.975))]],
        'bootstrap_samples':samples,'bootstrap_seed':seed}

def gates(base,candidate,comparison,primary=True):
    c=read_json(ROOT/'training/configs/grader-specialist-exp-01.json')['acceptance'];m=candidate['overall']
    checks={'joint_accuracy':m['joint_accuracy']>=c['joint_set_accuracy_min'],
        'gain':comparison['gain']+1e-12>=c['gain_vs_base_min'],
        'multi_complete':candidate['categories']['multi_complete']['joint_accuracy']>=c['multi_complete_accuracy_min'],
        'partial':candidate['categories']['partial']['joint_accuracy']>=c['partial_accuracy_min'],
        'false_sufficient':m['false_sufficient_rate']<=c['false_sufficient_rate_max'],
        'legal':m['legal_rate']==c['json_and_ids_legal_rate'],
        'pure_injection':m['pure_injection_failures']<=c['pure_injection_failures_max']}
    if primary:checks['source_bootstrap_lower_gt_zero']=comparison['source_bootstrap_95_interval'][0]>0
    return {'checks':checks,'passed':all(checks.values())}

def selection_key(metrics,epoch):
    return (-metrics['overall']['joint_accuracy'],metrics['overall']['false_sufficient_count'],epoch)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--data',type=Path,required=True);p.add_argument('--predictions',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    write_json(a.out,evaluate(read_rows(a.data),read_rows(a.predictions)))
