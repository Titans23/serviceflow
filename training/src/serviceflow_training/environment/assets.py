"""Download pinned public assets, preferring ModelScope / hf-mirror.com."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import quote

import requests

ROOT = Path(__file__).resolve().parents[4]


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def fetch_json(url):
    response = requests.get(url, timeout=60)
    response.raise_for_status()
    return response.json()


def model():
    from modelscope.hub.api import HubApi
    model_id = "Qwen/Qwen3-8B"
    revision = "26028140be3ee69b82b1d1450179ab71bb1121b9"
    original_revision = "b968826d9c46dd6066d109eabc6255188de91218"
    output = ROOT / "runtime-data/training/models/Qwen3-8B"
    records = ROOT / "runtime-data/training/assets"
    files = HubApi().get_model_files(model_id, revision=revision, recursive=True)
    save(records / "modelscope-files.json", files)
    original = fetch_json(f"https://hf-mirror.com/api/models/{model_id}/revision/{original_revision}?blobs=true")
    save(records / "huggingface-model-metadata.json", original)
    print("Downloading pinned ModelScope Qwen3-8B to", output, flush=True)
    def download(item):
        if item.get("Type") == "tree":
            return
        path = (output / item["Path"]).resolve()
        if not path.is_relative_to(output.resolve()):
            raise ValueError("Unsafe model path")
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists() and digest(path) == item["Sha256"]:
            return
        temp = path.with_name(path.name + ".partial")
        url = f"https://modelscope.cn/api/v1/models/{model_id}/repo?Revision={revision}&FilePath={quote(item['Path'])}"
        for attempt in range(5):
            try:
                offset = temp.stat().st_size if temp.exists() else 0
                if offset != item["Size"]:
                    with requests.get(url, headers={"Range": f"bytes={offset}-"} if offset else {}, stream=True, timeout=(30, 180)) as response:
                        response.raise_for_status()
                        if offset and response.status_code == 206 and not response.headers.get("Content-Range", "").startswith(f"bytes {offset}-"):
                            raise ValueError("Unexpected resume range")
                        with temp.open("ab" if offset and response.status_code == 206 else "wb") as stream:
                            for chunk in response.iter_content(4 * 1024 * 1024):
                                stream.write(chunk)
                if temp.stat().st_size != item["Size"] or digest(temp) != item["Sha256"]:
                    temp.unlink()
                    raise ValueError("Downloaded size or SHA mismatch")
                temp.replace(path)
                print("Downloaded and verified", item["Path"], flush=True)
                return
            except Exception as error:
                print("Retry", item["Path"], attempt + 1, type(error).__name__, flush=True)
                if attempt == 4:
                    raise
                time.sleep(2 ** attempt)
    with ThreadPoolExecutor(max_workers=5) as pool:
        list(pool.map(download, files))
    checked = {}
    for item in files:
        if item.get("Type") == "tree":
            continue
        name, expected = item["Path"], item.get("Sha256")
        path = (output / name).resolve()
        if not path.is_relative_to(output.resolve()):
            raise ValueError("Unsafe model path")
        print("Verifying", name, flush=True)
        actual = digest(path)
        if expected and actual != expected:
            raise ValueError("ModelScope SHA mismatch: " + name)
        checked[name] = actual
    upstream = {x["rfilename"]: x for x in original["siblings"]}
    weight_matches = {}
    for name, value in checked.items():
        if name.endswith(".safetensors"):
            upstream_sha = upstream.get(name, {}).get("lfs", {}).get("sha256")
            if upstream_sha != value:
                raise ValueError("Original model LFS SHA missing or different: " + name)
            weight_matches[name] = True
    save(records / "model-files-sha256.json", checked)
    save(records / "model-download.json", {"status": "VERIFIED", "downloaded_at": datetime.now(timezone.utc).isoformat(),
        "model_id": model_id, "download_provider": "modelscope", "download_revision": revision,
        "original_revision": original_revision, "original_metadata_endpoint": "https://hf-mirror.com",
        "local_path_windows": str(output), "local_path_linux": "/mnt/d/projects/serviceflow/runtime-data/training/models/Qwen3-8B",
        "original_weight_hash_matches": weight_matches, "files_sha256": checked,
        "license_file": str(output / "LICENSE"), "note": "Weight shards match original LFS hashes; small-file parity is checked separately before runtime use."})
    print("MODEL DOWNLOAD VERIFIED", flush=True)




if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("asset", choices=("model",))
    args = parser.parse_args()
    model()
