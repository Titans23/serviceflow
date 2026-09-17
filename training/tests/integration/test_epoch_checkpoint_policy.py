"""Pinned Trainer CPU check: epoch-only save, seal-before-rotate and exact resume."""
import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "src"))
import shutil
from serviceflow_training.engine.checkpoints import atomic_json, seal_checkpoint, retain_latest_sealed, select_checkpoint
from serviceflow_training.engine.policy import apply_pause
from serviceflow_training.core.contracts import require


def main():
    p = argparse.ArgumentParser(); p.add_argument('--out', type=Path, required=True); a = p.parse_args()
    require(not a.out.exists(), 'Use a new regression directory')
    a.out.mkdir(parents=True)
    import torch
    from transformers import Trainer, TrainerCallback, TrainingArguments, set_seed
    from serviceflow_training.engine.resume_compat import install_resume_tail_fix
    install_resume_tail_fix()

    class Data(torch.utils.data.Dataset):
        def __len__(self): return 78
        def __getitem__(self, i):
            return {'input_ids':torch.tensor([i / 78, i % 7 / 7, 1.]), 'labels':torch.tensor([i % 3 / 3])}

    class Model(torch.nn.Module):
        def __init__(self):
            super().__init__(); self.dropout = torch.nn.Dropout(.1); self.layer = torch.nn.Linear(3, 1)
        def forward(self, input_ids, labels=None):
            output = self.layer(self.dropout(input_ids))
            return {'loss':torch.nn.functional.mse_loss(output, labels), 'logits':output}

    saves = []
    class Policy(TrainerCallback):
        def __init__(self, pause): self.pause = pause; self.requested = False
        def on_step_end(self, args, state, control, **kwargs):
            self.requested |= self.pause and state.global_step >= 2
            return apply_pause('epoch', self.requested, control)
        def on_epoch_end(self, args, state, control, **kwargs):
            return apply_pause('epoch', self.requested, control, epoch_end=True)
        def on_save(self, args, state, control, **kwargs):
            directory = Path(args.output_dir) / f'checkpoint-{state.global_step}'
            # A tiny full model stands in for LoRA; optimizer/RNG/Trainer files are real.
            shutil.copyfile(directory / 'model.safetensors', directory / 'adapter_model.safetensors')
            atomic_json(directory / 'adapter_config.json', {'synthetic_cpu_fixture':True})
            seal_checkpoint(directory, 'cpu-epoch-policy')
            retain_latest_sealed(args.output_dir, directory, 'cpu-epoch-policy')
            saves.append({'step':state.global_step,'epoch':state.epoch,'run':Path(args.output_dir).name})

    def train(name, pause=False, resume=None):
        set_seed(42); model = Model()
        options = TrainingArguments(output_dir=str(a.out / name), use_cpu=True, num_train_epochs=2,
            per_device_train_batch_size=1, gradient_accumulation_steps=16, learning_rate=.001,
            seed=42, data_seed=42, save_strategy='epoch', save_total_limit=None,
            report_to='none', disable_tqdm=True, dataloader_pin_memory=False)
        trainer = Trainer(model=model, args=options, train_dataset=Data(), callbacks=[Policy(pause)])
        trainer.train(resume_from_checkpoint=resume)
        return trainer.state, {k:v.detach().clone() for k,v in model.state_dict().items()}

    reference, weights = train('reference')
    paused, _ = train('resumed', pause=True)
    require(paused.global_step == 5 and paused.epoch == 1., 'Pause saved before epoch boundary')
    checkpoint, rejected = select_checkpoint(a.out / 'resumed', 'latest', 'cpu-epoch-policy')
    require(not rejected, 'Unexpected incomplete state')
    resumed, final = train('resumed', resume=str(checkpoint))
    difference = max((final[k] - weights[k]).abs().max().item() for k in weights)
    require(resumed.global_step == reference.global_step == 10 and difference == 0, 'Resume differs from uninterrupted run')
    require([x.name for x in (a.out / 'resumed').glob('checkpoint-*')] == ['checkpoint-10'], 'Old checkpoint retained')
    require(all(x['epoch'] in [1., 2.] for x in saves), 'Unexpected mid-epoch checkpoint')
    report = {'status':'PASSED','saves':saves,'final_step':10,'max_absolute_difference':difference,
              'latest_only':True,'pause_waits_for_epoch':True}
    atomic_json(a.out / 'verification.json', report); print(report)


if __name__ == '__main__': main()
