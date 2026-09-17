"""Merge a verified local adapter into a new BF16 model using fixed LLaMA-Factory."""
import argparse
from contextlib import redirect_stdout, redirect_stderr
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import time

from serviceflow_training.engine.checkpoints import atomic_json
from serviceflow_training.core.contracts import ROOT, read_json, read_rows, require, training_runtime
from serviceflow_training.core.hashing import sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adapter', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--lock', type=Path, default=ROOT / 'runtime-data/training/grader-specialist-exp-01/seed-42.lock.json')
    parser.add_argument('--reviewed', type=Path, default=ROOT / 'local-datasets/serviceflow/grader-specialist-exp-01/all.jsonl')
    args = parser.parse_args()
    out = args.out.resolve()
    require(out.is_relative_to(ROOT / 'runtime-data/training') and not out.exists(),
            'Use a new directory inside runtime-data/training')
    adapter = args.adapter.resolve()
    epoch_receipt = read_json(adapter / 'epoch-adapter.json') if (adapter / 'epoch-adapter.json').exists() else None
    source_run = Path(epoch_receipt['source_run']) if epoch_receipt else adapter.parent
    source_execution = read_json(source_run / 'execution.json')
    require(source_execution['status'] == 'COMPLETED', 'Adapter source run is not complete')
    adapter_hash = sha(adapter / 'adapter_model.safetensors')
    if epoch_receipt:
        require(adapter.resolve().parent == source_run.resolve() / 'epoch-adapters', 'Epoch adapter path mismatch')
        require(epoch_receipt['contract_sha256'] == source_execution['contract_sha256'], 'Epoch belongs to another run')
        for name, expected in epoch_receipt['files_sha256'].items():
            require((adapter/name).resolve().parent == adapter and sha(adapter/name) == expected, 'Epoch adapter changed')
    else:
        require(adapter_hash == source_execution['adapter_sha256'], 'Adapter changed after training')
    lock = read_json(args.lock)
    source_manifest = read_json(source_run / 'run-manifest.json')
    require(source_manifest['lock'] == lock, 'Merge lock differs from the actual training run')
    require(source_manifest['input_sha256'] == sha(args.reviewed), 'Merge data differs from training data')
    model = Path(lock['base_model']['local_path'])
    os.environ.update(HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1', HF_DATASETS_OFFLINE='1',
                      WANDB_DISABLED='true', TOKENIZERS_PARALLELISM='false')
    runtime = training_runtime()
    require(all(lock['training_environment'].get(k) == v for k, v in runtime.items()
                if k != 'template_source_sha256'), 'Fixed merge environment changed')
    out.mkdir(parents=True)
    config = {'model_name_or_path': str(model), 'adapter_name_or_path': str(adapter),
              'template': 'qwen3', 'enable_thinking': False, 'trust_remote_code': False,
              'finetuning_type': 'lora', 'infer_dtype': 'bfloat16',
              'export_dir': str(out), 'export_size': 5, 'export_device': 'cpu', 'export_legacy_format': False}
    atomic_json(out / 'merge-config.json', config)
    record = {'status': 'VERIFYING', 'started_at': datetime.now(timezone.utc).isoformat(),
              'base_model': lock['base_model'], 'adapter_path': str(adapter), 'adapter_sha256': adapter_hash,
              'config_sha256': sha(out / 'merge-config.json'), 'runtime': runtime,
              'runner_sha256': sha(Path(__file__)), 'production_ready': False}
    record.update(reviewed_data_sha256=sha(args.reviewed), experiment_lock_sha256=sha(args.lock),
                  epoch_adapter=epoch_receipt,
                  hash_helper_sha256=sha(ROOT / 'training/src/serviceflow_training/core/hashing.py'))
    atomic_json(out / 'merge-manifest.json', record)
    started = time.monotonic()
    try:
        model_hashes = read_json(lock['base_model']['files_sha256_manifest'])
        for name, expected in model_hashes.items():
            path = (model / name).resolve()
            require(path.is_relative_to(model.resolve()) and sha(path) == expected, 'Base file changed: ' + name)
        record['status'] = 'MERGING'
        atomic_json(out / 'merge-manifest.json', record)
        with (out / 'merge.log').open('w', encoding='utf-8') as log, redirect_stdout(log), redirect_stderr(log):
            from llamafactory.train.tuner import export_model
            export_model(config)
        record['status'] = 'VERIFYING_EXPORT'
        atomic_json(out / 'merge-manifest.json', record)
        from transformers import AutoTokenizer
        from safetensors import safe_open
        original = AutoTokenizer.from_pretrained(model, local_files_only=True)
        merged = AutoTokenizer.from_pretrained(out, local_files_only=True)
        require(original.get_vocab() == merged.get_vocab(), 'Merged vocabulary changed')
        require(original.all_special_ids == merged.all_special_ids, 'Merged special token IDs changed')
        require(read_json(out / 'config.json')['torch_dtype'] == 'bfloat16', 'Export config is not BF16')
        rows = read_rows(args.reviewed)
        for row in rows:
            options = dict(tokenize=True, add_generation_prompt=True, enable_thinking=False)
            require(original.apply_chat_template(row['messages'][:2], **options)
                    == merged.apply_chat_template(row['messages'][:2], **options), 'Merged token parity failed')
        index = read_json(out / 'model.safetensors.index.json')
        tensor_keys = set()
        for shard in sorted(set(index['weight_map'].values())):
            with safe_open(out / shard, framework='pt', device='cpu') as weights:
                for key in weights.keys():
                    require(weights.get_slice(key).get_dtype() == 'BF16', 'Non-BF16 exported tensor: ' + key)
                    require('lora_' not in key, 'Unmerged adapter tensor in export')
                    tensor_keys.add(key)
        require(tensor_keys == set(index['weight_map']), 'Shard index/tensor mismatch')
        shutil.copyfile(model / 'LICENSE', out / 'LICENSE.base-model')
        template = ROOT / 'runtime-data/training/qwen3-nonthinking.jinja'
        shutil.copyfile(template, out / 'serviceflow-nonthinking.jinja')
        (out / 'SERVICEFLOW_MODEL_CARD.md').write_text(
            '# ServiceFlow merged candidate model\n\n'
            'BF16 merge of the fixed Qwen3-8B base and the recorded LoRA.\n'
            f'{sum(r["split"] == "train" for r in rows)} training examples; '
            'AI review is not independent human review. No production acceptance is implied.\n'
            'Use serviceflow-nonthinking.jinja when serving. This export has no optimizer state; '
            'resume training from the original experiment checkpoints.\n'
            'Base Apache-2.0 license is preserved in LICENSE.base-model. '
            'See merge-manifest.json for provenance and hashes.\n', encoding='utf-8')
        files = {p.name: sha(p) for p in sorted(out.iterdir()) if p.is_file()
                 and p.name not in {'merge-manifest.json', 'merge.log'}}
        record.update(status='COMPLETED', files_sha256=files, token_parity_rows=len(rows),
                      all_weight_tensors_bf16=True, tensor_count=len(tensor_keys),
                      serving_template_sha256=sha(template), elapsed_seconds=time.monotonic() - started)
    except BaseException as error:
        record.update(status='FAILED', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        record['finished_at'] = datetime.now(timezone.utc).isoformat()
        atomic_json(out / 'merge-manifest.json', record)
    print({k: v for k, v in record.items() if k not in {'files_sha256', 'base_model', 'runtime'}}, flush=True)


if __name__ == '__main__':
    main()
