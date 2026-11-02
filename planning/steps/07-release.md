# Local release and hosting gates

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
06. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
compose.yaml, .github/workflows/ci.yml, README.md, all tests. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Run clean Compose build/start and health through proxy, test restart behavior and container restrictions. Reconcile README support/limits with code. Before public hosting add threat review, per-client abuse controls, TLS, observability, retention and immutable result policy.

## Verification
mvn verify on Java25; npm ci/test/build; docker compose config; docker compose up --build; live public GitHub smoke if network permits; mocked offline security/load suite.

## Acceptance
Each check has a recorded pass/fail/unrun result. Local boot works with no paid service and optional token. All hosted-service gates remain explicit until tested.

## Non-goals
No deployment, push, merge or promise of production readiness.

## Risks, rollback and diagnostics
Rolling back local containers loses memory cache only; do not introduce persistent schema without a migration/recovery plan.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
