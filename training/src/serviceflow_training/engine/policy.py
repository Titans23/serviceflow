"""Checkpoint policy shared by the runner and CPU Trainer regression."""
from serviceflow_training.core.contracts import require


def validate_policy(config):
    strategy = config.get('save_strategy')
    require(strategy in {'steps', 'epoch'}, 'Expected steps or epoch save strategy')
    limit = config.get('save_total_limit')
    require(type(limit) is int and limit >= 1, 'save_total_limit must be positive')
    if strategy == 'steps':
        require(type(config.get('save_steps')) is int and config['save_steps'] > 0,
                'save_steps must be a positive optimizer-step count')
        require(limit >= 2, 'Legacy step strategy requires at least two checkpoints')
    else:
        require(limit == 1, 'Epoch policy retains the latest complete checkpoint only')
        require(not config.get('load_best_model_at_end'), 'Latest-only policy cannot retain a second best checkpoint')


def apply_pause(strategy, requested, control, *, epoch_end=False):
    if requested and (strategy == 'steps' or epoch_end):
        control.should_save = True
        control.should_training_stop = True
    return control
