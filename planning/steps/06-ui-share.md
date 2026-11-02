# Interactive inspection and share navigation

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
05. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
frontend/src/, frontend/nginx.conf, frontend tests. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Provide input, errors/loading, graph, searchable module list and inspector, cycle explanations and visible warnings. Cancel stale requests and protect repeated submits. Current query share links replay analysis; clearly distinguish these from stored immutable reports.

## Verification
Component tests for loading/error/empty/partial/cyclic results; literal HTML rendered safely; URL roundtrip; browser reload/back/forward; clipboard failure; repeated submissions.

## Acceptance
Share URL restores inputs; warnings remain visible; keyboard-accessible alternative exists; no v-html for repository data.

## Non-goals
No promise that a moving branch share URL reproduces the original graph; no private share authorization.

## Risks, rollback and diagnostics
A large layout may block rendering; test measured caps and offer list fallback.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
