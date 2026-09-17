"""Single entry point for the maintained Grader training toolchain."""
import os
from pathlib import Path
import runpy
import sys

SOURCE = Path(__file__).resolve().parent / 'src'
sys.path.insert(0, str(SOURCE))
# Child Python processes use the same package layout (including fixed WSL runtimes).
os.environ['PYTHONPATH'] = str(SOURCE) + os.pathsep + os.environ.get('PYTHONPATH', '')
COMMANDS = {
    'status': 'runtime.status',
    'data': 'core.contracts',
    'fetch-sources': 'data.fetch',
    'build-data': 'data.build',
    'audit-data': 'data.audit',
    'review-data': 'data.reviews',
    'inspect-sources': 'data.inspect',
    'lengths': 'data.lengths',
    'train': 'engine.train',
    'resume-final': 'engine.frozen_resume',
    'merge': 'engine.merge',
    'evaluate': 'evaluation.run',
    'score': 'evaluation.metrics',
    'score-tasks': 'evaluation.tasks',
    'java-acceptance': 'evaluation.java',
    'preflight': 'environment.preflight',
    'download-model': 'environment.assets',
    'download-wheels': 'environment.wheels',
    'verify-environment': 'environment.verify',
    'smoke-inference': 'environment.smoke',
    'download-java': 'environment.download_tools',
    'install-java': 'environment.install_tools',
    'maven': 'environment.maven',
}

def main():
    if len(sys.argv) < 2 or sys.argv[1] in {'-h', '--help'}:
        print('Usage: python -B training/cli.py <command> [arguments]\n')
        print('\n'.join(f'  {name:20} {target}' for name, target in COMMANDS.items()))
        return
    command = sys.argv[1]
    if command not in COMMANDS:
        raise SystemExit(f'Unknown command: {command}; use --help')
    sys.argv = [f'{sys.argv[0]} {command}', *sys.argv[2:]]
    runpy.run_module('serviceflow_training.' + COMMANDS[command], run_name='__main__')

if __name__ == '__main__':
    main()
