"""Offline corruption/ownership tests; temporary synthetic checkpoint files only."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from serviceflow_training.engine.checkpoints import (REQUIRED, atomic_json, seal_checkpoint, select_checkpoint,
                           verify_checkpoint, preserve_incomplete, retain_latest_sealed)
from serviceflow_training.engine.policy import validate_policy, apply_pause
from types import SimpleNamespace


class CheckpointTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.output = Path(self.temporary.name).resolve() / 'checkpoints'
        self.output.mkdir()

    def checkpoint(self, step):
        path = self.output / f'checkpoint-{step}'
        path.mkdir()
        for name in REQUIRED:
            (path / name).write_bytes(b'synthetic-state')
        atomic_json(path / 'trainer_state.json', {'global_step': step})
        seal_checkpoint(path, 'experiment-one')
        return path

    def test_latest_is_numeric_and_complete(self):
        self.checkpoint(2)
        expected = self.checkpoint(10)
        actual, rejected = select_checkpoint(self.output, 'latest', 'experiment-one')
        self.assertEqual(actual, expected)
        self.assertFalse(rejected)

    def test_partial_latest_falls_back_and_is_preserved(self):
        complete = self.checkpoint(2)
        partial = self.checkpoint(3)
        (partial / 'optimizer.pt').write_bytes(b'partial-write')
        actual, rejected = select_checkpoint(self.output, 'latest', 'experiment-one')
        self.assertEqual(actual, complete)
        self.assertEqual(len(rejected), 1)
        preserve_incomplete(self.output, rejected, 'attempt-test')
        self.assertFalse(partial.exists())
        self.assertEqual((Path(rejected[0]['preserved_at']) / 'optimizer.pt').read_bytes(), b'partial-write')

    def test_adapter_only_is_not_resumable(self):
        path = self.checkpoint(2)
        (path / 'optimizer.pt').unlink()
        with self.assertRaisesRegex(ValueError, 'No complete verified checkpoint'):
            select_checkpoint(self.output, 'latest', 'experiment-one')

    def test_different_experiment_is_rejected(self):
        path = self.checkpoint(2)
        with self.assertRaisesRegex(ValueError, 'different experiment'):
            verify_checkpoint(path, 'experiment-two')

    def test_explicit_rewind_is_rejected(self):
        older = self.checkpoint(2)
        self.checkpoint(3)
        with self.assertRaisesRegex(ValueError, 'newest complete checkpoint'):
            select_checkpoint(self.output, str(older), 'experiment-one')

    def test_receipt_cannot_escape_checkpoint(self):
        path = self.checkpoint(2)
        from serviceflow_training.core.contracts import read_json
        receipt = read_json(path / 'serviceflow-checkpoint.json')
        receipt['files_sha256']['../outside.pt'] = 'bad'
        atomic_json(path / 'serviceflow-checkpoint.json', receipt)
        with self.assertRaisesRegex(ValueError, 'unsafe'):
            verify_checkpoint(path, 'experiment-one')

    def test_wrong_trainer_step_cannot_be_sealed(self):
        path = self.checkpoint(2)
        atomic_json(path / 'trainer_state.json', {'global_step': 1})
        with self.assertRaisesRegex(ValueError, 'step'):
            seal_checkpoint(path, 'experiment-one')

    def test_epoch_rotation_keeps_only_latest_after_seal(self):
        old = self.checkpoint(5)
        newest = self.checkpoint(10)
        self.assertEqual(retain_latest_sealed(self.output, newest, 'experiment-one'), ['checkpoint-5'])
        self.assertFalse(old.exists())
        self.assertEqual(select_checkpoint(self.output, 'latest', 'experiment-one')[0], newest)

    def test_failed_replacement_does_not_remove_old_checkpoint(self):
        old = self.checkpoint(5)
        newest = self.checkpoint(10)
        (newest / 'optimizer.pt').write_bytes(b'broken')
        with self.assertRaises(ValueError):
            retain_latest_sealed(self.output, newest, 'experiment-one')
        self.assertTrue(old.exists())
        self.assertEqual(select_checkpoint(self.output, 'latest', 'experiment-one')[0], old)

    def test_rotation_refuses_foreign_checkpoint(self):
        old = self.checkpoint(5)
        seal_checkpoint(old, 'foreign')
        newest = self.checkpoint(10)
        with self.assertRaises(ValueError):
            retain_latest_sealed(self.output, newest, 'experiment-one')
        self.assertTrue(old.exists())

    def test_epoch_pause_waits_for_boundary(self):
        control = SimpleNamespace(should_save=False, should_training_stop=False)
        apply_pause('epoch', True, control)
        self.assertFalse(control.should_save)
        self.assertFalse(control.should_training_stop)
        apply_pause('epoch', True, control, epoch_end=True)
        self.assertTrue(control.should_save and control.should_training_stop)

    def test_epoch_policy_is_latest_only(self):
        validate_policy({'save_strategy': 'epoch', 'save_total_limit': 1})
        for bad in [{'save_strategy':'epoch','save_total_limit':2},
                    {'save_strategy':'epoch','save_total_limit':1,'load_best_model_at_end':True},
                    {'save_strategy':'steps','save_total_limit':1,'save_steps':5}]:
            with self.assertRaises(ValueError):
                validate_policy(bad)


if __name__ == '__main__':
    unittest.main()
