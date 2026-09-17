"""Start an isolated localhost server, verify two responses and SSE, then stop it.

Installation smoke test only; this is not the module or system quality baseline.
"""
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import Request, urlopen

from serviceflow_training.core.contracts import INTENTS, grader_output, prompt_contract

ROOT = Path(__file__).resolve().parents[4]
REPORT = ROOT / "runtime-data/training/installation-check"
MODEL = ROOT / "runtime-data/training/models/Qwen3-8B"
SERVER_LOG = ROOT / "runtime-data/training/logs/vllm-smoke-server.log"


def main():
    key = secrets.token_urlsafe(32)
    env = dict(os.environ, VLLM_API_KEY=key, HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", VLLM_NO_USAGE_STATS="1")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    url = f"http://127.0.0.1:{port}"
    command = [sys.executable, "-m", "vllm.entrypoints.openai.api_server", "--model", str(MODEL),
               "--served-model-name", "serviceflow-qwen3-8b-base", "--host", "127.0.0.1", "--port", str(port),
               "--dtype", "bfloat16", "--max-model-len", "4096", "--gpu-memory-utilization", "0.65",
               "--max-num-seqs", "2", "--enforce-eager", "--generation-config", "vllm",
               "--chat-template", str(ROOT / "runtime-data/training/qwen3-nonthinking.jinja")]
    REPORT.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    SERVER_LOG.parent.mkdir(parents=True, exist_ok=True)
    with SERVER_LOG.open("w") as log:
        process = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + 600
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError(f"vLLM exited; inspect {SERVER_LOG}")
                try:
                    with urlopen(url + "/health", timeout=2) as response:
                        if response.status == 200:
                            break
                except (URLError, TimeoutError):
                    time.sleep(2)
            else:
                raise TimeoutError("Server startup timed out")
            startup_seconds = time.monotonic() - started
            prompts = prompt_contract()["prompts"]
            cases = [("intent", "请查询我的订单物流进度"), ("grader", 'query: 保修需要什么凭证？\ncandidates: []'), ("chat", "你好",)]
            results = []
            for task, user in cases:
                streaming = task == "chat"
                payload = {"model": "serviceflow-qwen3-8b-base", "messages": [{"role": "system", "content": prompts[task]}, {"role": "user", "content": user}], "temperature": 0.1, "top_p": 1.0, "max_tokens": 128, "seed": 42, "stream": streaming}
                request = Request(url + "/v1/chat/completions", data=json.dumps(payload).encode(), headers={"Content-Type": "application/json", "Authorization": "Bearer " + key})
                before = time.monotonic()
                first = None
                with urlopen(request, timeout=90) as response:
                    if streaming:
                        text, done, events = "", False, 0
                        for line in response:
                            if not line.startswith(b"data: "):
                                continue
                            value = line[6:].strip()
                            if value == b"[DONE]":
                                done = True
                                break
                            event = json.loads(value)
                            events += 1
                            content = event["choices"][0]["delta"].get("content", "") or ""
                            if content and first is None:
                                first = time.monotonic() - before
                            text += content
                        assert done and events and text, "Invalid/empty SSE response"
                    else:
                        value = json.load(response)
                        text = value["choices"][0]["message"]["content"]
                        assert value["choices"][0]["finish_reason"] == "stop"
                assert "<think>" not in text and "</think>" not in text
                if task == "intent":
                    assert text in INTENTS
                if task == "grader":
                    grader_output(text, [])
                results.append({"task": task, "output": text, "stream": streaming, "elapsed_seconds": time.monotonic() - before, "first_content_seconds": first})
            report = {"status": "PASS", "scope": "INSTALLATION_SMOKE_ONLY_NOT_QUALITY_BASELINE", "startup_seconds": startup_seconds, "results": results, "server_left_running": False, "business_backend_connected": False}
        finally:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)
    (REPORT / "vllm-smoke.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
