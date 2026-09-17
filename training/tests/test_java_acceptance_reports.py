import os
from pathlib import Path
import sys
import tempfile
import time
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from serviceflow_training.evaluation.java import fresh_test_counts


class JavaReportFreshnessTest(unittest.TestCase):
    def test_stale_passing_report_cannot_validate_a_failed_new_attempt(self):
        with tempfile.TemporaryDirectory() as directory:
            report=Path(directory)/'suite.xml'
            report.write_text('<testsuite tests="5" failures="0" errors="0" skipped="0"/>')
            os.utime(report,ns=(1_000_000_000,1_000_000_000))
            with self.assertRaisesRegex(ValueError,'fresh Java test report'):
                fresh_test_counts(report,time.time_ns())

    def test_fresh_report_preserves_failure_and_skip_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            report=Path(directory)/'suite.xml';started=time.time_ns()
            report.write_text('<testsuite tests="5" failures="1" errors="0" skipped="2"/>')
            os.utime(report,ns=(started+1_000_000_000,started+1_000_000_000))
            self.assertEqual(fresh_test_counts(report,started),{'tests':5,'failures':1,'errors':0,'skipped':2})


if __name__=='__main__':unittest.main()
