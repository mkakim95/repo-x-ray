# Gradle static subset

## Objective
Deliver a reviewable, truthful slice of Repo X-Ray.

## Prerequisites
03. Read `planning/01-implementation-plan.md` and current repository instructions.

## Inspect first
backend/src/ Gradle parser and fixture tests. Names beyond existing paths must be verified, not assumed.

## Work and failure behavior
Specify recognized literal settings/project dependency syntax for Groovy and Kotlin. Ignore comments and non-code strings correctly. Mark dynamic includes, interpolations, convention plugins, composite builds and unsupported catalogs. Prefer tokenization to loose regex inference.

## Verification
Literal Groovy/Kotlin examples; multiline/comment/escape cases; fake dependency text inside strings; dynamic/malicious scripts remain inert.

## Acceptance
Supported fixtures are exact; unsupported constructs produce warnings; no GroovyShell, Tooling API or process execution.

## Non-goals
No complete Gradle evaluation or plugin application.

## Risks, rollback and diagnostics
Static parsing cannot prove runtime configuration; label every report accordingly.

## Codex handoff
Implement only this stage after inspecting current source and tests. Preserve working behavior and established conventions. Do not execute analyzed code, add unrelated infrastructure, publish, or merge. Update the decision log for resolved assumptions. Run targeted and relevant aggregate checks; report changed files, exact outcomes and remaining risks. Stop on an unresolved correctness blocker.
