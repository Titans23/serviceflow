import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.error
import urllib.request
import uuid


class ChannelContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="serviceflow-channel-")
        self.addCleanup(self.temp.cleanup)
        self.identifier = str(uuid.uuid4())
        self.start()
        self.addCleanup(self.stop)

    def start(self):
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        self.base = "http://127.0.0.1:" + str(port)
        env = {**os.environ, "PORT": str(port), "CHANNEL_DB": str(Path(self.temp.name) / "channel.sqlite"),
               "CHANNEL_TOKEN": "isolated-contract-token", "CALLBACK_URL": ""}
        self.process = subprocess.Popen([sys.executable, str(Path(__file__).with_name("server.py"))], env=env,
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        for _ in range(100):
            try:
                if self.request("/health")[0] == 200:
                    return
            except OSError:
                time.sleep(0.02)
        self.fail("channel did not start")

    def stop(self):
        self.process.terminate()
        self.process.communicate(timeout=5)

    def request(self, path, data=None, token="isolated-contract-token"):
        req = urllib.request.Request(self.base + path, data=json.dumps(data).encode() if data is not None else None,
                                     headers={"Content-Type": "application/json", "X-Refund-Token": token})
        try:
            with urllib.request.urlopen(req, timeout=3) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def submit(self, attempt=1, amount="299.00"):
        return self.request("/refunds", {"refundId": self.identifier, "attemptNo": attempt, "amount": amount})

    def test_concurrent_duplicate_submissions_are_persistent_and_amount_bound(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            results = list(pool.map(lambda _: self.submit(), range(20)))
        self.assertTrue(all(status == 200 and row["status"] == "SUCCEEDED" for status, row in results))
        row = self.request("/inspect/" + self.identifier)[1]
        self.assertEqual((row["accept_count"], row["effective_refunds"]), (1, 1))
        self.assertEqual(self.submit(amount="300.00")[0], 409)
        self.stop()
        self.start()
        self.assertEqual(self.request("/refunds/" + self.identifier)[1]["status"], "SUCCEEDED")
        self.assertEqual(self.request("/inspect/" + self.identifier)[1]["effective_refunds"], 1)

    def test_lost_acceptance_response_is_queryable_and_does_not_repeat_refund(self):
        self.request("/control/" + self.identifier, {"mode": "DROP_RESPONSE_ONCE"})
        with self.assertRaises((http.client.RemoteDisconnected, urllib.error.URLError)):
            self.submit()
        self.assertEqual(self.request("/refunds/" + self.identifier)[1]["status"], "SUCCEEDED")
        self.assertEqual(self.submit()[0], 200)
        self.assertEqual(self.request("/inspect/" + self.identifier)[1]["accept_count"], 1)

    def test_unknown_and_pending_are_not_retry_permission_but_explicit_failure_is(self):
        self.request("/control/" + self.identifier, {"mode": "UNAVAILABLE"})
        self.assertEqual(self.request("/refunds/" + self.identifier)[0], 503)
        self.assertEqual(self.request("/inspect/" + self.identifier)[1]["accept_count"], 0)
        self.request("/control/" + self.identifier, {"mode": "PENDING"})
        self.assertEqual(self.submit()[1]["status"], "ACCEPTED")
        self.assertEqual(self.submit(attempt=2)[0], 409)
        self.identifier = str(uuid.uuid4())
        self.request("/control/" + self.identifier, {"mode": "FAIL_ONCE"})
        self.assertEqual(self.submit()[1]["status"], "FAILED")
        self.assertEqual(self.submit(attempt=2)[1]["status"], "SUCCEEDED")
        self.assertEqual(self.submit(attempt=2)[1]["status"], "SUCCEEDED")
        self.assertEqual(self.request("/inspect/" + self.identifier)[1]["effective_refunds"], 1)

    def test_missing_credential_cannot_query_or_mutate(self):
        self.assertEqual(self.request("/refunds/" + self.identifier, token="wrong")[0], 403)
        self.assertEqual(self.request("/control/" + self.identifier, {"mode": "SUCCESS"}, token="")[0], 403)


if __name__ == "__main__":
    unittest.main()
