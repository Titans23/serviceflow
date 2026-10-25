import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cloud_business_recovery import budget_count, drop_committed_response, verify_runtime, denied, PLAN


class CloudBusinessTests(unittest.TestCase):
    def fixture(self):
        environment = {
            'SERVICEFLOW_AI_MODE':'cloud','SERVICEFLOW_RAG_MODE':'cloud',
            'SERVICEFLOW_EVALUATION_ENABLED':'true','SERVICEFLOW_REFUND_SANDBOX_ENABLED':'true',
            'SERVICEFLOW_EVALUATION_PROVIDER_BUDGET_ENABLED':'true','SERVICEFLOW_EVALUATION_PROVIDER_REQUEST_LIMIT':'27',
            'SPRING_AI_RETRY_MAX_ATTEMPTS':'1','RESILIENCE4J_RETRY_INSTANCES_CHAT_MAX_ATTEMPTS':'1',
            'RESILIENCE4J_RETRY_INSTANCES_EMBEDDING_MAX_ATTEMPTS':'1','RESILIENCE4J_RETRY_INSTANCES_RERANKER_MAX_ATTEMPTS':'1',
            'SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP':'false','SPRING_RABBITMQ_LISTENER_DIRECT_AUTO_STARTUP':'false',
            'JAVA_TOOL_OPTIONS':'-Djdk.httpclient.redirects.retrylimit=1','OPENAI_API_KEY':'synthetic-stub-only'}
        container = {'Config':{'Env':[k+'='+v for k,v in environment.items()]},'RestartCount':0,
                     'HostConfig':{'RestartPolicy':{'Name':'no'}}}
        return container, environment, ['Evaluation provider budget enabled: limit=27']

    def test_frozen_plan_has_six_cases_and_fourteen_new_turns(self):
        plan=json.loads(PLAN.read_text(encoding='utf-8'))
        self.assertEqual(len(plan['cases']),6)
        self.assertEqual(sum(len(c['messages']) for c in plan['cases']),14)
        self.assertEqual(plan['providerRequestLimit'],27)

    def test_counter_accepts_contiguous_attempts_and_rejects_restart(self):
        lines=['Evaluation provider request admitted: used='+str(i)+', limit=27' for i in range(1,28)]
        self.assertEqual(budget_count(lines),27)
        with self.assertRaises(RuntimeError):budget_count(lines+lines[:1])
        with self.assertRaises(RuntimeError):budget_count(lines[1:])

    def test_preflight_requires_unused_budget_and_matching_secret_without_exposing_it(self):
        container,expected,lines=self.fixture()
        verify_runtime(container,expected,lines)
        with self.assertRaises(RuntimeError):verify_runtime(container,expected,lines+['Evaluation provider request admitted: used=1'])
        expected['OPENAI_API_KEY']='another-private-value'
        with self.assertRaises(RuntimeError) as failure:verify_runtime(container,expected,lines)
        self.assertNotIn(expected['OPENAI_API_KEY'],str(failure.exception))

    def test_preflight_rejects_wrong_modes_retries_ingestion_and_restart(self):
        original,expected,lines=self.fixture()
        for key,value in [('SERVICEFLOW_AI_MODE','demo'),('SERVICEFLOW_REFUND_SANDBOX_ENABLED','false'),
                          ('SPRING_AI_RETRY_MAX_ATTEMPTS','2'),('SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP','true')]:
            container=copy.deepcopy(original)
            container['Config']['Env']=[key+'='+value if v.startswith(key+'=') else v for v in container['Config']['Env']]
            with self.subTest(key=key),self.assertRaises(RuntimeError):verify_runtime(container,expected,lines)
        for field,value in [('RestartCount',1)]:
            container=copy.deepcopy(original);container[field]=value
            with self.assertRaises(RuntimeError):verify_runtime(container,expected,lines)

    def test_proxy_calls_upstream_once_then_discards_whole_response(self):
        calls=[]
        def commit():
            calls.append('commit')
            return {'events':{'done':{'resultCode':'CANCELLED'}}}
        upstream,lost,elapsed=drop_committed_response(commit)
        self.assertTrue(lost)
        self.assertEqual(calls,['commit'])
        self.assertEqual(upstream[0]['events']['done']['resultCode'],'CANCELLED')
        self.assertGreaterEqual(elapsed,0)

    def test_retryable_internal_error_is_not_a_valid_permission_denial(self):
        self.assertFalse(denied({'status':200,'events':{'error':{'code':'INTERNAL_ERROR','retryable':True}}}))
        self.assertTrue(denied({'status':200,'events':{'error':{'code':'FORBIDDEN','retryable':False}}}))


if __name__=='__main__':unittest.main()
