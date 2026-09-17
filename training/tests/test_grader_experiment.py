import sys
from pathlib import Path
import tempfile
import unittest
import json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from serviceflow_training.evaluation.metrics import case, selection_key, evaluate, paired
from serviceflow_training.engine.adapters import preserve_epoch_adapter
from serviceflow_training.core.contracts import write_json, sha

class GraderExperimentTest(unittest.TestCase):
    def fixture(self):
        return {'id':'x','group_id':'g','category':'multi_complete','context':{'candidates':[{'chunkId':'a'},{'chunkId':'b'}]},
         'messages':[{}, {}, {'content':json.dumps({'sufficient':True,'rankedChunkIds':['a','b']})}]}
    def test_order_is_not_error_but_omission_is(self):
        r=self.fixture()
        self.assertTrue(case(r,{'status':'ok','output':'{"sufficient":true,"rankedChunkIds":["b","a"]}'})['joint'])
        self.assertFalse(case(r,{'status':'ok','output':'{"sufficient":true,"rankedChunkIds":["a"]}'})['joint'])
    def test_strict_json_and_ids(self):
        for out in ['{"sufficient":true,"sufficient":false,"rankedChunkIds":[]}',
                    '{"sufficient":true,"rankedChunkIds":["x"]}',
                    '```json\n{"sufficient":true,"rankedChunkIds":["a","b"]}\n```']:
            self.assertFalse(case(self.fixture(),{'status':'ok','output':out})['legal'])
    def test_missing_transport_remains_in_denominator(self):
        m=evaluate([self.fixture()],[{'id':'x','status':'timeout','output':''}])
        self.assertEqual(m['overall']['n'],1);self.assertEqual(m['overall']['joint_accuracy'],0)
    def test_partial_requires_supported_ids(self):
        r=self.fixture();r['messages'][2]['content']='{"sufficient":false,"rankedChunkIds":["a"]}'
        self.assertFalse(case(r,{'status':'ok','output':'{"sufficient":false,"rankedChunkIds":[]}'})['joint'])
    def test_selection_ties_prefer_earlier_epoch(self):
        m={'overall':{'joint_accuracy':.8,'false_sufficient_count':3}}
        self.assertLess(selection_key(m,1),selection_key(m,2))
    def test_epoch_adapter_has_no_optimizer_state_and_is_immutable(self):
        with tempfile.TemporaryDirectory() as t:
            run=Path(t);cp=run/'checkpoints'/'checkpoint-38';cp.mkdir(parents=True)
            (cp/'adapter_config.json').write_text('{}');(cp/'adapter_model.safetensors').write_bytes(b'fixture')
            (cp/'optimizer.pt').write_bytes(b'optimizer');write_json(cp/'trainer_state.json',{'epoch':1.,'global_step':38})
            receipt={'contract_sha256':'fixed','files_sha256':{n:sha(cp/n) for n in ['adapter_config.json','adapter_model.safetensors']}}
            target=Path(preserve_epoch_adapter(cp,run,receipt));self.assertFalse((target/'optimizer.pt').exists())
            self.assertEqual(str(target),preserve_epoch_adapter(cp,run,receipt))
            (target/'adapter_model.safetensors').write_bytes(b'corrupt')
            with self.assertRaises(ValueError):preserve_epoch_adapter(cp,run,receipt)

if __name__=='__main__':unittest.main()
