# Execution plan

Order: **01 foundation → 02 safe intake → 03 Maven → 04 Gradle → 05 graph → 06 UI/share → 07 release verification**. Maven and Gradle share a normalized graph model; do not introduce two independent graph APIs.

The existing scaffold is a local MVP, not a hardened multi-tenant hosted service. Inspect `backend/`, `frontend/`, `compose.yaml`, and existing tests before each stage. Reuse what is already present. Stage documents describe the acceptance bar, including follow-up work where the MVP intentionally cuts scope.

## Operating rules

- Read repository instructions and `planning/02-open-questions.md` first.
- Work only on the selected stage; avoid unrelated refactoring.
- Never run downloaded build scripts, wrappers, plugins, Maven, Gradle, or Git hooks. Running this application's own trusted tests/build is separate and permitted.
- Parser incompleteness must be user-visible, never a false claim of a complete effective build graph.
- Keep API errors stable and source strings untrusted; never log access tokens.
- Record resolved assumptions in `decision-log.md`.
- Run targeted tests, then applicable aggregate checks. Report passed, failed, and unrun checks separately.
- If correctness depends on unavailable information, report the exact blocker instead of inventing behavior.

## Completion gates

A working UI does not establish archive security. Unit tests do not establish a working Docker deployment. A fixture analysis does not establish live GitHub access. Validate each separately. Before public hosting, require all security/load/retention gates in stage 07 and an explicit deployment authorization.
