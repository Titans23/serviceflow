"""Verify autonomous refunds against disposable local HTTP/MySQL/RabbitMQ/channel services."""
import argparse
import base64
from datetime import datetime
import json
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid

from eval_v2 import Client, ROOT, write_json
from prepare_v2_fixtures import prepare, sql_string

PROJECT = "serviceflow-refund-verification"
DATABASE = "serviceflow_eval_refund_verification"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-isolated-faults", action="store_true")
    args = parser.parse_args()
    if not args.allow_isolated_faults:
        parser.error("Requires explicit opt-in: creates synthetic rows and restarts only the isolated verification containers")
    client = Client("http://127.0.0.1:28180/api")
    info = client.request("GET", "/actuator/info")["body"]["serviceflow"]
    assert info["aiMode"] == info["ragMode"] == "demo"
    assert str(info["evaluationEnabled"]).lower() == str(info["refundDispatchEnabled"]).lower() == "true"

    def docker(*command, stdin=None):
        result = subprocess.run(["docker", *command], input=stdin, text=True, encoding="utf-8",
                                capture_output=True, timeout=150, check=True)
        return result.stdout.strip()

    for service in ("server", "mysql", "rabbitmq", "refund-channel"):
        assert docker("inspect", PROJECT + "-" + service + "-1", "--format",
                      '{{index .Config.Labels "com.docker.compose.project"}}') == PROJECT

    def sql(query):
        return docker("exec", "-i", PROJECT + "-mysql-1", "mysql", "--default-character-set=utf8mb4",
                      "-userviceflow", "-peval-only-password", "-N", "-B", DATABASE, stdin=query)

    assert sql("SELECT DATABASE();") == DATABASE
    names = ["normal", "lost-response", "lost-callback", "duplicate-callback", "failed-retry",
             "manual", "outbox-restart", "worker-restart", "browser-manual"]
    run_id = datetime.now().strftime("%Y%m%d%H%M%S") + f"{secrets.randbelow(10000):04}"
    fixture_sql, fixture = prepare({"cases": [{"id": name, "fixture": {
        "owner": "customer", "orderStatus": "PROCESSING", "paymentStatus": "PAID"}} for name in names]}, run_id)
    output = ROOT / "quality/evaluation/reports" / ("refund-delivery-" + run_id)
    output.mkdir(parents=True, exist_ok=False)
    sql(fixture_sql)
    client.usernames = fixture["principals"]
    checks, results = [], {}

    def check(name, condition):
        checks.append({"name": name, "passed": bool(condition)})
        write_json(output / "checks.json", checks)
        assert condition, name
        print("PASS " + name, flush=True)

    def channel(path, data=None):
        request = urllib.request.Request("http://127.0.0.1:28090" + path,
            data=json.dumps(data).encode() if data is not None else None,
            headers={"X-Refund-Token": "isolated-refund-channel-token", "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.load(response)

    def rabbit(path, data=None):
        request = urllib.request.Request("http://127.0.0.1:28672/api" + path,
            data=json.dumps(data).encode() if data is not None else None,
            headers={"Authorization": "Basic " + base64.b64encode(b"serviceflow:eval-only-password").decode(),
                     "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.load(response)

    def until(predicate, seconds=30):
        deadline = time.monotonic() + seconds
        while True:
            if predicate():
                return
            if time.monotonic() >= deadline:
                raise TimeoutError("Isolated refund did not reach its expected state")
            time.sleep(0.2)

    def state(name):
        number = fixture["cases"][name]["orderNo"]
        assert number.startswith("SF") and number[2:].isdigit()
        return json.loads(sql("SELECT JSON_OBJECT('orderStatus',o.status,'paymentRefundStatus',p.refund_status,"
            "'refundStatus',r.status,'refundId',r.public_id,'attempt',r.attempt_no,'deliveryState',d.state,"
            "'queryCount',d.query_count,'submitCount',d.submit_count,'ticketId',d.ticket_id,'refundKey',r.id,"
            "'operations',(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),"
            "'refunds',(SELECT COUNT(*) FROM refund_request x WHERE x.order_id=o.id)) "
            "FROM customer_order o JOIN payment p ON p.order_id=o.id LEFT JOIN refund_request r ON r.order_id=o.id "
            "LEFT JOIN refund_delivery d ON d.refund_id=r.id WHERE o.order_no=" + sql_string(number) + ";"))

    def pending(name, mode):
        session = client.request("POST", "/chat/sessions", {"title": "Synthetic refund delivery"}, "customer")["body"]["publicId"]
        response = client.request("POST", "/chat/sessions/" + session + "/messages/stream",
            {"message": "取消订单 " + fixture["cases"][name]["orderNo"], "clientRequestId": str(uuid.uuid4())}, "customer")
        action = response["events"]["action_required"]["actionId"]
        uuid.UUID(action)
        fixture["cases"][name].update(actionId=action, sessionId=session)
        channel("/control/" + action, {"mode": mode})
        check(name + ": no effects before confirmation", state(name)["operations"] == state(name)["refunds"] == 0)
        return action

    def confirm(action):
        response = client.request("POST", "/chat/actions/" + action + "/confirm", {"decision": "CONFIRM"}, "customer")
        check("confirmed action returns durable receipt", response["events"].get("done", {}).get("resultCode") == "CANCELLED")

    def success(name, action, timeout=35):
        until(lambda: state(name)["refundStatus"] == "SUCCEEDED", timeout)
        actual, external = state(name), channel("/inspect/" + action)
        check(name + ": one effective refund in both systems", actual["operations"] == actual["refunds"] == external["effective_refunds"] == 1
              and actual["paymentRefundStatus"] == "REFUNDED" and actual["deliveryState"] == "DONE")
        results[name] = {"state": actual, "channelAcceptedAttempts": external["accept_count"],
                         "channelEffectiveRefunds": external["effective_refunds"]}

    until(lambda: rabbit("/queues/%2F/serviceflow.refund.deliver").get("consumers") == 2)
    check("two real RabbitMQ consumers are active", True)
    inquiry_session = client.request("POST", "/chat/sessions", {"title": "Read-only intent"}, "customer")["body"]["publicId"]
    for query in ("不用取消，帮我查物流", "取消了吗", "只查退款状态", "能不能取消"):
        reply = client.request("POST", "/chat/sessions/" + inquiry_session + "/messages/stream",
            {"message": fixture["cases"]["normal"]["orderNo"] + " " + query, "clientRequestId": str(uuid.uuid4())}, "customer")
        check("read-only intent has no confirmation card: " + query, "action_required" not in reply["events"])
    check("read-only inquiry persists no action or refund", sql("SELECT COUNT(*) FROM chat_action WHERE session_public_id="
          + sql_string(inquiry_session) + ";") == "0" and state("normal")["refunds"] == 0)
    action = pending("normal", "SUCCESS")
    confirm(action)
    task = state("normal")["refundKey"]
    for _ in range(20):
        check("duplicate task routed", rabbit("/exchanges/%2F/amq.default/publish",
            {"properties": {"content_type": "text/plain"}, "routing_key": "serviceflow.refund.deliver",
             "payload": str(task), "payload_encoding": "string"})["routed"])
    success("normal", action)
    for name, mode in (("lost-response", "DROP_RESPONSE_ONCE"), ("lost-callback", "DROP_CALLBACK"),
                       ("duplicate-callback", "DUPLICATE_CALLBACK")):
        action = pending(name, mode)
        confirm(action)
        until(lambda: channel("/inspect/" + action)["effective_refunds"] == 1)
        accepted = time.monotonic()
        success(name, action)
        results[name]["recoveryAfterObservedChannelSuccessMs"] = round((time.monotonic() - accepted) * 1000, 2)
        check(name + ": channel accepted once", results[name]["channelAcceptedAttempts"] == 1)
        if name == "lost-callback":
            check("lost callback reconciled within two query periods plus poll allowance",
                  results[name]["recoveryAfterObservedChannelSuccessMs"] <= 12000)

    action = pending("failed-retry", "FAIL_ONCE")
    confirm(action)
    until(lambda: state("failed-retry")["refundStatus"] == "FAILED")
    check("explicit channel failure has no effective refund", channel("/inspect/" + action)["effective_refunds"] == 0)
    retry = client.request("POST", "/admin/refunds/" + action + "/retry", {"expectedAttemptNo": 1}, "admin")
    check("admin retry reuses refund identity", retry["status"] == 200 and retry["body"]["publicId"] == action and retry["body"]["attemptNo"] == 2)
    success("failed-retry", action)

    def make_manual(name):
        action = pending(name, "UNAVAILABLE")
        confirm(action)
        sql("UPDATE refund_delivery SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE refund_id="
            "(SELECT id FROM refund_request WHERE public_id=" + sql_string(action) + ");")
        until(lambda: state(name)["deliveryState"] == "MANUAL")
        actual = state(name)
        check(name + ": unknown result creates one ticket and never submits blindly",
              bool(actual["ticketId"]) and channel("/inspect/" + action)["accept_count"] == 0)
        ticket_count = sql("SELECT COUNT(*) FROM ticket WHERE public_id=" + sql_string(actual["ticketId"]) + ";")
        check(name + ": handoff is unique", ticket_count == "1")
        return action

    action = make_manual("manual")
    progress = client.request("POST", "/chat/sessions/" + fixture["cases"]["manual"]["sessionId"] + "/messages/stream",
        {"message": "它的退款进度呢", "clientRequestId": str(uuid.uuid4())}, "customer")
    check("Agent reads the actual manual-review ticket", state("manual")["ticketId"] in progress["answer"])
    ticket_id = state("manual")["ticketId"]
    check("customer cannot publish ticket messages", client.request("PUT", "/admin/tickets/" + ticket_id + "/customer-message",
        {"message": "forbidden"}, "customer")["status"] == 403)
    check("admin publishes customer-visible progress", client.request("PUT", "/admin/tickets/" + ticket_id + "/customer-message",
        {"message": "正在核验原退款，请关注本工单"}, "admin")["status"] == 204)
    fresh = client.request("POST", "/chat/sessions", {"title": "New refund inquiry"}, "customer")["body"]["publicId"]
    number = fixture["cases"]["manual"]["orderNo"]
    inquiry = client.request("POST", "/chat/sessions/" + fresh + "/messages/stream",
        {"message": "查询退款工单进度 " + number, "clientRequestId": str(uuid.uuid4())}, "customer")
    check("new session reads the same ticket and public progress", inquiry["events"]["order"]["refundReview"]["publicId"] == ticket_id
          and "正在核验原退款" in inquiry["answer"] and "action_required" not in inquiry["events"])
    check("customer cannot resume channel work", client.request("POST", "/admin/refunds/" + action + "/reconcile", {}, "customer")["status"] == 403)
    channel("/control/" + action, {"mode": "SUCCESS"})
    check("admin resumes read-first reconciliation", client.request("POST", "/admin/refunds/" + action + "/reconcile", {}, "admin")["status"] == 204)
    success("manual", action)
    completed = client.request("POST", "/chat/sessions/" + fresh + "/messages/stream",
        {"message": "退款工单结果 " + number, "clientRequestId": str(uuid.uuid4())}, "customer")
    check("same ticket exposes verified final outcome", completed["events"]["order"]["refundReview"]["publicId"] == ticket_id
          and "退款已完成" in completed["answer"])

    # Broker is unavailable when the cancellation transaction commits; the durable task survives process death.
    action = pending("outbox-restart", "SUCCESS")
    docker("stop", PROJECT + "-rabbitmq-1")
    try:
        confirm(action)
        check("task persists while broker is unavailable", state("outbox-restart")["deliveryState"] == "READY")
        docker("kill", PROJECT + "-server-1")
    finally:
        docker("start", PROJECT + "-rabbitmq-1")
        docker("start", PROJECT + "-server-1")

    def ready():
        try:
            return client.request("GET", "/actuator/health/readiness")["body"].get("status") == "UP"
        except Exception:
            return False

    until(ready, 90)
    success("outbox-restart", action)
    action = pending("worker-restart", "ACCEPT_THEN_HANG")
    confirm(action)
    until(lambda: channel("/inspect/" + action)["effective_refunds"] == 1)
    docker("kill", PROJECT + "-server-1")
    check("crashed worker leaves a durable lease", bool(sql("SELECT lease_token FROM refund_delivery WHERE refund_id="
          "(SELECT id FROM refund_request WHERE public_id=" + sql_string(action) + ");")))
    docker("restart", PROJECT + "-refund-channel-1")
    docker("start", PROJECT + "-server-1")
    until(ready, 90)
    success("worker-restart", action, 45)
    check("channel record survives independent process restart", results["worker-restart"]["channelAcceptedAttempts"] == 1)
    browser_action = make_manual("browser-manual")
    channel("/control/" + browser_action, {"mode": "SUCCESS"})
    write_json(output / "fixtures.json", fixture)
    write_json(output / "summary.json", {"runId": run_id, "mode": "isolated-local-simulator", "cloudCalls": 0,
        "consumers": 2, "duplicateMessages": 20, "checks": checks, "cases": results,
        "paymentScope": "Independent persistent simulator; no real funds"})
    print("REPORT " + str(output / "summary.json"), flush=True)


if __name__ == "__main__":
    main()
