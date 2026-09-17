"""Read-only hardware/environment inventory. No installation or downloads."""
import argparse
import ctypes
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[4]
PACKAGES = ("torch", "transformers", "datasets", "peft", "accelerate", "trl", "llamafactory", "vllm", "modelscope")


def command(args):
    try:
        result = subprocess.run(args, capture_output=True, timeout=25)
        def decode(value):
            return value.decode("utf-16-le" if b"\x00" in value else "utf-8", errors="replace").strip()
        return {"exit_code": result.returncode, "stdout": decode(result.stdout), "stderr": decode(result.stderr)}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"error": type(error).__name__ + ": " + str(error)}


def physical_memory_gib():
    if sys.platform == "win32":
        class MemoryStatus(ctypes.Structure):
            _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
                (name, ctypes.c_ulonglong) for name in
                ("total_phys", "avail_phys", "total_page", "avail_page", "total_virtual", "avail_virtual", "extended")]
        state = MemoryStatus()
        state.length = ctypes.sizeof(state)
        if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(state)):
            raise OSError("GlobalMemoryStatusEx failed")
        return round(state.total_phys / 2**30, 2)
    return round(os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE") / 2**30, 2)


def collect():
    packages = {}
    for name in PACKAGES:
        try:
            packages[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            packages[name] = None
    report = {
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "platform": platform.platform(), "python": sys.version, "python_executable": sys.executable,
        "git": command(["git", "-C", str(ROOT), "rev-parse", "HEAD"]),
        "git_status": command(["git", "-C", str(ROOT), "status", "--short"]),
        "packages": packages,
        "disk_free_gib": round(shutil.disk_usage(ROOT).free / 2**30, 2),
        "nvidia_smi": command(["nvidia-smi", "--query-gpu=name,memory.total,memory.used,memory.free,driver_version,utilization.gpu", "--format=csv,noheader"]),
    }
    try:
        report["ram_gib"] = physical_memory_gib()
    except (OSError, AttributeError, ValueError) as error:
        report["ram_error"] = str(error)
    # A subprocess bounds import/driver probing and doesn't touch existing environments.
    report["torch_probe"] = command([sys.executable, "-c",
        'import torch,json; d={"torch":torch.__version__,"cuda_build":torch.version.cuda,"cuda_available":torch.cuda.is_available()}; '
        'd.update({"gpu":torch.cuda.get_device_name(0),"bf16_supported":torch.cuda.is_bf16_supported()} if torch.cuda.is_available() else {}); print(json.dumps(d))'])
    if sys.platform == "win32":
        report["wsl"] = command(["wsl", "--list", "--verbose"])
    blockers = []
    if platform.system() != "Linux":
        blockers.append("正式训练用 Linux/WSL2 环境尚未验证；Windows 识别 GPU 不等于 Linux 就绪")
    for name in ("torch", "transformers", "datasets", "peft", "accelerate", "llamafactory"):
        if not packages[name]:
            blockers.append("当前 Python 缺少 " + name)
    try:
        probe = json.loads(report["torch_probe"].get("stdout", ""))
        if not probe.get("cuda_available") or not probe.get("bf16_supported"):
            blockers.append("当前 PyTorch 未确认 CUDA 和 BF16 均可用")
    except (ValueError, TypeError):
        blockers.append("PyTorch/CUDA 探测未成功")
    if report.get("ram_gib", 0) < 64:
        blockers.append("未确认主机内存达到规划目标 64 GiB")
    if report["disk_free_gib"] < 150:
        blockers.append("工作区磁盘不足规划预留 150 GiB")
    report["blockers"] = blockers
    report["preflight_passed"] = not blockers
    report["training_validated"] = False
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = collect()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["preflight_passed"] else 2


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    sys.exit(main())
