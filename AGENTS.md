# ServiceFlow agent guidance

## Read only what the task needs

Read applicable instructions and directly relevant code first. Use `docs/architecture/system-overview.md` for service boundaries, `docs/adr/` for relevant architectural decisions, and deployment/runbook documentation when the task involves those operations. Do not read the full documentation tree before routine edits. Use a specialized skill only when its workflow matches the requested output.

## Execution and authorization

Continue authorized local edits, read-only inspection, and relevant local checks without repeated confirmation. Preserve unrelated user changes and original datasets. Existing authorization remains valid within its stated scope.

Ask before unapproved production changes, destructive data operations, system-wide installations, paid external operations, or sending private data to external services. Never expose or commit `.env`, credentials, customer uploads, or unredacted model responses. Retain the product's order-cancellation confirmation and authorization checks.

## Verification and completion

Run checks appropriate to the changed behavior; see `CONTRIBUTING.md` for commands. Documentation-only changes need relevant document/link review, not full application builds. Do not assume a test is isolated: inspect its configuration before running checks that can use databases or external services. Cloud-model evaluation requires authorization for its data and cost scope.

Reuse passing results for unchanged code and environments. Fix failures caused by the change and rerun affected checks; broaden testing when failures or cross-component impact justify it. Complete the requested work and relevant verification, then report changes, checks, and unresolved limitations. Do not stop for review after the first implementation unless the user requested that boundary.
