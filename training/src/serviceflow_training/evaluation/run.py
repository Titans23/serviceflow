"""Fixed local vLLM module evaluation for a verified base or merged candidate."""
import argparse
from collections import Counter
from datetime import datetime, timezone
from importlib.metadata import version
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
from urllib.request import urlopen
from urllib.error import URLError, HTTPError
from serviceflow_training.core.contracts import ROOT, PROTECTED, read_rows, read_json, validate, write_json, prompt_contract, require
from serviceflow_training.evaluation.client import request_case, percentile
from serviceflow_training.evaluation.tasks import score
from serviceflow_training.core.hashing import sha

def main():
    p=argparse.ArgumentParser();p.add_argument('--data',type=Path,required=True);p.add_argument('--model',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--splits',nargs='+',choices=['validation','test'],default=['validation','test']);a=p.parse_args()
    require(not a.out.exists(),'Use a new evaluation directory')
    all_rows=read_rows(a.data);validate(all_rows)
    rows=[r for r in all_rows if r['split'] in a.splits]
    require(rows,'No evaluation rows')
    require(version('vllm')=='0.8.5','Pinned vLLM required')
    model=a.model.resolve()
    if (model/'merge-manifest.json').exists():
        merge=read_json(model/'merge-manifest.json');require(merge['status']=='COMPLETED','Unverified merge')
        hashes=merge['files_sha256']
    else:
        lock=read_json(ROOT/'runtime-data/training/environment.lock.json')
        require(model==Path(lock['base_model']['local_path']).resolve(),'Unknown model')
        hashes=read_json(lock['base_model']['files_sha256_manifest'])
    a.out.mkdir(parents=True)
    meta={'status':'VERIFYING','started_at':datetime.now(timezone.utc).isoformat(),'model':str(model),'data_sha256':sha(a.data),
          'splits':a.splits,'protected_eval_sha256':sha(PROTECTED),'runner_sha256':sha(Path(__file__)),
          'hash_helper_sha256':sha(ROOT/'training/src/serviceflow_training/core/hashing.py'),
          'contract':prompt_contract(),'scope':'LOCAL_AI_REVIEWED_MODULES_NOT_PRODUCTION_ACCEPTANCE'}
    write_json(a.out/'run-manifest.json',meta)
    for name,expected in hashes.items():
        path=(model/name).resolve();require(path.is_relative_to(model) and sha(path)==expected,'Model changed: '+name)
    from transformers import AutoTokenizer
    tokenizer=AutoTokenizer.from_pretrained(model,local_files_only=True)
    template=ROOT/'runtime-data/training/qwen3-nonthinking.jinja'
    budgets={'intent':64,'grader':256,'rewrite':160,'product_answer':384,'chat':192}
    lengths=[]
    for r in rows:
        tokens=tokenizer.apply_chat_template(r['messages'][:2],tokenize=True,add_generation_prompt=True,enable_thinking=False)
        require(tokens==tokenizer.apply_chat_template(r['messages'][:2],tokenize=True,add_generation_prompt=True,chat_template=template.read_text()),'Template mismatch')
        require(len(tokens)+budgets[r['task_type']]<=4096,'Generation would truncate input')
        lengths.append({'id':r['id'],'input_tokens':len(tokens)})
    meta.update(status='STARTING',model_files_sha256=hashes,lengths=lengths,template_sha256=sha(template),
                parameters={'temperature':.1,'top_p':1,'top_k':-1,'seed':42,'dtype':'bfloat16','max_model_len':4096,'max_num_seqs':2,'gpu_memory_utilization':.65,'enforce_eager':True,'budgets':budgets,'concurrency':1})
    write_json(a.out/'run-manifest.json',meta)
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    url=f'http://127.0.0.1:{port}';key=secrets.token_urlsafe(32)
    env=dict(os.environ,VLLM_API_KEY=key,HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',VLLM_NO_USAGE_STATS='1')
    cmd=[sys.executable,'-m','vllm.entrypoints.openai.api_server','--model',str(model),'--served-model-name','serviceflow-qwen3-8b-base',
         '--host','127.0.0.1','--port',str(port),'--dtype','bfloat16','--max-model-len','4096','--max-num-seqs','2',
         '--gpu-memory-utilization','0.65','--enforce-eager','--generation-config','vllm','--chat-template',str(template),'--disable-log-requests']
    results=[]
    with (a.out/'server.log').open('w') as log:
        proc=subprocess.Popen(cmd,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            deadline=time.monotonic()+900
            while time.monotonic()<deadline:
                require(proc.poll() is None,'Inference process exited')
                try:
                    with urlopen(url+'/health',timeout=2) as response:
                        if response.status==200:break
                except (URLError,TimeoutError):time.sleep(2)
            else:raise TimeoutError('Server startup timeout')
            try:urlopen(url+'/v1/models',timeout=5);raise ValueError('Missing API authentication')
            except HTTPError as e:require(e.code==401,'Unexpected authentication status')
            warm={'id':'warmup','task_type':'intent','messages':[{'role':'system','content':prompt_contract()['prompts']['intent']},{'role':'user','content':'想了解客服能做什么。'}]}
            request_case(url,key,warm,64)
            meta['status']='EVALUATING';write_json(a.out/'run-manifest.json',meta)
            with (a.out/'predictions.jsonl').open('w',encoding='utf-8') as stream:
                for i,r in enumerate(rows):
                    result=request_case(url,key,r,budgets[r['task_type']]);result['split']=r['split']
                    stream.write(json.dumps(result,ensure_ascii=False)+'\n');stream.flush();results.append(result)
                    print(f'{i+1}/{len(rows)} {r["split"]} {r["task_type"]} {result["status"]}',flush=True)
            require(sha(a.data)==meta['data_sha256'] and prompt_contract()==meta['contract'],'Data or contract changed during evaluation')
            for split in a.splits:
                gold=[r for r in rows if r['split']==split];pred=[r for r in results if r['split']==split]
                if not gold:continue
                metrics=score(gold,pred)
                metrics['timing']={'count':len(pred),'status':dict(Counter(r['status'] for r in pred)),
                    'request_p95_seconds':percentile([r['elapsed_seconds'] for r in pred],.95),
                    'ttft_p95_seconds':percentile([r['first_token_seconds'] for r in pred if r['first_token_seconds'] is not None],.95)}
                write_json(a.out/(split+'-metrics.json'),metrics)
            meta.update(status='COMPLETED',count=len(results),successful=sum(r['status']=='ok' for r in results),predictions_sha256=sha(a.out/'predictions.jsonl'))
        except BaseException as e:
            meta.update(status='FAILED',error=type(e).__name__+': '+str(e));raise
        finally:
            try:os.killpg(proc.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            try:proc.wait(timeout=20)
            except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=10)
            meta.update(server_stopped=True,finished_at=datetime.now(timezone.utc).isoformat())
            write_json(a.out/'run-manifest.json',meta)
    print(json.dumps({k:meta.get(k) for k in ['status','count','successful','server_stopped']}))

if __name__=='__main__':main()
