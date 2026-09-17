import json
import unittest
from test_pipeline import fixture
from serviceflow_training.evaluation.metrics import case
def grader_pass(row, prediction): return case(row, prediction)["joint"]

class IndependentMetricsTests(unittest.TestCase):
    def test_equal_support_order_is_not_quality_regression(self):
        r=fixture(task='grader',split='test')
        r['context']['candidates']=[{'chunkId':i,'title':'fixture','content':'fixture'} for i in ['a','b']]
        r['messages'][2]['content']=json.dumps({'sufficient':True,'rankedChunkIds':['a','b']})
        self.assertTrue(grader_pass(r,{'status':'ok','output':json.dumps({'sufficient':True,'rankedChunkIds':['b','a']})}))

    def test_partial_requires_both_false_and_supported_id(self):
        r=fixture(task='grader',split='test')
        r['messages'][2]['content']=json.dumps({'sufficient':False,'rankedChunkIds':['fixture-chunk']})
        for answer in [{'sufficient':True,'rankedChunkIds':['fixture-chunk']},{'sufficient':False,'rankedChunkIds':[]},{'sufficient':False,'rankedChunkIds':['outside']}]:
            self.assertFalse(grader_pass(r,{'status':'ok','output':json.dumps(answer)}))
        self.assertFalse(grader_pass(r,{'status':'incomplete','output':r['messages'][2]['content']}))
        self.assertTrue(grader_pass(r,{'status':'ok','output':r['messages'][2]['content']}))

if __name__=='__main__':unittest.main()
