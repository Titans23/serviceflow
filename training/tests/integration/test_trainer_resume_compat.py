"""Real fixed-Trainer CPU regression: partial accumulation, RNG and resumed updates."""
import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "src"))

from serviceflow_training.engine.checkpoints import atomic_json
from serviceflow_training.core.contracts import require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--epoch-fix', action='store_true')
    args = parser.parse_args()
    require(not args.out.exists(), 'Use a new regression output directory')
    args.out.mkdir(parents=True)
    import torch
    from transformers import Trainer, TrainerCallback, TrainingArguments, set_seed
    if args.epoch_fix:
        from serviceflow_training.engine.resume_compat import install_resume_tail_fix
    else:
        from serviceflow_training.engine.resume_constants import install_resume_tail_fix
    class Fixture(torch.utils.data.Dataset):
        def __init__(self, count=78):
            self.count = count
        def __len__(self):
            return self.count
        def __getitem__(self, index):
            return {'input_ids': torch.tensor([index / 78, (index % 7) / 7, 1.]),
                    'labels': torch.tensor([index % 3 / 3])}
    class Tiny(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.dropout = torch.nn.Dropout(.1)
            self.linear = torch.nn.Linear(3, 1)
        def forward(self, input_ids, labels=None):
            logits = self.linear(self.dropout(input_ids))
            return {'loss': torch.nn.functional.mse_loss(logits, labels), 'logits': logits}
    class Pause(TrainerCallback):
        def __init__(self, stop_step=2):
            self.stop_step = stop_step
        def on_step_end(self, args, state, control, **kwargs):
            if state.global_step == self.stop_step:
                control.should_save = True
                control.should_training_stop = True
            return control
    def train(name, resume=None, pause=False, epochs=1, stop_step=2, count=78):
        set_seed(42)
        model = Tiny()
        options = TrainingArguments(output_dir=str(args.out / name), use_cpu=True, num_train_epochs=epochs,
            per_device_train_batch_size=1, gradient_accumulation_steps=16, learning_rate=.001,
            lr_scheduler_type='cosine', seed=42, data_seed=42, report_to='none', save_steps=1,
            save_total_limit=3, logging_steps=1, disable_tqdm=True, dataloader_pin_memory=False)
        trainer = Trainer(model=model, args=options, train_dataset=Fixture(count), callbacks=[Pause(stop_step)] if pause else [])
        trainer.train(resume_from_checkpoint=resume)
        return trainer.state.global_step, {key: value.detach().clone() for key, value in model.state_dict().items()}
    _, reference = train('reference')
    train('unfixed', pause=True)
    unfixed_step, _ = train('unfixed', str(args.out / 'unfixed/checkpoint-2'))
    require(unfixed_step == 4, 'Fixture did not reproduce the pinned resume-tail bug')
    metadata = install_resume_tail_fix()
    train('fixed', pause=True)
    fixed_step, fixed = train('fixed', str(args.out / 'fixed/checkpoint-2'))
    differences = {key: (fixed[key] - reference[key]).abs().max().item() for key in fixed}
    require(fixed_step == 5, 'Corrected resume did not finish the final partial update')
    require(all(value == 0 for value in differences.values()), 'Corrected resume differs from uninterrupted CPU training')
    extra = []
    for count, epochs, stop_step in [(80, 1, 2), (78, 2, 6), (78, 2, 5)]:
        name = f'rows-{count}-epochs-{epochs}-pause-{stop_step}'
        target_step, target_weights = train(name + '-reference', count=count, epochs=epochs)
        train(name, count=count, epochs=epochs, pause=True, stop_step=stop_step)
        resumed_step, resumed_weights = train(name, str(args.out / name / f'checkpoint-{stop_step}'), count=count, epochs=epochs)
        difference = max((resumed_weights[key] - target_weights[key]).abs().max().item() for key in target_weights)
        require(resumed_step == target_step and difference == 0, 'Additional resume boundary failed: ' + name)
        extra.append({'rows': count, 'epochs': epochs, 'pause_step': stop_step,
                      'final_step': resumed_step, 'max_absolute_difference': difference})
    report = {'status': 'PASSED', 'unfixed_final_step': unfixed_step, 'fixed_final_step': fixed_step,
              'reference_step': 5, 'dropout_probability': .1, 'max_absolute_tensor_differences': differences,
              'bitwise_equal_to_uninterrupted': True, 'additional_boundaries': extra, 'compatibility': metadata}
    atomic_json(args.out / 'verification.json', report)
    print(report)


if __name__ == '__main__':
    main()
