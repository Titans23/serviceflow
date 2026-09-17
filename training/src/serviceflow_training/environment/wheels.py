"""Download the exact Linux wheels in a uv-generated pylock using HTTP/1.1.

Useful when a mirror throttles multiplexed uv downloads. Hashes are required.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import time
import tomllib
from urllib.parse import urlsplit, unquote
import requests
from packaging.tags import cpython_tags, compatible_tags
from packaging.utils import parse_wheel_filename


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def download(wheel, output):
    url = wheel["url"]
    name = unquote(urlsplit(url).path.rsplit("/", 1)[1])
    assert name.endswith(".whl") and "/" not in name and "\\" not in name
    path = output / name
    expected = wheel["hashes"]["sha256"]
    if path.is_file() and sha(path) == expected:
        return
    partial = path.with_suffix(".whl.partial")
    for attempt in range(5):
        try:
            offset = partial.stat().st_size if partial.exists() else 0
            if offset != wheel.get("size"):
                with requests.get(url, headers={"Range": f"bytes={offset}-"} if offset else {}, stream=True, timeout=(30, 120)) as response:
                    response.raise_for_status()
                    if offset and response.status_code == 206:
                        assert response.headers["Content-Range"].startswith(f"bytes {offset}-")
                    with partial.open("ab" if offset and response.status_code == 206 else "wb") as stream:
                        for chunk in response.iter_content(1024 * 1024):
                            stream.write(chunk)
            if (wheel.get("size") is not None and partial.stat().st_size != wheel["size"]) or sha(partial) != expected:
                partial.unlink()
                raise ValueError("Wheel hash mismatch")
            partial.replace(path)
            print("VERIFIED", name, flush=True)
            return
        except Exception as error:
            print("RETRY", name, attempt + 1, type(error).__name__, flush=True)
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lock", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--endpoint", choices=("source", "pypi"), default="source")
    parser.add_argument("--exclude-lock", action="append", type=Path, default=[])
    args = parser.parse_args()
    lock = tomllib.loads(args.lock.read_text(encoding="utf-8"))
    excluded = {x["hashes"]["sha256"] for path in args.exclude_lock for p in tomllib.loads(path.read_text(encoding="utf-8"))["packages"] for x in p.get("wheels", [])}
    platforms = [f"manylinux_2_{minor}_x86_64" for minor in range(43, 4, -1)] + ["manylinux2014_x86_64", "manylinux2010_x86_64", "manylinux1_x86_64", "linux_x86_64"]
    tags = list(cpython_tags((3, 12), platforms=platforms)) + list(compatible_tags((3, 12), "cp312", platforms=platforms))
    ranks = {tag: index for index, tag in enumerate(tags)}
    def wheel_rank(wheel):
        parsed = parse_wheel_filename(unquote(urlsplit(wheel["url"]).path.rsplit("/", 1)[1]))
        return min((ranks[t] for t in parsed[3] if t in ranks), default=999999)
    wheels = []
    for package in lock["packages"]:
        choices = package.get("wheels", [])
        if not choices and (package.get("sdist") or package.get("directory")):
            print("SOURCE INSTALL REQUIRED", package["name"], flush=True)
            continue
        choices = sorted(choices, key=wheel_rank)
        if not choices or wheel_rank(choices[0]) == 999999:
            raise ValueError("No compatible Linux CPython 3.12 wheel: " + package["name"])
        if choices[0]["hashes"]["sha256"] not in excluded:
            wheels.append(choices[0])
    args.output.mkdir(parents=True, exist_ok=True)
    if args.endpoint == "pypi":
        for wheel in wheels:
            parts = urlsplit(wheel["url"])
            assert parts.netloc == "pypi.tuna.tsinghua.edu.cn" and parts.path.startswith("/packages/")
            wheel["url"] = "https://files.pythonhosted.org" + parts.path
    manifest = args.output / (args.lock.stem + "-sources.json")
    manifest.write_text(json.dumps(wheels, default=str, indent=2) + "\n", encoding="utf-8")
    with ThreadPoolExecutor(max_workers=5) as pool:
        list(pool.map(lambda wheel: download(wheel, args.output), sorted(wheels, key=lambda x: -x.get("size", 0))))
    print("ALL WHEELS VERIFIED", flush=True)
