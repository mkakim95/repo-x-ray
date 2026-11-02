# Maven declared graph

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
02. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
backend/src/ Maven parser and fixture tests. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Harden XML against DTD/external entities. Discover POM modules; resolve supported local properties and coordinates without remote downloads. Keep dependencyManagement, inheritance and aggregation distinct from actual dependency edges. Mark unsupported parent/profile/BOM semantics.

## Verification
Golden single/multi-module fixtures; XXE; malformed POM; property/parent loops; duplicate artifactIds; dependencyManagement does not create dependency edges.

## Acceptance
Deterministic graph, source provenance and visible incomplete-model diagnostics; malformed input fails safely.

## Non-goals
Not an effective Maven model and never invoke Maven on repository input.

## Risks, rollback and diagnostics
Incorrect internal matching creates false cycles; prefer unresolved diagnostics over guessing.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
