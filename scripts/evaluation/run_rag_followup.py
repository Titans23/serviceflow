"""Run only the approved fixed RAG follow-up, serially, with the existing provider guard."""
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import uuid

from eval_v2 import Client, ROOT, read_json, run_case, summarize, validate_package, validate_suite, write_json
from cloud_business_recovery import budget_count

SUITE = ROOT / 'quality/evaluation/datasets/rag-context-followup-v1.json'
PLAN = ROOT / 'quality/evaluation/results/rag-context-followup-plan-2026-10-25.json'
PACKAGE = ROOT / 'data/retail-holdout-v2'


class ExternalStop(RuntimeError):
    pass


class GuardedClient(Client):
    def __init__(self, docker, output):
        super().__init__('http://127.0.0.1:18080/api')
        self.docker, self.output, self.stopped = docker, output, None
        self.case = None
        self.records = []
        self.last_count, self.counter_complete = None, False
        self.identity = self.inspect()

    def command(self, *args):
        return subprocess.check_output([self.docker, *args], text=True, encoding='utf-8', stderr=subprocess.PIPE)

    def inspect(self):
        return json.loads(self.command('inspect', 'serviceflow-cloud-eval-server-1'))[0]

    def snapshot(self):
        self.counter_complete = False
        try:
            current = self.inspect()
            if (current['Id'], current['State']['StartedAt'], current['RestartCount']) != (
                    self.identity['Id'], self.identity['State']['StartedAt'], 0):
                raise ExternalStop('process_identity_changed')
            lines = [line for line in self.command('logs', current['Id']).splitlines() if 'Evaluation provider' in line]
            (self.output / 'provider-budget.log').write_text('\n'.join(lines)+'\n', encoding='utf-8')
            count = budget_count(lines)
            self.last_count, self.counter_complete = count, True
            if sum('budget enabled:' in line for line in lines) != 1:
                raise ExternalStop('budget_initialization_unconfirmed')
            if count > 63 or any('calls stopped:' in line or 'budget exhausted:' in line for line in lines):
                raise ExternalStop('provider_guard_stopped')
            return count
        except Exception as failure:
            self.stopped = str(failure) if isinstance(failure, ExternalStop) else 'budget_record_unavailable'
            raise ExternalStop(self.stopped) from None

    def request(self, method, path, body=None, principal=None, content_type='application/json'):
        # Never persist tokens, passwords or authentication response bodies.
        if path.startswith('/auth/'):
            return super().request(method, path, body, principal, content_type)
        if self.stopped:
            raise ExternalStop(self.stopped)
        record = {'case': self.case, 'method': method, 'path': path, 'body': body,
                  'providerBefore': self.snapshot()}
        started = time.monotonic()
        try:
            response = super().request(method, path, body, principal, content_type)
            record['response'] = response
            if response['status'] >= 429 or ('/messages/stream' in path and (
                    'done' not in response['events'] or 'error' in response['events'])):
                self.stopped = 'api_error_or_incomplete_stream'
        except Exception as failure:
            record['errorType'] = type(failure).__name__
            self.stopped = 'api_transport'
        finally:
            record['elapsedMs'] = round((time.monotonic()-started)*1000)
            try:
                record['providerAfter'] = self.snapshot()
            except ExternalStop:
                record['providerAfter'] = None
            self.records.append(record)
            with (self.output / 'attempts.raw.jsonl').open('a', encoding='utf-8') as file:
                file.write(json.dumps(record, ensure_ascii=False)+'\n')
        if self.stopped:
            raise ExternalStop(self.stopped)
        return response


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--docker', default='docker')
    parser.add_argument('--allow-cloud', action='store_true')
    args = parser.parse_args()
    if not args.allow_cloud:
        parser.error('Requires approval for fixed 4 cases / 7 turns / <=63 provider attempts')
    plan, suite = read_json(PLAN), read_json(SUITE)
    assert plan['status'] == 'authorized_fixed_scope_not_yet_executed'
    inventory = validate_suite(suite, PACKAGE)
    package_inventory = validate_package(PACKAGE)
    assert (inventory['cases'], inventory['chatTurns'], plan['providerRequestCap']) == (4, 7, 63)
    assert not subprocess.check_output(['git', 'diff', 'HEAD', '--name-only'], cwd=ROOT, text=True).strip()
    required_files=[SUITE.relative_to(ROOT).as_posix(),PLAN.relative_to(ROOT).as_posix(),
                    'scripts/evaluation/run_rag_followup.py','scripts/evaluation/capture_evidence.py',
                    'deploy/evaluation-rag-followup.yml']
    subprocess.check_output(['git','ls-files','--error-unmatch',*required_files],cwd=ROOT,stderr=subprocess.PIPE)
    output = ROOT / 'quality/evaluation/reports' / ('rag-followup-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
    output.mkdir()
    print('RUN', output, flush=True)
    client = GuardedClient(args.docker, output)
    env = {k:v for k,v in os.environ.items() if not k.startswith('OPENAI_')}
    compose = ['compose','--env-file',str(ROOT/'.env'),'-p','serviceflow-cloud-eval','-f',str(ROOT/'deploy/evaluation-cloud-compose.yml'),
               '-f',str(ROOT/'deploy/evaluation-provider-budget.yml'),'-f',str(ROOT/'deploy/evaluation-rag-followup.yml')]
    expanded = json.loads(subprocess.check_output([args.docker,*compose,'config','--format','json'],env=env,text=True,stderr=subprocess.PIPE))['services']['server']['environment']
    actual = dict(value.split('=',1) for value in client.identity['Config']['Env'] if '=' in value)
    assert all(actual.get(k)==str(v) for k,v in expanded.items() if k.startswith('OPENAI_'))
    for name,key in [('chat','OPENAI_MODEL'),('embedding','OPENAI_EMBEDDING_MODEL'),('reranker','OPENAI_RERANKER_MODEL')]:
        assert actual.get(key)==plan['plannedModelNames'][name],'Model changed outside reviewed scope'
    required={'SERVICEFLOW_AI_MODE':'cloud','SERVICEFLOW_RAG_MODE':'cloud','SERVICEFLOW_EVALUATION_ENABLED':'true',
              'SERVICEFLOW_EVALUATION_PROVIDER_BUDGET_ENABLED':'true','SERVICEFLOW_EVALUATION_PROVIDER_REQUEST_LIMIT':'63',
              'SERVICEFLOW_EVALUATION_STOP_ON_PROVIDER_FAILURE':'true','SERVICEFLOW_REFUND_SANDBOX_ENABLED':'false',
              'SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP':'false','SPRING_RABBITMQ_LISTENER_DIRECT_AUTO_STARTUP':'false',
              'SPRING_AI_RETRY_MAX_ATTEMPTS':'1','RESILIENCE4J_RETRY_INSTANCES_CHAT_MAX_ATTEMPTS':'1',
              'RESILIENCE4J_RETRY_INSTANCES_EMBEDDING_MAX_ATTEMPTS':'1','RESILIENCE4J_RETRY_INSTANCES_RERANKER_MAX_ATTEMPTS':'1',
              'JAVA_TOOL_OPTIONS':'-Djdk.httpclient.redirects.retrylimit=1'}
    assert all(actual.get(k)==v for k,v in required.items()),'Isolated cloud stop/budget configuration mismatch'
    assert client.identity['HostConfig']['RestartPolicy']['Name']=='no' and client.snapshot()==0
    versions = client.request('GET','/admin/knowledge-documents',principal='admin')['body']
    products = client.request('GET','/products?size=50&page=0',principal='customer')['body']['items']
    product_ids = {row['sku']:row['id'] for row in products}
    files = subprocess.check_output(['git','ls-files','apps/serviceflow-server/src/main','data/retail-holdout-v2',SUITE.relative_to(ROOT).as_posix(),PLAN.relative_to(ROOT).as_posix(),
                                     'scripts/evaluation/run_rag_followup.py','scripts/evaluation/eval_v2.py',
                                     'scripts/evaluation/capture_evidence.py','deploy/evaluation-cloud-compose.yml',
                                     'deploy/evaluation-provider-budget.yml','deploy/evaluation-rag-followup.yml'],cwd=ROOT,text=True).splitlines()
    hashes = {name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in files}
    metadata = {'codeCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                'containerId':client.identity['Id'],'imageId':client.identity['Image'],'startedAt':client.identity['State']['StartedAt'],'restartCount':0,
                'models':{k:v for k,v in actual.items() if k in ('OPENAI_MODEL','OPENAI_EMBEDDING_MODEL','OPENAI_RERANKER_MODEL')},
                'activeVersions':versions,'fileSha256':hashes,'inventory':inventory,'packageInventory':package_inventory,'providerLimit':63}
    write_json(output/'runtime-before.json',metadata)
    results=[]
    for case in suite['cases']:
        client.case=case['id']
        start_record=len(client.records)
        if client.stopped:
            result={'id':case['id'],'split':'development','passed':False,'notAttempted':True,'steps':[]}
        else:
            result=run_case(client,case,{'products':product_ids})
            # run_case catches transport exceptions; retain the actual failing response and trace, if available.
            case_records=[r for r in client.records[start_record:] if '/messages/stream' in r['path']]
            for step,record in zip(result['steps'],case_records):
                if 'errorType' in step and 'response' in record:
                    step.update(record['response'])
            for step in result['steps'][len(case_records):]:
                step['notAttempted'] = True
            if not client.stopped:
                try:
                    if any(hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=value for name,value in hashes.items()):
                        client.stopped='frozen_inputs_changed'
                    elif client.request('GET','/admin/knowledge-documents',principal='admin')['body'] != versions:
                        client.stopped='active_versions_changed'
                except Exception:
                    client.stopped=client.stopped or 'frozen_inputs_unavailable'
        completed=len(result['steps'])
        for index in range(completed,len(case['steps'])):
            result['steps'].append({'index':index,'passed':False,'notAttempted':True,'checks':{'notAttempted':False}})
        results.append(result)
        write_json(output/'responses.raw.json',{'runId':output.name,'results':results})
        print(case['id'], 'PASS' if result['passed'] else 'FAIL/NOT_ATTEMPTED', flush=True)
    write_json(output/'summary.json',{'runId':output.name,**summarize(results),'plannedTurns':7,'stopReason':client.stopped,
                                    'lastKnownProviderAttempts':client.last_count,'providerCountComplete':client.counter_complete,
                                    'actualChatHttpAttempts':sum('/messages/stream' in record['path'] for record in client.records),
                                    'notAttemptedSlots':sum(step.get('notAttempted',False) for result in results for step in result['steps']),
                                    'tokenUsage':None,'currencyCost':None,'metadata':metadata})
    write_json(output/'runtime-after.json',{'stopReason':client.stopped,'lastKnownProviderAttempts':client.last_count,
                                          'providerCountComplete':client.counter_complete})
    print('STOP_REASON',client.stopped,flush=True)
    raise SystemExit(0 if not client.stopped and all(result['passed'] for result in results) else 1)


if __name__=='__main__':
    main()
