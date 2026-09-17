"""Fetch preassigned domestic sources; preserve original snapshots unchanged."""
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
from pathlib import Path
import shutil
import serviceflow_training.data.http_sources as fetcher
from serviceflow_training.core.contracts import ROOT, read_json, sha, require, write_json

PLAN=ROOT/'training/data/grader-specialist-source-plan.json'
OUT=ROOT/'local-datasets/serviceflow/raw/grader-specialist-exp-01'

def main():
    plan=read_json(PLAN);groups=plan['groups'];entries=[]
    old=read_json(ROOT/'training/data/business-source-plan-v1.json')['entries']
    forbidden={x[0] for x in old if x[2]!='train'}
    forbidden.update(x[0] for x in read_json(ROOT/'training/data/v1-independent-eval-source-plan.json')['entries'])
    seen=set()
    for g in groups:
        for page in g['pages']:
            require(page not in forbidden and page not in seen,'Source overlap: '+page)
            seen.add(page);entries.append((page,g['id'],g['split']))
    require(Counter(g['split'] for g in groups)=={'train':30,'validation':10,'test':15},'Source group count')
    fetcher.PLAN=PLAN;fetcher.OUT=OUT
    # Existing source text/html are copied byte-for-byte; old metadata is not rewritten.
    def fetch(entry):
        page,group,split=entry;old_path=ROOT/'local-datasets/serviceflow/raw/huawei-support-v1'/page
        if old_path.is_dir() and not (OUT/page).exists():
            m=read_json(old_path/'manifest.json')
            require(m['split']=='train' and split=='train','Old heldout reuse')
            require(sha(old_path/'source.txt')==m['text_sha256'],'Old source changed')
            (OUT/page).mkdir(parents=True)
            for name in ['source.txt','source.html']:shutil.copyfile(old_path/name,OUT/page/name)
            m.update(family=group,split=split,plan_sha256=sha(PLAN),copied_from=str(old_path.relative_to(ROOT)))
            write_json(OUT/page/'manifest.json',m)
        return fetcher.fetch(entry)
    with ThreadPoolExecutor(max_workers=3) as pool:records=list(pool.map(fetch,entries))
    write_json(OUT/'manifest.json',{'plan_sha256':sha(PLAN),'sources':records,'groups':groups})
    print('VERIFIED',len(records),'pages',len(groups),'groups')

if __name__=='__main__':main()
