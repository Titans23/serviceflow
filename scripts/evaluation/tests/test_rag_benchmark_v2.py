import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_rag_benchmark_v2 import build, validate, DESTINATION, MODES
from rag_metrics import score, summarize
from rag_benchmark import write_report


class RagBenchmarkV2Test(unittest.TestCase):
    def test_frozen_question_holdout_is_disjoint_and_labels_partition_the_product_sources(self):
        suite = build()
        self.assertEqual(suite, json.loads(DESTINATION.read_text(encoding='utf-8')))
        self.assertEqual(validate(suite)['splits'], {'revised_regression':104,'frozen_question_holdout':40})
        broken = copy.deepcopy(suite)
        case = next(c for c in broken['cases'] if c['optionalEvidence'])
        case['optionalEvidence'].append(case['goldEvidence'][0])
        with self.assertRaises(AssertionError): validate(broken)
        broken = copy.deepcopy(suite)
        broken['cases'][-1]['question'] = broken['cases'][0]['question']
        with self.assertRaises(AssertionError): validate(broken)

    def test_optional_facts_do_not_mask_missing_required_facts_or_forbidden_facts(self):
        case = {'id':'case','labelVersion':2,'answerable':True,'goldEvidence':['x.md#a'],
                'requiredFacts':['required'], 'optionalFacts':['optional'], 'forbiddenFacts':['forbidden']}
        facts = [{'source':'x.md','text':text} for text in ['required','optional','forbidden']]
        response = {'result':{'degraded':False}, 'selection':{'factIds':[0,1],'insufficient':False},
                    'factContext':{'facts':facts}}
        trace = {'stages':[{'stage':'initial.rerank','input':[{'chunkId':'x'}],'output':[{'chunkId':'x'}],'elapsedMs':1}]}
        chunks = {'x':{'source':'x.md','content':'## a：title\nrequired'}}
        result = score(case,response,trace,chunks)
        self.assertTrue(result['answerPass'])
        self.assertFalse(result['strictAnswerPass'])
        self.assertEqual(result['retrieval']['recallAt5'],1)
        self.assertEqual(result['optionalFactCount'],1)
        self.assertFalse(score({**case,'labelVersion':1},response,trace,chunks)['answerPass'])
        response['selection']['factIds']=[1]
        self.assertFalse(score(case,response,trace,chunks)['answerPass'])
        response['selection']['factIds']=[0,1,2]
        result=score(case,response,trace,chunks)
        self.assertFalse(result['answerPass'])
        self.assertEqual(result['forbiddenFactCount'],1)
        response['selection']={'factIds':[],'insufficient':True}
        unknown={**case,'answerable':False,'goldEvidence':[],'requiredFacts':[],'optionalFacts':[]}
        self.assertTrue(score(unknown,response,trace,chunks)['answerPass'])

    def test_report_keeps_holdout_separate_and_counts_unattempted_cases(self):
        suite=build()
        with tempfile.TemporaryDirectory() as directory:
            write_report(Path(directory),[],suite['cases'],{'modes':MODES},'not started')
            report=json.loads((Path(directory)/'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(report['perSplit']['frozen_question_holdout']['DENSE']['planned'],40)
            self.assertEqual(report['perSplit']['revised_regression']['DENSE']['notAttempted'],104)
            self.assertEqual(report['summary']['BM25']['answerPassRate'],0)
            self.assertIsNone(report['summary']['BM25']['p95Ms'])


if __name__=='__main__': unittest.main()
