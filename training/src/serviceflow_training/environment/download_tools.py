"""Download project-local JDK/Maven and pin official Docker image manifests."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import requests
from serviceflow_training.core.contracts import ROOT, write_json, sha

OUT=ROOT/'runtime-data/training/integration-tools'

def file(url,name,expected,algorithm='sha256'):
    dest=OUT/name
    if dest.exists():
        digest=hashlib.new(algorithm,dest.read_bytes()).hexdigest()
        if digest==expected:return {'file':name,'url':url,'checksum':expected,'algorithm':algorithm,'reused':True}
    r=requests.get(url,stream=True,timeout=(20,120));r.raise_for_status()
    partial=dest.with_suffix(dest.suffix+'.partial');h=hashlib.new(algorithm)
    with partial.open('wb') as f:
        for chunk in r.iter_content(1024*1024):f.write(chunk);h.update(chunk)
    assert h.hexdigest()==expected,'Download checksum mismatch'
    partial.replace(dest)
    return {'file':name,'url':url,'checksum':expected,'algorithm':algorithm,'sha256':sha(dest)}

def jdk():
    name='Alibaba_Dragonwell_Standard_21.0.12.0.12.8_x64_linux.tar.gz'
    url='https://dragonwell.oss-cn-shanghai.aliyuncs.com/21.0.12.0.12%2B8/'+name
    return file(url,name,'16842c9422323c3e0f047e543a3183997c39723061e4eff2816ff9a93baa0694')

def maven():
    name='apache-maven-3.9.11-bin.tar.gz';path='org/apache/maven/apache-maven/3.9.11/'+name
    r=requests.get('https://repo.maven.apache.org/maven2/'+path+'.sha512',timeout=30);r.raise_for_status()
    checksum=r.text.strip().split()[0];assert len(checksum)==128
    return file('https://maven.aliyun.com/repository/central/'+path,name,checksum,'sha512')

def image(name,tag):
    repo=name if '/' in name else 'library/'+name
    r=requests.get('https://auth.docker.io/token',params={'service':'registry.docker.io','scope':f'repository:{repo}:pull'},timeout=30);r.raise_for_status()
    headers={'Authorization':'Bearer '+r.json()['token'],'Accept':'application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json'}
    r=requests.get(f'https://registry-1.docker.io/v2/{repo}/manifests/{tag}',headers=headers,timeout=30);r.raise_for_status()
    index=r.json();digest=r.headers['Docker-Content-Digest']
    assert 'sha256:'+hashlib.sha256(r.content).hexdigest()==digest
    if 'manifests' in index:
        digest=next(x['digest'] for x in index['manifests'] if x['platform']['architecture']=='amd64' and x['platform']['os']=='linux')
    result={'original':name+':'+tag,'platform':'linux/amd64','digest':digest,'mirror':'m.daocloud.io/docker.io/'+repo+'@'+digest,'official_index':index}
    write_json(OUT/(name.replace('/','-')+'-image.json'),result);print(name,digest,flush=True);return result

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    with ThreadPoolExecutor(max_workers=5) as pool:
        jobs={name:pool.submit(fn) for name,fn in [('jdk',jdk),('maven',maven),('mysql',lambda:image('mysql','8.4')),('redis',lambda:image('redis','7.4-alpine')),('rabbitmq',lambda:image('rabbitmq','4-management'))]}
        records={}
        for name,future in jobs.items():
            try:records[name]=future.result();print(name,'verified',flush=True)
            except Exception as e:records[name]={'error':str(e)};print(name,type(e).__name__,str(e),flush=True)
    write_json(OUT/'downloads.json',records)
    assert all('error' not in v for v in records.values())
