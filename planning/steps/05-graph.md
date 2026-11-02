# Graph correctness and reproducibility

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
03 and 04. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
backend/src/ graph model/analysis/cache tests. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Separate aggregation from DEPENDS_ON edges. Compute strongly connected components over internal dependencies, including self-loops. Bound returned examples and graph size. Key any cache by resolved commit and analyzer version/options, not a moving branch alone.

## Verification
DAG, SCC, self-loop, duplicate edge, isolated node, unresolved coordinate, graph cap and cache/ref change fixtures.

## Acceptance
Cycle output is deterministic and bounded; no exponential enumeration; analysis records source SHA and limitations.

## Non-goals
No persisted immutable report service until explicitly implemented.

## Risks, rollback and diagnostics
Scope filtering must agree between graph and cycle calculation. Clear cache on incompatible analyzer upgrades.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
