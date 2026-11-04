"""Independent, persistent local payment-channel simulator. No real payment integration."""
import hmac
import json
import os
import sqlite3
import threading
import time
import urllib.request
import uuid
from decimal import Decimal, InvalidOperation
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DB = os.environ.get("CHANNEL_DB", "/data/channel.sqlite")
TOKEN = os.environ.get("CHANNEL_TOKEN", "")
CALLBACK = os.environ.get("CALLBACK_URL", "")
MODES = {"SUCCESS", "DROP_RESPONSE_ONCE", "DROP_CALLBACK", "DUPLICATE_CALLBACK", "UNAVAILABLE", "PENDING", "FAIL_ONCE", "ACCEPT_THEN_HANG"}
if not TOKEN:
    raise RuntimeError("CHANNEL_TOKEN is required")
os.makedirs(os.path.dirname(os.path.abspath(DB)), exist_ok=True)


def database():
    conn = sqlite3.connect(DB, timeout=5)
    conn.row_factory = sqlite3.Row
    return conn


with database() as conn:
    conn.executescript("""
    PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS controls(refund_id TEXT PRIMARY KEY,mode TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS refunds(
      refund_id TEXT PRIMARY KEY,attempt_no INTEGER NOT NULL,amount TEXT NOT NULL,status TEXT NOT NULL,
      reason TEXT NOT NULL,mode TEXT NOT NULL,ready_at REAL NOT NULL,notified INTEGER NOT NULL DEFAULT 0,
      response_dropped INTEGER NOT NULL DEFAULT 0,accept_count INTEGER NOT NULL DEFAULT 1,
      effective_refunds INTEGER NOT NULL DEFAULT 0);
    """)


def receipt(row):
    return {"refundId": row["refund_id"], "attemptNo": row["attempt_no"], "amount": row["amount"],
            "status": row["status"], "reason": row["reason"]}


def valid_id(value):
    return str(uuid.UUID(value))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def send(self, status, body):
        payload = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def authorized(self):
        if not hmac.compare_digest(self.headers.get("X-Refund-Token", "").encode(), TOKEN.encode()):
            self.send(403, {"error": "invalid channel credential"})
            return False
        return True

    def body(self):
        size = int(self.headers.get("Content-Length", "0"))
        if size < 1 or size > 4096:
            raise ValueError("invalid request size")
        return json.loads(self.rfile.read(size), parse_float=Decimal)

    def do_GET(self):
        if self.path == "/health":
            return self.send(200, {"status": "UP", "mode": "local-simulator"})
        if not self.authorized():
            return
        try:
            identifier = valid_id(self.path.split("/")[-1])
            with database() as conn:
                control = conn.execute("SELECT mode FROM controls WHERE refund_id=?", (identifier,)).fetchone()
                row = conn.execute("SELECT * FROM refunds WHERE refund_id=?", (identifier,)).fetchone()
            if self.path.startswith("/refunds/"):
                if control and control["mode"] == "UNAVAILABLE":
                    return self.send(503, {"error": "injected query outage"})
                return self.send(200, receipt(row)) if row else self.send(404, {"error": "not accepted"})
            if self.path.startswith("/inspect/"):
                return self.send(200, dict(row) if row else {"effective_refunds": 0, "accept_count": 0})
            self.send(404, {"error": "unknown path"})
        except (ValueError, IndexError):
            self.send(400, {"error": "invalid refund id"})

    def do_POST(self):
        if not self.authorized():
            return
        try:
            data = self.body()
            if self.path.startswith("/control/"):
                identifier = valid_id(self.path.split("/")[-1])
                mode = data["mode"]
                if mode not in MODES:
                    raise ValueError("unknown mode")
                with database() as conn:
                    conn.execute("INSERT INTO controls VALUES(?,?) ON CONFLICT(refund_id) DO UPDATE SET mode=excluded.mode", (identifier, mode))
                    conn.execute("UPDATE refunds SET mode=?,ready_at=? WHERE refund_id=? AND status='ACCEPTED'",
                                 (mode, time.time() + (86400 if mode == "PENDING" else 0.5), identifier))
                return self.send(200, {"mode": mode})
            if self.path != "/refunds":
                return self.send(404, {"error": "unknown path"})
            identifier = valid_id(data["refundId"])
            amount = Decimal(str(data["amount"]))
            attempt = int(data["attemptNo"])
            if not amount.is_finite() or amount < 0 or amount != amount.quantize(Decimal("0.01")) or attempt < 1:
                raise ValueError("invalid amount or attempt")
            amount = format(amount, ".2f")
            drop = False
            with database() as conn:
                conn.execute("BEGIN IMMEDIATE")
                control = conn.execute("SELECT mode FROM controls WHERE refund_id=?", (identifier,)).fetchone()
                mode = control["mode"] if control else "SUCCESS"
                if mode == "UNAVAILABLE":
                    return self.send(503, {"error": "injected submission outage"})
                old = conn.execute("SELECT * FROM refunds WHERE refund_id=?", (identifier,)).fetchone()
                if old and (old["amount"] != amount or attempt < old["attempt_no"]):
                    return self.send(409, {"error": "idempotency arguments differ"})
                if old and attempt > old["attempt_no"] and (old["status"] != "FAILED" or attempt != old["attempt_no"] + 1):
                    return self.send(409, {"error": "previous attempt is not retryable"})
                if not old or attempt > old["attempt_no"]:
                    failed = mode == "FAIL_ONCE" and attempt == 1
                    pending = mode in {"DROP_CALLBACK", "DUPLICATE_CALLBACK", "PENDING"}
                    status = "FAILED" if failed else "ACCEPTED" if pending else "SUCCEEDED"
                    ready = time.time() + (86400 if mode == "PENDING" else 0.5)
                    reason = "模拟渠道明确拒绝，可显式重试" if failed else ""
                    conn.execute("""INSERT INTO refunds(refund_id,attempt_no,amount,status,reason,mode,ready_at,effective_refunds)
                        VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(refund_id) DO UPDATE SET attempt_no=excluded.attempt_no,
                        status=excluded.status,reason=excluded.reason,mode=excluded.mode,ready_at=excluded.ready_at,
                        notified=0,accept_count=refunds.accept_count+1,effective_refunds=excluded.effective_refunds""",
                        (identifier, attempt, amount, status, reason, mode, ready, int(status == "SUCCEEDED")))
                row = conn.execute("SELECT * FROM refunds WHERE refund_id=?", (identifier,)).fetchone()
                if mode == "DROP_RESPONSE_ONCE" and not row["response_dropped"]:
                    conn.execute("UPDATE refunds SET response_dropped=1 WHERE refund_id=?", (identifier,))
                    drop = True
            # Commit precedes transport failure, reproducing the accepted-but-response-lost window.
            if drop:
                self.close_connection = True
                return
            if mode == "ACCEPT_THEN_HANG":
                time.sleep(10)
            self.send(200, receipt(row))
        except (ValueError, KeyError, InvalidOperation, TypeError):
            self.send(400, {"error": "invalid request"})


def complete_and_notify():
    while True:
        try:
            with database() as conn:
                conn.execute("UPDATE refunds SET status='SUCCEEDED',effective_refunds=1 WHERE status='ACCEPTED' AND ready_at<=?", (time.time(),))
                rows = conn.execute("SELECT * FROM refunds WHERE status IN ('SUCCEEDED','FAILED') AND notified=0").fetchall()
            for row in rows:
                if not CALLBACK or row["mode"] in {"DROP_CALLBACK", "DROP_RESPONSE_ONCE", "UNAVAILABLE", "ACCEPT_THEN_HANG"}:
                    continue
                payload = {"eventId": str(uuid.uuid5(uuid.NAMESPACE_URL, row["refund_id"] + ":" + str(row["attempt_no"]) + ":" + row["status"])),
                           "attemptNo": row["attempt_no"], "outcome": row["status"], "amount": row["amount"], "reason": row["reason"]}
                request = urllib.request.Request(CALLBACK + "/" + row["refund_id"] + "/events",
                    data=json.dumps(payload).encode(), headers={"Content-Type": "application/json", "X-Refund-Token": TOKEN})
                for _ in range(2 if row["mode"] == "DUPLICATE_CALLBACK" else 1):
                    with urllib.request.urlopen(request, timeout=3) as response:
                        response.read()
                with database() as conn:
                    conn.execute("UPDATE refunds SET notified=1 WHERE refund_id=? AND attempt_no=?", (row["refund_id"], row["attempt_no"]))
        except (OSError, sqlite3.Error):
            pass  # Durable unacknowledged callbacks are retried on the next scan.
        time.sleep(0.5)


if __name__ == "__main__":
    threading.Thread(target=complete_and_notify, daemon=True).start()
    ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "8090"))), Handler).serve_forever()
