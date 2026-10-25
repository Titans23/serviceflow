# Contributing to ServiceFlow

## Local checks

Before opening a pull request, run checks for the affected components using the commands below. Documentation-only changes require relevant content/link review; mark unrelated checks N/A with a reason. Cross-component changes require the corresponding combined checks. Full CI gates remain in place. Reuse passing local results while code and environment are unchanged; rerun affected checks after fixes.

Use `npm ci` when dependencies are missing or the lockfile changed. Before integration checks, verify the configured data/services are isolated from production.

Available commands:

```powershell
cd apps/serviceflow-server
mvn verify

cd ../serviceflow-web
npm ci
npm run lint
npm test
npm run build

cd ../..
powershell -ExecutionPolicy Bypass -File .\scripts\evaluation\validate-evaluation.ps1
python -B scripts/evaluation/eval_v2.py validate
python -B scripts/evaluation/eval_v2.py validate --suite quality/evaluation/datasets/refund-sandbox-v1.json
python -B -m unittest discover -s scripts/evaluation/tests
```

Cloud-model evaluation is intentionally excluded from pull-request checks. Never commit `.env`, API keys, access tokens, uploaded customer files, or unredacted model responses.

Without Docker, use `mvn -DskipITs verify` for offline backend checks; this does **not** establish MySQL migration/concurrency correctness. The opt-in local Grader acceptance suite additionally needs its own configured model. Refund/data live checks use the [isolated evaluation runbook](docs/guides/evaluation-and-refund.md). Browser tests use mocked APIs and are reported separately from live refund scenarios. CI runs both and never calls paid cloud models.

## Commit convention

Use `type(scope): summary`, for example:

```text
feat(chat): persist customer request idempotency
fix(rag): keep evidence insufficient when grader fails
test(order): cover concurrent cancellation
docs(adr): explain transactional outbox
```

Recommended types are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, and `chore`.
