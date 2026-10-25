from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_rag_followup import GuardedClient, ExternalStop
from eval_v2 import Client


class FollowupStopTests(unittest.TestCase):
    def client(self,output):
        client=object.__new__(GuardedClient)
        client.output=Path(output);client.stopped=None;client.case='fixture';client.records=[]
        client.last_count=None;client.counter_complete=False
        client.identity={'Id':'container','State':{'StartedAt':'time'},'RestartCount':0}
        client.inspect=Mock(return_value=client.identity)
        return client

    def test_provider_stop_record_prevents_next_http(self):
        with tempfile.TemporaryDirectory() as temp:
            client=self.client(temp)
            client.command=Mock(return_value='Evaluation provider budget enabled:\nEvaluation provider request admitted: used=1\nEvaluation provider calls stopped: reason=http_429')
            with self.assertRaises(ExternalStop):client.snapshot()
            self.assertEqual(client.last_count,1)
            self.assertTrue(client.counter_complete)
            with patch.object(Client,'request') as request,self.assertRaises(ExternalStop):
                client.request('POST','/chat/sessions/session/messages/stream',{})
            request.assert_not_called()

    def test_process_change_is_not_a_complete_counter(self):
        with tempfile.TemporaryDirectory() as temp:
            client=self.client(temp)
            client.inspect=Mock(return_value={'Id':'other','State':{'StartedAt':'later'},'RestartCount':0})
            with self.assertRaises(ExternalStop):client.snapshot()
            self.assertFalse(client.counter_complete)

    def test_incomplete_api_stream_is_retained_then_batch_stops(self):
        with tempfile.TemporaryDirectory() as temp:
            client=self.client(temp)
            client.snapshot=Mock(side_effect=[0,1])
            response={'status':200,'events':{},'eventNames':[],'answer':'partial','body':None,'latencyMs':1}
            with patch.object(Client,'request',return_value=response) as request:
                with self.assertRaises(ExternalStop):client.request('POST','/chat/sessions/session/messages/stream',{})
                with self.assertRaises(ExternalStop):client.request('POST','/chat/sessions/session/messages/stream',{})
                self.assertEqual(request.call_count,1)
            self.assertEqual(client.records[0]['response'],response)
            self.assertTrue((Path(temp)/'attempts.raw.jsonl').exists())


if __name__=='__main__':unittest.main()
