"""Preserve small, immutable epoch adapters before full checkpoint rotation."""
from pathlib import Path
import shutil
from serviceflow_training.engine.checkpoints import atomic_json
from serviceflow_training.core.contracts import read_json, require
from serviceflow_training.core.hashing import sha

def preserve_epoch_adapter(checkpoint, run_dir, receipt):
    checkpoint=Path(checkpoint).resolve();run_dir=Path(run_dir).resolve()
    require(checkpoint.parent==run_dir/'checkpoints','Unexpected checkpoint root')
    state=read_json(checkpoint/'trainer_state.json')
    epoch=state['epoch']
    require(abs(epoch-round(epoch))<1e-6 and epoch>=1,'Adapter must be a completed epoch')
    target=run_dir/'epoch-adapters'/f'epoch-{round(epoch)}'
    names=['adapter_config.json','adapter_model.safetensors']
    hashes={n:receipt['files_sha256'][n] for n in names}
    for n,h in hashes.items():require(sha(checkpoint/n)==h,'Adapter source changed')
    if target.exists():
        m=read_json(target/'epoch-adapter.json')
        require(m['contract_sha256']==receipt['contract_sha256'] and m['files_sha256']==hashes,'Existing epoch adapter differs')
        for n,h in hashes.items():require(sha(target/n)==h,'Saved epoch adapter corrupted')
        return str(target)
    pending=target.with_name(target.name+'.pending')
    require(not pending.exists(),'Incomplete adapter copy preserved; inspect before retry')
    pending.mkdir(parents=True)
    for n,h in hashes.items():
        shutil.copyfile(checkpoint/n,pending/n);require(sha(pending/n)==h,'Adapter copy mismatch')
    atomic_json(pending/'epoch-adapter.json',{'epoch':round(epoch),'global_step':state['global_step'],
       'source_run':str(run_dir),'source_checkpoint':str(checkpoint),'contract_sha256':receipt['contract_sha256'],
       'files_sha256':hashes,'resumable':False,'validation_required':True})
    pending.rename(target)
    return str(target)
