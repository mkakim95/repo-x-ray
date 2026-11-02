# Safe public GitHub intake

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
01. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
backend/src/ intake/client/controller and security tests; compose.yaml. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Validate canonical HTTPS GitHub owner/repo URLs and optional refs. Use fixed GitHub API destinations, verify public visibility, resolve SHA and fetch bounded tree/blob data. Document actual file/byte/time/concurrency/cache limits. If archives are added later, require safe streamed parsing with traversal/link/bomb defenses before enabling them.

## Verification
Reject credentials, ports, alternate hosts and paths; test private metadata, rate limits, malformed payloads, truncated tree, source caps, timeout and saturation with mocked HTTP.

## Acceptance
Every fetch is bounded; overload is controlled; failed analyses release concurrency permits; no repository code or arbitrary destination is executed/fetched.

## Non-goals
No git clone, submodules, LFS or archive intake in the tree/blob MVP.

## Risks, rollback and diagnostics
API budgets and mutable refs affect reproducibility. Record resolved SHA and structured failure without secrets.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
