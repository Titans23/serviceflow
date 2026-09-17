"""Full SHA-256 reads, using the native host for large Windows-backed WSL files.

No hash cache or skipped verification. Small files and Linux files use hashlib.
The quoted PowerShell script is UTF-16 encoded; filenames cannot become commands.
"""
import base64
from pathlib import Path
import re
import shutil
import subprocess
from serviceflow_training.core.contracts import sha as portable_sha

def sha(path):
    path = Path(path).resolve()
    match = re.fullmatch(r'/mnt/([a-z])/(.+)', str(path))
    executable = shutil.which('powershell.exe') if match and path.stat().st_size >= 64 * 1024**2 else None
    if not executable:
        return portable_sha(path)
    before = path.stat()
    windows_path = match[1].upper() + ':\\' + match[2].replace('/', '\\')
    quoted = "'" + windows_path.replace("'", "''") + "'"
    script = ("$ErrorActionPreference='Stop'; $stream=[IO.File]::OpenRead(" + quoted + "); "
              "$algorithm=[Security.Cryptography.SHA256]::Create(); try { "
              "[BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() "
              "} finally { $stream.Dispose(); $algorithm.Dispose() }")
    encoded = base64.b64encode(script.encode('utf-16le')).decode('ascii')
    result = subprocess.run([executable, '-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', encoded],
                            capture_output=True, timeout=600)
    if result.returncode:
        raise RuntimeError('Native SHA-256 failed: ' + result.stderr.decode('gb18030', errors='replace')[:800])
    digest = result.stdout.decode('ascii').strip()
    after = path.stat()
    if not re.fullmatch('[0-9a-f]{64}', digest) or (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ValueError('File changed during SHA-256 verification or invalid host response')
    return digest
