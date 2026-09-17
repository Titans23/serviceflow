"""Package boundaries and immutable final identity; no model calls."""
import ast
import importlib.util
import json
from pathlib import Path
import sys
import unittest

TRAINING = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TRAINING / 'src'))
from serviceflow_training.core.contracts import ROOT


class LayoutTests(unittest.TestCase):
    def test_repository_root_survives_package_move(self):
        self.assertEqual(ROOT, TRAINING.parent)
        self.assertTrue((ROOT / 'apps/serviceflow-server/pom.xml').is_file())

    def test_all_internal_imports_resolve_to_source_files(self):
        for path in (TRAINING / 'src').rglob('*.py'):
            for node in ast.walk(ast.parse(path.read_text(encoding='utf-8'))):
                names = [node.module] if isinstance(node, ast.ImportFrom) else [x.name for x in node.names] if isinstance(node, ast.Import) else []
                for name in names:
                    if name and name.startswith('serviceflow_training.'):
                        module = TRAINING / 'src' / name.replace('.', '/')
                        self.assertTrue(module.with_suffix('.py').exists() or (module / '__init__.py').exists(), (path, name))

    def test_public_commands_map_to_real_modules(self):
        spec = importlib.util.spec_from_file_location('training_cli_layout', TRAINING / 'cli.py')
        cli = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cli)
        for module in cli.COMMANDS.values():
            self.assertTrue((TRAINING / 'src/serviceflow_training' / (module.replace('.', '/') + '.py')).is_file(), module)

    def test_final_identity_is_primary_grader_not_chatbot(self):
        policy = json.loads((TRAINING / 'configs/final-model.json').read_text(encoding='utf-8'))
        self.assertEqual((policy['seed'], policy['epoch']), (42, 2))
        self.assertEqual(policy['task'], 'evidence_grading_only')
        self.assertNotIn('official-business-v1', policy['model_directory'])
