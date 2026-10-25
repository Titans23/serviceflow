# ServiceFlow agent guidance

## Read only what the task needs

Read applicable instructions and directly relevant code first. Use `docs/architecture/system-overview.md` for service boundaries, `docs/adr/` for relevant architectural decisions, and deployment/runbook documentation when the task involves those operations. Do not read the full documentation tree before routine edits. Use a specialized skill only when its workflow matches the requested output.

## Execution and authorization

Continue authorized local edits, read-only inspection, and relevant local checks without repeated confirmation. Preserve unrelated user changes and original datasets. Existing authorization remains valid within its stated scope.

Ask before unapproved production changes, destructive data operations, system-wide installations, paid external operations, or sending private data to external services. Never expose or commit `.env`, credentials, customer uploads, or unredacted model responses. Retain the product's order-cancellation confirmation and authorization checks.

## Verification and completion

Run checks appropriate to the changed behavior; see `CONTRIBUTING.md` for commands. Documentation-only changes need relevant document/link review, not full application builds. Do not assume a test is isolated: inspect its configuration before running checks that can use databases or external services. Cloud-model evaluation requires authorization for its data and cost scope.

Reuse passing results for unchanged code and environments. Fix failures caused by the change and rerun affected checks; broaden testing when failures or cross-component impact justify it. Complete the requested work and relevant verification, then report changes, checks, and unresolved limitations. Do not stop for review after the first implementation unless the user requested that boundary.

## Delivery checkpoints and project direction

After each independently verifiable progress point passes relevant checks, commit and push that checkpoint to GitHub, as requested by the user. Review the staged diff and explicit file list first; exclude secrets, raw model responses, local uploads, unrelated edits and `.publish/` artifacts. Never force-push or invent a history of passing intermediate states. When existing changes cannot be separated honestly, publish a clearly described current-state baseline, then use smaller checkpoints going forward. Report the SHA/link, validation evidence and actual CI status; pending is not passed. If pushing fails, preserve the local commit and report the failure.

For the Agent/Harness internship portfolio, ServiceFlow demonstrates stateful business-agent execution correctness: request → evidence → pending confirmation → authorized tool execution → result verification → timeout/duplicate-request recovery. Reuse existing trace, checkpoint, authorization, idempotency and refund code; a checkpoint alone does not establish safe replay, and recovery must not repeat a refund. Prioritize measured gaps in one side-effecting business chain over more SKUs, mall UI, middleware or framework rewrites. Track tool arguments and state assertions, unauthorized side effects, duplicate operations, recovery behavior, cost and latency. Keep rule checks, model screening and evidence adjudication separate; extractive grounding is not a claim of general Agent capability. New paid cloud experiments still require authorization for their data and cost scope.
