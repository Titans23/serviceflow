"""Snapshot explicitly selected domestic official HTML pages; no discovery crawler."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from urllib.parse import urlparse
import requests
from bs4 import BeautifulSoup
from serviceflow_training.core.contracts import ROOT, read_json, sha, write_json

OUT=ROOT/'local-datasets/serviceflow/raw/huawei-support-v1'
PLAN=ROOT/'training/data/business-source-plan-v1.json'

def fetch(entry):
    id_,family,split=entry
    url=f'https://consumer.huawei.com/cn/support/content/{id_}/'
    dest=OUT/id_
    if (dest/'manifest.json').exists():
        old=read_json(dest/'manifest.json')
        assert old['url']==url and sha(dest/'source.html')==old['html_sha256']
        assert sha(dest/'source.txt')==old['text_sha256']
        return old
    r=requests.get(url,timeout=(15,60))
    r.raise_for_status()
    assert urlparse(r.url).hostname=='consumer.huawei.com'
    soup=BeautifulSoup(r.content,'html.parser')
    h=soup.find(id='knowledgeTitle')
    assert h and h.get_text(strip=True)
    body=soup.select_one('.knowledge-detail')
    assert body
    for unwanted in body(['script','style']): unwanted.decompose()
    content=body.get_text('\n',strip=True)
    assert len(content)>150
    dest.mkdir(parents=True,exist_ok=False)
    (dest/'source.html').write_bytes(r.content)
    (dest/'source.txt').write_text(content,encoding='utf-8')
    meta={'id':id_,'url':url,'title':h.get_text(strip=True),'family':family,'split':split,
          'downloaded_at':datetime.now(timezone.utc).isoformat(),'html_sha256':sha(dest/'source.html'),
          'text_sha256':sha(dest/'source.txt'),'http_status':r.status_code,
          'license':'Copyright Huawei; no open dataset license asserted. Local reference snapshot only; training uses authored factual summaries, not copied articles.',
          'plan_sha256':sha(PLAN)}
    write_json(dest/'manifest.json',meta)
    print(id_,split,len(content),flush=True)
    return meta
