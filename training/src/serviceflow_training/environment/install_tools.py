"""Extract checked project-local JDK/Maven archives inside WSL (no system install)."""
from pathlib import Path
import hashlib
import tarfile
from serviceflow_training.core.contracts import ROOT, read_json, write_json

out=Path('/home/titans/tools/serviceflow-integration-v1')
source=ROOT/'runtime-data/training/integration-tools'
records=read_json(source/'downloads.json')
out.mkdir(parents=True,exist_ok=True)
for key in ['jdk','maven']:
    entry=records[key];archive=source/entry['file']
    assert hashlib.new(entry['algorithm'],archive.read_bytes()).hexdigest()==entry['checksum']
    dest=out/key
    if not dest.exists():
        dest.mkdir()
        with tarfile.open(archive) as tar:tar.extractall(dest,filter='data')
    dirs=[p for p in dest.iterdir() if p.is_dir()]
    assert len(dirs)==1
    records[key]['installed_path']=str(dirs[0])
write_json(source/'installed.json',records)
print({key:records[key]['installed_path'] for key in ['jdk','maven']})
