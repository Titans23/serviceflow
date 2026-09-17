"""Pinned Trainer resume corrections for partial accumulation and sampler epoch."""
import hashlib
from importlib.metadata import version
import inspect
import textwrap

from serviceflow_training.core.contracts import require
from serviceflow_training.engine.resume_constants import ORIGINAL_SHA256, OLD, NEW


def install_resume_tail_fix():
    from transformers import Trainer
    require(version('transformers') == '4.52.4' and version('accelerate') == '1.7.0',
            'Resume compatibility corrections require Transformers 4.52.4 / Accelerate 1.7.0')
    if hasattr(Trainer, '_serviceflow_resume_epoch_fix'):
        return Trainer._serviceflow_resume_epoch_fix
    original = Trainer._inner_training_loop
    source = inspect.getsource(original)
    require(hashlib.sha256(source.encode()).hexdigest() == ORIGINAL_SHA256,
            'Trainer source changed; review compatibility corrections')
    skip = 'epoch_dataloader = skip_first_batches(epoch_dataloader, steps_trained_in_current_epoch)'
    # Accelerate returns a new DataLoaderShard with iteration=0. Restore its epoch
    # before iteration, otherwise later-epoch resume repeats epoch-zero ordering.
    restored = skip + '\n                if hasattr(epoch_dataloader, "set_epoch"):\n                    epoch_dataloader.set_epoch(epoch)'
    require(source.count(OLD) == source.count(skip) == 1 and not original.__code__.co_freevars,
            'Unexpected Trainer loop structure')
    corrected = textwrap.dedent(source.replace(OLD, NEW).replace(skip, restored))
    namespace = dict(original.__globals__)
    exec(compile(corrected, '<serviceflow-transformers-4.52.4-resume-tail-and-epoch>', 'exec'), namespace)
    Trainer._inner_training_loop = namespace['_inner_training_loop']
    metadata = {'name': 'transformers-4.52.4-accelerate-1.7.0-resume-tail-and-epoch',
                'original_source_sha256': ORIGINAL_SHA256,
                'corrected_source_sha256': hashlib.sha256(corrected.encode()).hexdigest(),
                'fixes': ['include skipped batches in final gradient flush', 'restore skipped dataloader sampler epoch'],
                'installed_files_modified': False}
    Trainer._serviceflow_resume_epoch_fix = metadata
    return metadata
