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
```

Cloud-model evaluation is intentionally excluded from pull-request checks. Never commit `.env`, API keys, access tokens, uploaded customer files, or unredacted model responses.

## Commit convention

Use `type(scope): summary`, for example:

```text
feat(chat): persist customer request idempotency
fix(rag): keep evidence insufficient when grader fails
test(order): cover concurrent cancellation
docs(adr): explain transactional outbox
```

Recommended types are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, and `chore`.
