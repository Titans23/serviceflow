"""Gate-controlled actual Spring AI/CloudAiGateway loopback acceptance.

Uses the preregistered primary seed, never a seed picked by test results.
Stops its own temporary model service; never restores a retired chatbot.
"""
from datetime import datetime,timezone
import argparse
import json
import os
from pathlib import Path
import secrets
import re
import shutil
import signal
import socket
import subprocess
import time
from urllib.request import urlopen
from urllib.error import URLError,HTTPError
import xml.etree.ElementTree as ET
from serviceflow_training.core.contracts import ROOT,read_json,write_json,require,prompt_contract
from serviceflow_training.core.hashing import sha
from serviceflow_training.evaluation.client import request_case

TRAIN='/home/titans/venvs/serviceflow-train-py312/bin/python'
INFER='/home/titans/venvs/serviceflow-infer-py312/bin/python'
OUT=ROOT/'runtime-data/training/grader-specialist-exp-01'
MAVEN=ROOT/'training/src/serviceflow_training/environment/maven.py'

def fresh_test_counts(xml,started_ns):
    require(xml.exists() and xml.stat().st_mtime_ns>=started_ns,'No fresh Java test report from this attempt')
    suite=ET.parse(xml).getroot()
    return {k:int(suite.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--attempt-name',default='java-acceptance');args=parser.parse_args()
    require(re.fullmatch(r'java-acceptance(?:-[a-z0-9-]+)?',args.attempt_name),'Invalid acceptance attempt name')
    comparison=read_json(OUT/'comparison.json');state=read_json(OUT/'execution.json')
    require(comparison['both_seeds_pass'],'Candidate Java acceptance is blocked by the preregistered quality gate')
    require(state['status']=='OFFLINE_EXPERIMENT_COMPLETED','Wait for completed offline evaluation')
    selection=read_json(OUT/'selection.json');require(sha(OUT/'selection.json')==comparison['selection_sha256'],'Selection changed')
    primary=str(selection['selection_policy']['primary_seed']);model=Path(selection['seeds'][primary]['model'])
    record=read_json(model/'merge-manifest.json');require(record['status']=='COMPLETED','Unverified model')
    dest=OUT/args.attempt_name;require(not dest.exists(),'Preserve prior acceptance attempt; do not overwrite')
    dest.mkdir()
    manifest={'status':'VERIFYING','primary_seed':int(primary),'model':str(model),'epoch':selection['seeds'][primary]['epoch'],
       'comparison_sha256':sha(OUT/'comparison.json'),'selection_sha256':sha(OUT/'selection.json'),
       'scope':'Actual CloudAiGateway + Spring AI; synthetic loopback fixtures; no database or public API',
       'started_at':datetime.now(timezone.utc).isoformat(),'runner_sha256':sha(Path(__file__)),
       'prompt_contract':prompt_contract(),'temperature':.1,'production_switched':False,'maven_offline':True,
       'java_tests_executed':False,'test_source_sha256':sha(ROOT/'apps/serviceflow-server/src/test/java/com/serviceflow/ai/LocalGraderAcceptanceTest.java')}
    write_json(dest/'manifest.json',manifest);proc=None
    try:
        for name,expected in record['files_sha256'].items():
            require((model/name).resolve().parent==model.resolve() and sha(model/name)==expected,'Model changed')
        with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
        url=f'http://127.0.0.1:{port}';key=secrets.token_urlsafe(32);alias='serviceflow-qwen3-8b-base'
        env=dict(os.environ,VLLM_API_KEY=key,HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',VLLM_NO_USAGE_STATS='1')
        cmd=[INFER,'-m','vllm.entrypoints.openai.api_server','--model',str(model),'--served-model-name',alias,
             '--host','127.0.0.1','--port',str(port),'--dtype','bfloat16','--max-model-len','4096','--max-num-seqs','2',
             '--gpu-memory-utilization','0.65','--enforce-eager','--generation-config','vllm',
             '--chat-template',str(ROOT/'runtime-data/training/qwen3-nonthinking.jinja'),'--disable-log-requests']
        with (dest/'server.log').open('w') as log:proc=subprocess.Popen(cmd,env=env,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        manifest['status']='STARTING';write_json(dest/'manifest.json',manifest)
        until=time.monotonic()+900
        while time.monotonic()<until:
            require(proc.poll() is None,'Temporary inference exited')
            try:
                with urlopen(url+'/health',timeout=2) as r:
                    if r.status==200:break
            except (URLError,TimeoutError):time.sleep(2)
        else:raise TimeoutError('Temporary inference startup')
        try:urlopen(url+'/v1/models',timeout=3);raise ValueError('API authentication missing')
        except HTTPError as e:require(e.code==401,'Unexpected auth status')
        warm={'id':'warmup','task_type':'grader','messages':[{'role':'system','content':prompt_contract()['prompts']['grader']},
             {'role':'user','content':json.dumps({'query':'此资料有价格吗？','candidates':[]},ensure_ascii=False)}]}
        require(request_case(url,key,warm,256)['status']=='ok','Warmup failed')
        testenv=dict(os.environ,SERVICEFLOW_GRADER_ACCEPTANCE_URL=url,SERVICEFLOW_GRADER_ACCEPTANCE_KEY=key,SERVICEFLOW_GRADER_ACCEPTANCE_MODEL=alias)
        manifest['status']='JAVA_TESTS';write_json(dest/'manifest.json',manifest)
        maven_started_ns=time.time_ns()
        with (dest/'maven.log').open('w') as log:
            result=subprocess.run([TRAIN,'-B',str(MAVEN),'-o','-Dspotless.skip=true','-Dtest=LocalGraderAcceptanceTest','test'],
                                  env=testenv,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        manifest['maven_exit_code']=result.returncode
        xml=ROOT/'apps/serviceflow-server/target/surefire-reports/TEST-com.serviceflow.ai.LocalGraderAcceptanceTest.xml'
        counts=fresh_test_counts(xml,maven_started_ns);shutil.copyfile(xml,dest/xml.name)
        manifest['java_tests_executed']=counts['tests']>counts['skipped']
        manifest.update(maven_exit_code=result.returncode,tests=counts,test_report_sha256=sha(dest/xml.name),
            test_source_sha256=sha(ROOT/'apps/serviceflow-server/src/test/java/com/serviceflow/ai/LocalGraderAcceptanceTest.java'))
        require(result.returncode==0 and counts=={'tests':5,'failures':0,'errors':0,'skipped':0},'Java model acceptance did not pass')
        manifest['status']='PASSED'
    except BaseException as error:
        manifest.update(status='FAILED',error=type(error).__name__+': '+str(error));raise
    finally:
        if proc:
            try:os.killpg(proc.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            try:proc.wait(timeout=20)
            except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=10)
        manifest['temporary_service_stopped']=True
        manifest['finished_at']=datetime.now(timezone.utc).isoformat()
        write_json(dest/'manifest.json',manifest)
    print('Java acceptance',manifest['status'],manifest.get('tests'),flush=True)

if __name__=='__main__':main()
