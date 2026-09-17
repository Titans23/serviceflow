"""Run or resume a prepared single-GPU SFT; save full state and audit each attempt."""
import argparse
from contextlib import redirect_stdout, redirect_stderr
from datetime import datetime, timezone
import math
import os
from pathlib import Path
import signal
import time

from serviceflow_training.engine.checkpoints import (atomic_json, digest, preserve_incomplete, seal_checkpoint,
                           select_checkpoint, retain_latest_sealed)
from serviceflow_training.engine.policy import validate_policy, apply_pause
from serviceflow_training.engine.resume_compat import install_resume_tail_fix
from serviceflow_training.core.contracts import (ROOT, read_json, read_rows, require, training_runtime,
                      validate, tokenizer_hashes)
from serviceflow_training.core.hashing import sha
from serviceflow_training.engine.adapters import preserve_epoch_adapter


def build_contract(run_dir, reviewed, config, manifest):
    summary = validate(read_rows(reviewed))
    require(manifest['input_sha256'] == sha(reviewed), 'Reviewed data changed')
    require(manifest['contract'] == summary['contract'], 'Java prompts or heldout registry changed')
    runtime = training_runtime()
    lock = manifest['lock']
    require(all(lock['training_environment'].get(k) == v for k, v in runtime.items()
                if k != 'template_source_sha256'), 'Training environment differs from experiment lock')
    model = Path(config['model_name_or_path']).resolve()
    require(model == Path(lock['base_model']['local_path']).resolve(), 'Base model path changed')
    require(lock['status'] == 'VERIFIED', 'Experiment lock is not verified')
    dataset = Path(config['dataset_dir']).resolve()
    exported = read_json(dataset / 'manifest.json')
    require(exported['input_sha256'] == sha(reviewed), 'Exported data differs from reviewed input')
    require(exported['contract'] == summary['contract'], 'Exported prompt contract changed')
    for name, expected in exported['files_sha256'].items():
        path = (dataset / name).resolve()
        require(path.parent == dataset and sha(path) == expected, 'Exported dataset changed: ' + name)
    model_hashes = read_json(lock['base_model']['files_sha256_manifest'])
    for name, expected in model_hashes.items():
        path = (model / name).resolve()
        require(path.is_relative_to(model) and sha(path) == expected, 'Base model changed: ' + name)
    return {'schema_version': 1, 'config': config, 'manifest_sha256': sha(run_dir / 'run-manifest.json'),
            'input_sha256': sha(reviewed), 'dataset_files_sha256': exported['files_sha256'],
            'model_files_sha256': model_hashes, 'tokenizer_sha256': tokenizer_hashes(model),
            'runtime': runtime, 'prompt_contract': summary['contract'],
            'runner_sha256': sha(Path(__file__)),
            'checkpointing_sha256': sha(ROOT / 'training/src/serviceflow_training/engine/checkpoints.py'),
            'checkpoint_policy_sha256': sha(ROOT / 'training/src/serviceflow_training/engine/policy.py'),
            'epoch_adapters_sha256': sha(ROOT / 'training/src/serviceflow_training/engine/adapters.py'),
            'hash_helper_sha256': sha(ROOT / 'training/src/serviceflow_training/core/hashing.py'),
            'trainer_compatibility': install_resume_tail_fix(),
            'trainer_compatibility_sha256': sha(ROOT / 'training/src/serviceflow_training/engine/resume_compat.py'),
            'trainer_compatibility_constants_sha256': sha(ROOT / 'training/src/serviceflow_training/engine/resume_constants.py')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--reviewed', type=Path, required=True)
    parser.add_argument('--resume', help="'latest' or absolute checkpoint path inside this run")
    parser.add_argument('--save-steps', type=int, help='Initial-run override, optimizer steps')
    parser.add_argument('--save-strategy', choices=['epoch', 'steps'], help='Initial-run override')
    parser.add_argument('--save-total-limit', type=int, help='Epoch: 1; legacy steps: at least 2')
    parser.add_argument('--stop-after-step', type=int, help='Pause after this absolute optimizer step')
    args = parser.parse_args()
    require(os.name == 'posix', 'Execute in the fixed Linux/WSL training environment')
    require(int(os.environ.get('WORLD_SIZE', '1')) == 1, 'This runner supports one GPU only')
    run_dir = args.run_dir.resolve()
    require(run_dir.is_dir(), 'Prepare run first')
    import fcntl
    with (run_dir / '.runner.lock').open('a+') as lock_file:
        try:
            fcntl.flock(lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('Another process is using this run directory') from None
        execute(args, run_dir)


def execute(args, run_dir):
    import yaml
    config = yaml.safe_load((run_dir / 'train.yaml').read_text())
    preserve_adapters = config.pop('serviceflow_preserve_epoch_adapters', False)
    require(not config.get('resume_from_checkpoint'), 'Use --resume, not an unaudited YAML resume path')
    config.update(do_eval=True, eval_on_start=True, include_effective_tokens_per_second=True,
                  disable_tqdm=True, save_only_model=False, ignore_data_skip=False)
    if args.save_steps is not None:
        config['save_steps'] = args.save_steps
    if args.save_strategy is not None:
        config['save_strategy'] = args.save_strategy
    if args.save_total_limit is not None:
        config['save_total_limit'] = args.save_total_limit
    require(args.save_steps is None or config['save_strategy'] == 'steps',
            '--save-steps requires the explicit legacy steps strategy')
    require(config.get('bf16') is True and not config.get('fp16') and not config.get('deepspeed')
            and not config.get('fsdp') and config.get('finetuning_type') == 'lora', 'Expected single-GPU BF16 LoRA')
    output = Path(config['output_dir']).resolve()
    require(output == run_dir / 'checkpoints', 'Output must be this run/checkpoints')
    contract_file = run_dir / 'resume-contract.json'
    if args.resume:
        require(contract_file.is_file(), 'No resume contract; legacy completed runs stay immutable')
        require(args.save_steps is None and args.save_total_limit is None and args.save_strategy is None,
                'Resume reuses original save settings')
        original = read_json(contract_file)['config']
        # Initial CLI overrides are part of the immutable experiment contract.
        for key in ('save_strategy', 'save_steps', 'save_total_limit'):
            if key in original:
                config[key] = original[key]
            else:
                config.pop(key, None)
        require(not (run_dir / 'execution.json').exists()
                or read_json(run_dir / 'execution.json')['status'] != 'COMPLETED', 'Run already completed')
    else:
        require(not contract_file.exists() and not (run_dir / 'execution.json').exists(),
                'Run already attempted; use --resume latest or a new directory')
        require(not output.exists() or not any(output.iterdir()), 'Output is not empty')
    validate_policy(config)
    require(not (run_dir / 'STOP_REQUESTED').exists(), 'Remove the acknowledged STOP_REQUESTED file before launch')
    os.environ.update(HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1', HF_DATASETS_OFFLINE='1',
                      WANDB_DISABLED='true', TOKENIZERS_PARALLELISM='false')
    print('Verifying fixed runtime, model and dataset before training...', flush=True)
    manifest = read_json(run_dir / 'run-manifest.json')
    contract = build_contract(run_dir, args.reviewed, config, manifest)
    contract['preserve_epoch_adapters'] = preserve_adapters
    if args.resume:
        require(contract == read_json(contract_file), 'Experiment changed; refuse incompatible resume')
    else:
        atomic_json(contract_file, contract)
    contract_sha = digest(contract)
    attempt_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    attempt = run_dir / 'attempts' / attempt_id
    attempt.mkdir(parents=True)
    resumed_step, rejected = 0, []
    if args.resume:
        checkpoint, rejected = select_checkpoint(output, args.resume, contract_sha)
        resumed_step = read_json(checkpoint / 'trainer_state.json')['global_step']
        preserve_incomplete(output, rejected, attempt_id)
        config['resume_from_checkpoint'] = str(checkpoint)
        # Do not consume RNG on an extra evaluation at the resume boundary.
        config['eval_on_start'] = False
    require(args.stop_after_step is None or args.stop_after_step > resumed_step,
            'stop-after-step must be beyond restored global_step')
    epoch_saving = config['save_strategy'] == 'epoch'
    if epoch_saving:
        # Trainer normally rotates BEFORE on_save. Defer deletion until our hash
        # receipt is durable, so interruption cannot remove the last good state.
        config['save_total_limit'] = None
    atomic_json(attempt / 'effective-config.json', config)
    status = {'status': 'INITIALIZING', 'attempt_id': attempt_id, 'started_at': datetime.now(timezone.utc).isoformat(),
              'resume_from_checkpoint': config.get('resume_from_checkpoint'), 'restored_step': resumed_step,
              'contract_sha256': contract_sha, 'rejected_checkpoints': rejected, 'production_ready': False}
    status['checkpoint_policy'] = {'strategy': config['save_strategy'],
        'retained_complete': 1 if epoch_saving else config['save_total_limit'],
        'rotation_after_seal': epoch_saving}
    def publish():
        atomic_json(attempt / 'execution.json', status)
        atomic_json(run_dir / 'execution.json', status)
    publish()
    started = time.monotonic()
    stop = {'requested': False}
    def request_stop(signum, frame):
        stop['requested'] = True
    previous = {sig: signal.signal(sig, request_stop) for sig in (signal.SIGINT, signal.SIGTERM)}
    try:
        with (attempt / 'training.log').open('w', encoding='utf-8', buffering=1) as log, redirect_stdout(log), redirect_stderr(log):
            import torch
            from transformers import TrainerCallback
            from llamafactory.hparams import get_train_args
            from llamafactory.model import load_tokenizer
            from llamafactory.data import get_dataset, get_template_and_fix_tokenizer
            from llamafactory.train.tuner import run_exp
            require(torch.cuda.is_available() and torch.cuda.is_bf16_supported(), 'CUDA BF16 unavailable')
            ma, da, ta, fa, ga = get_train_args(config)
            tokmod = load_tokenizer(ma)
            tokenizer = tokmod['tokenizer']
            template = get_template_and_fix_tokenizer(tokenizer, da)
            datasets = get_dataset(template, ma, da, ta, stage='sft', **tokmod)
            reviewed = read_rows(args.reviewed)
            masks = {}
            for split, key in [('train', 'train_dataset'), ('validation', 'eval_dataset')]:
                expected = {}
                for row in reviewed:
                    if row['split'] != split:
                        continue
                    system, user, answer = [m['content'] for m in row['messages']]
                    source, target = template.encode_oneturn(tokenizer, [{'role': 'user', 'content': user},
                            {'role': 'assistant', 'content': answer}], system=system)
                    require(tokenizer.decode(target) == answer + tokenizer.eos_token + '\n', 'Unexpected target')
                    expected[tuple(source + target)] = [-100] * len(source) + target
                actual = datasets[key]
                require(len(actual) == len(expected), 'Actual dataset lost or duplicated rows')
                supervised = 0
                for item in actual:
                    ids = tuple(item['input_ids'])
                    require(ids in expected and item['labels'] == expected[ids], 'Actual label mask mismatch')
                    supervised += sum(x != -100 for x in item['labels'])
                masks[split] = {'rows': len(actual), 'supervised_tokens': supervised, 'no_truncation': True}
            atomic_json(attempt / 'mask-audit.json', masks)
            del datasets
            class Monitor(TrainerCallback):
                def on_train_begin(self, args, state, control, model=None, optimizer=None, lr_scheduler=None, **kwargs):
                    trainable = [(n, p) for n, p in model.named_parameters() if p.requires_grad]
                    require(trainable and all('lora_' in n for n, _ in trainable), 'Non-LoRA weights trainable')
                    require(state.global_step == resumed_step, 'Trainer did not restore expected step')
                    optimizer_steps = sorted({int(v['step']) for v in optimizer.state.values() if 'step' in v})
                    if resumed_step:
                        require(optimizer_steps == [resumed_step], 'Optimizer state was not restored')
                        require(lr_scheduler.state_dict()['last_epoch'] == resumed_step, 'Scheduler state was not restored')
                    status.update(status='TRAINING', trainable_parameters=sum(p.numel() for _, p in trainable),
                                  restored_optimizer_steps=optimizer_steps,
                                  restored_scheduler_epoch=lr_scheduler.state_dict()['last_epoch'], target_steps=state.max_steps)
                    torch.cuda.reset_peak_memory_stats()
                    publish()
                def on_step_end(self, args, state, control, **kwargs):
                    status['last_step'] = state.global_step
                    if stop['requested'] or (run_dir / 'STOP_REQUESTED').exists() or (
                            a.stop_after_step is not None and state.global_step >= a.stop_after_step):
                        stop['requested'] = True
                        status['pause_requested'] = True
                    publish()
                    return apply_pause(config['save_strategy'], stop['requested'], control)
                def on_epoch_end(self, args, state, control, **kwargs):
                    stop['requested'] |= (run_dir / 'STOP_REQUESTED').exists()
                    return apply_pause(config['save_strategy'], stop['requested'], control, epoch_end=True)
                def on_save(self, args, state, control, **kwargs):
                    checkpoint = output / f'checkpoint-{state.global_step}'
                    receipt = seal_checkpoint(checkpoint, contract_sha)
                    if preserve_adapters:
                        status['last_epoch_adapter'] = preserve_epoch_adapter(checkpoint, run_dir, receipt)
                    if epoch_saving:
                        status['rotated_checkpoints'] = retain_latest_sealed(output, checkpoint, contract_sha)
                    status.update(last_complete_checkpoint=str(checkpoint), last_checkpoint_step=receipt['global_step'])
                    publish()
                def on_log(self, args, state, control, logs=None, **kwargs):
                    for key in ('loss', 'eval_loss', 'grad_norm'):
                        if key in (logs or {}):
                            require(math.isfinite(logs[key]), 'Nonfinite ' + key)
                    event = {'step': state.global_step, 'epoch': state.epoch, **(logs or {})}
                    import json
                    with (attempt / 'progress.jsonl').open('a', encoding='utf-8') as stream:
                        stream.write(json.dumps(event) + '\n')
            a = args
            run_exp(args=config, callbacks=[Monitor()])
            completed = status.get('last_step', resumed_step) >= status.get('target_steps', math.inf)
            require(status.get('last_complete_checkpoint'), 'No complete resumable checkpoint saved')
            require(completed or stop['requested'], 'Trainer exited before target without a pause request')
            status.update(status='COMPLETED' if completed else 'PAUSED',
                          peak_allocated_bytes=torch.cuda.max_memory_allocated(),
                          peak_reserved_bytes=torch.cuda.max_memory_reserved(),
                          adapter_sha256=sha(output / 'adapter_model.safetensors'))
    except BaseException as error:
        status.update(status='FAILED', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        status.update(finished_at=datetime.now(timezone.utc).isoformat(), elapsed_seconds=time.monotonic() - started)
        publish()
        for sig, handler in previous.items():
            signal.signal(sig, handler)
    print(status, flush=True)


if __name__ == '__main__':
    main()
