"""Verify or run the original final-checkpoint engine without rewriting its contract."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys
from serviceflow_training.core.contracts import ROOT, read_json, require

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', action='store_true', help='Resume with the unchanged original epoch configuration (WSL only)')
    parser.add_argument('--verify-state', action='store_true', help='Verify every retained checkpoint file without starting training')
    args = parser.parse_args()
    run = ROOT / 'runtime-data/training/grader-specialist-exp-01/seed-42'
    bundle = run.parent / 'resume-engine'
    sources = read_json(bundle / 'sources.json')
    for name, digest in sources.items():
        require(hashlib.sha256((bundle / 'training/scripts' / name).read_bytes()).hexdigest() == digest,
                'Frozen engine changed: ' + name)
    contract = read_json(run / 'resume-contract.json')
    keys = {'run_sft.py':'runner_sha256','checkpointing.py':'checkpointing_sha256',
            'checkpoint_policy.py':'checkpoint_policy_sha256','epoch_adapters.py':'epoch_adapters_sha256',
            'local_file_hash.py':'hash_helper_sha256','trainer_resume_epoch_compat.py':'trainer_compatibility_sha256',
            'trainer_resume_compat.py':'trainer_compatibility_constants_sha256'}
    for name, key in keys.items():
        require(sources[name] == contract[key], 'Frozen source does not match original checkpoint contract: ' + name)
    require((run / 'checkpoints/checkpoint-76/optimizer.pt').exists(), 'Full optimizer state missing')
    print('Frozen final engine and checkpoint source contract: VERIFIED', flush=True)
    if args.verify_state:
        from serviceflow_training.engine.checkpoints import verify_checkpoint, digest
        # The checkpoint receipt stores the original run contract digest.
        verify_checkpoint(run / 'checkpoints/checkpoint-76', digest(contract))
        print('Full checkpoint receipt: VERIFIED', flush=True)
    if not args.run: return
    require(os.name == 'posix', 'Use the fixed WSL training environment')
    # Recreate the old relative layout with links to unchanged real assets.
    links = {bundle / name:ROOT / name for name in ['apps','quality','data','local-datasets','runtime-data']}
    links.update({bundle / 'training' / name:ROOT / 'training' / name for name in ['data','configs']})
    for link, target in links.items():
        if link.is_symlink(): require(link.resolve() == target.resolve(), 'Unexpected recovery link')
        else:
            require(not link.exists(), 'Recovery link destination occupied')
            link.symlink_to(target, target_is_directory=True)
    command = [sys.executable, '-B', str(bundle / 'training/scripts/run_sft.py'),
               '--run-dir', str(run), '--reviewed', str(ROOT / 'local-datasets/serviceflow/grader-specialist-exp-01/all.jsonl'),
               '--resume', 'latest']
    subprocess.run(command, cwd=ROOT, check=True)

if __name__ == '__main__': main()
