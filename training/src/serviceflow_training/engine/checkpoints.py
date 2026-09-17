"""Complete-checkpoint receipts and guarded resume for single-GPU LoRA runs."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

from serviceflow_training.core.contracts import read_json, require, sha

RECEIPT = 'serviceflow-checkpoint.json'
REQUIRED = {'adapter_config.json', 'adapter_model.safetensors', 'optimizer.pt',
            'scheduler.pt', 'rng_state.pth', 'trainer_state.json', 'training_args.bin'}


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('w', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def checkpoint_step(path):
    match = re.fullmatch(r'checkpoint-(\d+)', Path(path).name)
    require(match, 'Expected checkpoint-N directory')
    return int(match[1])


def seal_checkpoint(path, contract_sha256):
    path = Path(path)
    require(REQUIRED <= {p.name for p in path.iterdir() if p.is_file()}, 'Incomplete checkpoint state')
    state = read_json(path / 'trainer_state.json')
    require(state['global_step'] == checkpoint_step(path), 'Checkpoint step disagrees with Trainer state')
    files = {p.name: sha(p) for p in sorted(path.iterdir())
             if p.is_file() and p.name != RECEIPT and not p.name.endswith('.tmp')}
    require(all((path / name).stat().st_size > 0 for name in REQUIRED), 'Empty checkpoint state')
    receipt = {'schema_version': 1, 'global_step': state['global_step'],
               'contract_sha256': contract_sha256, 'files_sha256': files,
               'sealed_at': datetime.now(timezone.utc).isoformat()}
    atomic_json(path / RECEIPT, receipt)
    return receipt


def verify_checkpoint(path, contract_sha256):
    path = Path(path)
    receipt = read_json(path / RECEIPT)
    require(receipt['contract_sha256'] == contract_sha256, 'Checkpoint belongs to a different experiment')
    require(REQUIRED <= set(receipt['files_sha256']), 'Checkpoint has no optimizer/scheduler/RNG receipt')
    for name, expected in receipt['files_sha256'].items():
        file = (path / name).resolve()
        require(file.parent == path.resolve() and file.is_file(), 'Checkpoint file missing or unsafe')
        require(sha(file) == expected, 'Checkpoint file changed: ' + name)
    state = read_json(path / 'trainer_state.json')
    require(receipt['global_step'] == state['global_step'] == checkpoint_step(path), 'Checkpoint step mismatch')
    return receipt


def select_checkpoint(output, requested, contract_sha256):
    """Select newest complete receipt; never silently start over or rewind a run."""
    output = Path(output).resolve()
    candidates = sorted((p for p in output.glob('checkpoint-*')
                         if p.is_dir() and re.fullmatch(r'checkpoint-\d+', p.name)),
                        key=checkpoint_step, reverse=True)
    rejected = []
    for candidate in candidates:
        require(candidate.resolve().parent == output, 'Checkpoint must stay inside run output')
        try:
            verify_checkpoint(candidate, contract_sha256)
        except (ValueError, OSError, KeyError, TypeError) as error:
            rejected.append({'path': str(candidate), 'reason': str(error)})
            continue
        if requested != 'latest':
            require(Path(requested).resolve() == candidate.resolve(),
                    'Use newest complete checkpoint; use a separate experiment for branching')
        return candidate, rejected
    raise ValueError('No complete verified checkpoint; cannot resume. Original files preserved.')


def preserve_incomplete(output, rejected, attempt):
    """Move rejected newer checkpoints aside so Trainer cannot mix partial writes."""
    output = Path(output).resolve()
    for item in rejected:
        source = Path(item['path']).resolve()
        destination = output.parent / 'incomplete-checkpoints' / f'{attempt}-{source.name}'
        require(source.parent == output and destination.resolve().is_relative_to(output.parent),
                'Checkpoint move escaped run directory')
        require(not destination.exists(), 'Quarantine destination already exists')
        destination.parent.mkdir(parents=True, exist_ok=True)
        source.rename(destination)
        item['preserved_at'] = str(destination)


def retain_latest_sealed(output, latest, contract_sha256):
    """Rotate only after the replacement is sealed; preserve unknown/partial state."""
    output = Path(output).resolve()
    latest = Path(latest).resolve()
    require(latest.parent == output, 'Latest checkpoint escaped run directory')
    verify_checkpoint(latest, contract_sha256)
    victims = []
    for candidate in output.glob('checkpoint-*'):
        if not re.fullmatch(r'checkpoint-\d+', candidate.name):
            continue
        require(not candidate.is_symlink() and candidate.resolve().parent == output,
                'Checkpoint rotation escaped run directory')
        if checkpoint_step(candidate) >= checkpoint_step(latest):
            continue
        # Never delete an unsealed, corrupt, or foreign experiment directory.
        verify_checkpoint(candidate, contract_sha256)
        require(not any(p.is_symlink() for p in candidate.rglob('*')), 'Unsafe checkpoint contents')
        victims.append(candidate.resolve())
    for candidate in victims:
        shutil.rmtree(candidate)
    return [p.name for p in victims]
