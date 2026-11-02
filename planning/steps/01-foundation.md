# Foundation and contract

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
None. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
backend/pom.xml, backend/src/, frontend/package.json, frontend/src/, compose.yaml. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Confirm Java 25/Spring Boot and Vue 3/Vite builds. Freeze the actual synchronous request/response contract and error DTO before adding jobs. Preserve a zero-token local startup.

## Verification
Backend unit tests; frontend tests and production build; health request through the frontend proxy.

## Acceptance
Both applications build; request DTO and response fields agree; token never appears in frontend assets.

## Non-goals
No job queue or database until required.

## Risks, rollback and diagnostics
Dependency upgrades may change APIs; revert the isolated version change if build fails.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
