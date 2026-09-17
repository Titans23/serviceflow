"""Narrow in-process correction for the pinned Transformers 4.52.4 resume tail.

After skip_first_batches, step is local to the remaining iterator. The final
accumulation flush must include steps_skipped when comparing to steps_in_epoch.
No installed files are changed. Unknown source/version fails closed.
"""
import hashlib
from importlib.metadata import version
import inspect
import textwrap

from serviceflow_training.core.contracts import require

ORIGINAL_SHA256 = '67dd52e45a1869e68c0dd7abd563bc652185bcaa70a4ad4d8c980c51689a4f74'
OLD = 'or (step + 1) == steps_in_epoch'
NEW = 'or (step + 1 + steps_skipped) == steps_in_epoch'


def install_resume_tail_fix():
    from transformers import Trainer
    require(version('transformers') == '4.52.4', 'Resume compatibility correction only supports Transformers 4.52.4')
    if hasattr(Trainer, '_serviceflow_resume_tail_fix'):
        return Trainer._serviceflow_resume_tail_fix
    original = Trainer._inner_training_loop
    source = inspect.getsource(original)
    require(hashlib.sha256(source.encode()).hexdigest() == ORIGINAL_SHA256,
            'Trainer loop source changed; review compatibility correction before running')
    require(source.count(OLD) == 1 and not original.__code__.co_freevars, 'Unexpected Trainer loop structure')
    corrected = textwrap.dedent(source.replace(OLD, NEW))
    namespace = dict(original.__globals__)
    # Only compile the hash-verified installed implementation with this single substitution.
    exec(compile(corrected, '<serviceflow-transformers-4.52.4-resume-tail>', 'exec'), namespace)
    Trainer._inner_training_loop = namespace['_inner_training_loop']
    metadata = {'name': 'transformers-4.52.4-resume-tail-flush', 'original_source_sha256': ORIGINAL_SHA256,
                'corrected_source_sha256': hashlib.sha256(corrected.encode()).hexdigest(),
                'old_expression': OLD, 'new_expression': NEW, 'installed_files_modified': False}
    Trainer._serviceflow_resume_tail_fix = metadata
    return metadata
