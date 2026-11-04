"""Reproducible, read-only HTTP load after additive seeding in the dedicated verification database."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import hashlib
import json
import math
from pathlib import Path
import platform
import re
import secrets
import subprocess
import sys
import time
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/evaluation"))
from eval_v2 import Client, write_json
from prepare_v2_fixtures import prepare, sql_string

PROJECT = "serviceflow-refund-verification"
DATABASE = "serviceflow_eval_refund_verification"


def docker(*args, stdin=None, timeout=600):
    result = subprocess.run(["docker", *args], input=stdin, capture_output=True, text=True,
                            encoding="utf-8", timeout=timeout)
    if result.returncode:
        raise RuntimeError("Isolated benchmark command failed: " + result.stderr[:2000])
    return result.stdout.strip()


def sql(text):
    return docker("exec", "-i", PROJECT + "-mysql-1", "mysql", "--default-character-set=utf8mb4",
                  "-userviceflow", "-peval-only-password", "-N", "-B", DATABASE, stdin=text)


def seed(rows):
    run = datetime.now().strftime("%Y%m%d%H%M%S") + f"{secrets.randbelow(10000):04}"
    statement, fixture = prepare({"cases": []}, run)
    sql(statement)
    owner = int(sql("SELECT customer_id FROM app_user WHERE username=" + sql_string(fixture["principals"]["customer"]) + ";"))
    regular = int(sql("SELECT customer_id FROM app_user WHERE username=" + sql_string(fixture["principals"]["other"]) + ";"))
    heavy = rows // 5
    print(f"Preparing {rows} additive synthetic orders; heavy customer has {heavy}.", flush=True)
    started = time.perf_counter()
    sql(f"""
    CREATE TEMPORARY TABLE perf_guard(allowed BOOLEAN CHECK(allowed=TRUE));
    INSERT INTO perf_guard VALUES(DATABASE()='{DATABASE}');
    CREATE TEMPORARY TABLE numbers(n INT PRIMARY KEY);
    INSERT INTO numbers WITH digits(n) AS (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)
    SELECT a.n+b.n*10+c.n*100+d.n*1000+e.n*10000+f.n*100000
      FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits d CROSS JOIN digits e CROSS JOIN digits f
      WHERE a.n+b.n*10+c.n*100+d.n*1000+e.n*10000+f.n*100000 < {rows};
    START TRANSACTION;
    INSERT INTO customer(name) SELECT CONCAT('perf-{run}-',n) FROM numbers WHERE n<1000 ORDER BY n;
    SET @perf_first_customer=LAST_INSERT_ID();
    INSERT INTO customer_order(order_no,customer_id,status,total_amount,created_at)
      SELECT CONCAT('SF{run}',LPAD(n,6,'0')),
        CASE WHEN n<{heavy} THEN {owner} WHEN MOD(n,1000)=0 THEN {regular} ELSE @perf_first_customer+MOD(n,1000) END,
        CASE MOD(n,4) WHEN 0 THEN 'PAID' WHEN 1 THEN 'PROCESSING' WHEN 2 THEN 'SHIPPED' ELSE 'DELIVERED' END,
        99,DATE_ADD('2026-01-01 00:00:00',INTERVAL FLOOR(n/20) SECOND)
      FROM numbers ORDER BY n;
    INSERT INTO order_item(order_id,product_id,quantity,unit_price)
      SELECT id,(SELECT MIN(id) FROM product),1,99 FROM customer_order WHERE order_no LIKE 'SF{run}%';
    COMMIT;
    ANALYZE TABLE customer_order,order_item;
    """)
    actual = int(sql(f"SELECT COUNT(*) FROM customer_order WHERE order_no LIKE 'SF{run}%';"))
    assert actual == rows
    assert int(sql(f"SELECT COUNT(*) FROM customer_order WHERE customer_id={owner};")) == heavy
    directory = ROOT / "quality/evaluation/reports" / ("order-performance-" + run)
    directory.mkdir(parents=True, exist_ok=False)
    receipt = {"synthetic": True, "runId": run, "project": PROJECT, "database": DATABASE,
               "rowsAdded": rows, "heavyCustomerRows": heavy, "customerId": owner,
               "regularCustomerId": regular, "principals": fixture["principals"],
               "orderNo": "SF" + run + "000000", "seedSeconds": round(time.perf_counter() - started, 2)}
    write_json(directory / "receipt.json", receipt)
    print("DATASET " + str(directory / "receipt.json"), flush=True)
    return directory / "receipt.json"


def measure(client, path, role, count, concurrency, expected_no=None, duration=0):
    for _ in range(5):
        response = client.request("GET", path, principal=role)
        assert response["status"] == 200
        if expected_no:
            assert len(response["body"]["items"]) == 1 and response["body"]["items"][0]["orderNo"] == expected_no, "Backend lookup contract does not match the selected measurement"

    def call(_):
        started = time.perf_counter()
        try:
            response = client.request("GET", path, principal=role)
            valid = response["status"] == 200
            if expected_no:
                valid = valid and len(response["body"]["items"]) == 1 and response["body"]["items"][0]["orderNo"] == expected_no
            return (time.perf_counter() - started) * 1000, valid
        except Exception:
            return (time.perf_counter() - started) * 1000, False

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        if duration:
            deadline = started + duration
            def worker():
                samples = []
                while time.perf_counter() < deadline:
                    samples.append(call(None))
                return samples
            batches = [executor.submit(worker) for _ in range(concurrency)]
            rows = [sample for batch in batches for sample in batch.result()]
        else:
            rows = list(executor.map(call, range(count)))
    elapsed = time.perf_counter() - started
    count = len(rows)
    times = sorted(value for value, _ in rows)
    return {"concurrency": concurrency, "requests": count, "errors": sum(not ok for _, ok in rows), "loadSeconds": duration,
            "elapsedSeconds": round(elapsed, 3), "throughputRps": round(count / elapsed, 2),
            "p50Ms": round(times[math.ceil(count * 0.50) - 1], 2),
            "p95Ms": round(times[math.ceil(count * 0.95) - 1], 2),
            "p99Ms": round(times[math.ceil(count * 0.99) - 1], 2)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=["baseline", "optimized"], required=True)
    parser.add_argument("--prepare", action="store_true")
    parser.add_argument("--rows", type=int, default=1_000_000)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--label", default="")
    parser.add_argument("--duration-seconds", type=int, default=0)
    parser.add_argument("--source-revision")
    parser.add_argument("--allow-isolated-load", action="store_true")
    args = parser.parse_args()
    if args.label and not re.fullmatch("[a-z0-9-]{1,32}", args.label):
        parser.error("Label must contain only lowercase letters, digits or hyphens")
    if not 0 <= args.duration_seconds <= 300:
        parser.error("Duration must be between 0 and 300 seconds")
    if not args.allow_isolated_load or not 1000 <= args.rows <= 1_000_000:
        parser.error("Explicit isolated-load opt-in and 1,000..1,000,000 rows required")
    client = Client("http://127.0.0.1:28180/api")
    info = client.request("GET", "/actuator/info")["body"]["serviceflow"]
    assert info["aiMode"] == info["ragMode"] == "demo"
    assert str(info["evaluationEnabled"]).lower() == "true"
    assert docker("inspect", PROJECT + "-mysql-1", "--format", '{{index .Config.Labels "com.docker.compose.project"}}') == PROJECT
    assert sql("SELECT DATABASE();") == DATABASE
    receipt_path = seed(args.rows) if args.prepare else args.receipt
    if not receipt_path:
        parser.error("--receipt or --prepare required")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    assert receipt["synthetic"] and receipt["project"] == PROJECT and receipt["database"] == DATABASE
    output = receipt_path.parent / (args.phase + ("-" + args.label if args.label else "") + ".json")
    if output.exists():
        raise RuntimeError("Refusing to overwrite an existing measurement")
    client.usernames = receipt["principals"]
    client.token("customer")
    client.token("other")
    number = receipt["orderNo"]
    assert number.startswith("SF") and number[2:].isdigit()
    customer = int(receipt["customerId"])
    # Parameter-expanded equivalent of the application's query, recorded separately from real HTTP timings.
    predicate = ("o.order_no=" + sql_string(number)) if args.phase == "optimized" else (
        f"(LOCATE({sql_string(number)},o.order_no)>0 OR EXISTS (SELECT 1 FROM order_item i JOIN product p ON p.id=i.product_id "
        f"WHERE i.order_id=o.id AND (LOCATE({sql_string(number)},p.name)>0 OR LOCATE({sql_string(number)},p.model)>0 "
        f"OR LOCATE({sql_string(number)},p.sku)>0 OR p.category={sql_string(number)})))")
    explanation = sql(f"EXPLAIN ANALYZE SELECT o.id,o.order_no,o.customer_id,o.status,o.total_amount,o.version,o.created_at "
                      f"FROM customer_order o WHERE o.customer_id={customer} AND {predicate} "
                      "ORDER BY o.created_at DESC,o.id DESC LIMIT 11;").replace("\\n", "\n")
    results = []
    query_key = "orderNo" if args.phase == "optimized" else "keyword"
    for concurrency in (1, 8, 16):
        result = measure(client, "/orders?" + urlencode({"size": 10, query_key: number}), "customer", 48, concurrency, number, args.duration_seconds)
        results.append(result)
        print(json.dumps({"phase": args.phase, **result}), flush=True)
    control = measure(client, "/orders?size=10", "other", 48, 8)
    source = ROOT / "apps/serviceflow-server/src/main/resources/mapper/OrderMapper.xml"
    revision = subprocess.check_output(["git", "rev-parse", "--verify", (args.source_revision or "HEAD") + "^{commit}"], cwd=ROOT, text=True).strip()
    source_bytes = subprocess.check_output(["git", "show", revision + ":" + source.relative_to(ROOT).as_posix()], cwd=ROOT) if args.source_revision else source.read_bytes()
    report = {"phase": args.phase, "label": args.label, "dataset": receipt, "host": platform.platform(),
              "timer": "perf_counter", "timerResolutionSeconds": time.get_clock_info("perf_counter").resolution,
              "docker": json.loads(docker("info", "--format", '{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}')),
              "serverMemoryLimitBytes": int(docker("inspect", PROJECT + "-server-1", "--format", "{{.HostConfig.Memory}}")),
              "mysqlMemoryLimitBytes": int(docker("inspect", PROJECT + "-mysql-1", "--format", "{{.HostConfig.Memory}}")),
              "serverImage": docker("inspect", PROJECT + "-server-1", "--format", "{{.Image}}"),
              "sourceRevision": revision, "sourceIsWorkingTree": not bool(args.source_revision),
              "mapperSha256": hashlib.sha256(source_bytes).hexdigest(),
              "queryParameter": query_key, "measurements": results, "ordinaryCustomerListControl": control,
              "explainAnalyze": explanation, "cloudCalls": 0,
              "scope": "Warm local HTTP lookup; five warmups; fixed-concurrency closed-loop load, outstanding requests drained; not a capacity limit or cloud-model benchmark"}
    write_json(output, report)
    print("REPORT " + str(output), flush=True)


if __name__ == "__main__":
    main()
