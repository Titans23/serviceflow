"""Use the project-local verified Java 21 and Maven; preserve system Java settings."""
import os
from pathlib import Path
import subprocess
import sys
from serviceflow_training.core.contracts import ROOT, read_json

tools=read_json(ROOT/'runtime-data/training/integration-tools/installed.json')
env=dict(os.environ,JAVA_HOME=tools['jdk']['installed_path'])
env['PATH']=tools['jdk']['installed_path']+'/bin:'+env['PATH']
agent=Path('/home/titans/tools/serviceflow-integration-v1/m2/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar')
if agent.exists():
    # Explicit test instrumentation avoids this WSL/JDK's failed self-attach.
    # JAVA_TOOL_OPTIONS is scoped to Maven and its test forks, not application serving.
    env['JAVA_TOOL_OPTIONS']=(env.get('JAVA_TOOL_OPTIONS','')+' -javaagent:'+str(agent)).strip()
cmd=[tools['maven']['installed_path']+'/bin/mvn','-B','-s',str(ROOT/'training/configs/maven-settings.xml'),
     '-Dmaven.repo.local=/home/titans/tools/serviceflow-integration-v1/m2','-f',str(ROOT/'apps/serviceflow-server/pom.xml'),*sys.argv[1:]]
raise SystemExit(subprocess.call(cmd,env=env,cwd=ROOT))
