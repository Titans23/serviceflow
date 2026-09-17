"""Produce row-level audit ledger and reject unsafe label/source inconsistencies."""
from collections import Counter
import json
from serviceflow_training.data.build import OUT, PLAN, RAW, EXP, facts, audit
from serviceflow_training.core.contracts import ROOT, read_rows, read_json, sha, require, write_json

def main():
    rows=read_rows(OUT/'draft.jsonl');audit(rows,False);bank={g['id']:g for g in facts()}
    ledger=[];summary=[]
    for r in rows:
        group=bank[r['source_group']];target=json.loads(r['messages'][2]['content'])
        by_id={c['chunkId']:c for c in r['context']['candidates']};proof=[]
        for a in r['atomic_review']:
            atom=next(x for x in group['atoms'] if x['atom_id']==a['atom_id'])
            for cid in a['support_ids']:
                require(atom['fact'] in by_id[cid]['content'],'Supporting text missing')
                require(atom['scope'] in by_id[cid]['content'],'Scope dropped')
            proof.append({'question':a['question'],'support_ids':a['support_ids'],'missing':a['missing'],
                          'source':atom['source_uri'],'source_sha256':atom['source_sha256'],'anchor':atom['anchor'],
                          'rationale':a['basis']})
        for c in r['context']['candidates']:
            p=r['candidate_provenance'][c['chunkId']]
            selected=c['chunkId'] in target['rankedChunkIds']
            require(selected==(p['kind'] in {'official_paraphrase','fact_with_injection'}),'Candidate label mismatch')
        ledger.append({'id':r['id'],'split':r['split'],'category':r['category'],'query':r['context']['question'],
                       'item_review':proof,'allowed_support_sets':r['allowed_support_sets'],
                       'sufficient':target['sufficient'],'review':'Codex AI, source/fact author review plus deterministic row audit; no independent human review'})
    # Preserve all prior registries and source plans, record their hashes.
    protected=[ROOT/'training/data/business-source-plan-v1.json',ROOT/'training/data/v1-independent-eval-source-plan.json',
               ROOT/'training/data/heldout-registry.json',ROOT/'training/data/evaluation-only-registry.json']
    prior=[p for p in protected if p.is_file()]
    old=read_json(prior[0])['entries'];forbidden={x[0] for x in old if x[2]!='train'}
    forbidden.update(x[0] for x in read_json(prior[1])['entries'])
    require(not forbidden & {p for g in bank.values() for p in g['pages']},'Old heldout source leak')
    output=OUT/'row-review.jsonl'
    output.write_text(''.join(json.dumps(x,ensure_ascii=False)+'\n' for x in ledger),encoding='utf-8')
    for split in ['train','validation','test']:
        rr=[r for r in rows if r['split']==split]
        summary.append({'split':split,'n':len(rr),'sources':len({r['source_group'] for r in rr}),
            'categories':dict(Counter(r['category'] for r in rr)),
            'selected_count':dict(Counter(len(json.loads(r['messages'][2]['content'])['rankedChunkIds']) for r in rr))})
    receipt={'draft_sha256':sha(OUT/'draft.jsonl'),'row_review_sha256':sha(output),'row_count':len(ledger),
       'review_kind':'AI_WITH_PROGRAMMATIC_ROW_CHECKS_NO_INDEPENDENT_HUMAN_REVIEW',
       'source_isolation':'Feature/document lineage grouped before authoring; older train groups may be reused only in train',
       'prior_registry_hashes':{str(p.relative_to(ROOT)):sha(p) for p in prior},'summary':summary,
       'limitations':['Authored FAQ fixtures, not raw production retrieval distribution',
                     'Validation consists of unseen PC-support feature groups; test consists of other unseen feature groups',
                     'Scope-mismatch fixtures use explicitly simulated release-channel metadata, not actual vendor support assertions',
                     'All six task structures repeat across splits; source/fact lineage and injection phrasing are disjoint',
                     'Same AI authored and reviewed facts/labels; independent human review absent']}
    write_json(OUT/'review-receipt.json',receipt)
    print(json.dumps(receipt,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
