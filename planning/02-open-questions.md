# Decisions and open questions

## Working assumptions

- “Dependencies” means declared build-module relationships, not source-code imports or runtime calls.
- Public GitHub repositories only. Optional credentials remain backend-only.
- Gradle is a deliberately limited static subset. No effective-model fidelity is promised.
- Compose is local-only by default; a public share URL requires an operator-authorized reachable deployment.
- Opaque result IDs are convenience identifiers, not authorization credentials.

## Must decide before public hosting

1. Retention/expiry and behavior after a repository becomes private. Current storage behavior must be verified in source and documented; do not promise durable links without persistent storage.
2. Authentication, abuse prevention, per-client limits, trusted reverse-proxy configuration, TLS and host policy.
3. Measured concurrency, archive, descriptor, nesting, graph, time and memory budgets. Avoid presenting proposed limits as enforced facts.
4. Restart recovery and durable job ownership, if asynchronous jobs are introduced.
5. Whether external dependencies, scope filters, ref input, version catalogs, Maven profiles and imported BOMs are required in the next release.

## Deliberate exclusions

No private repository support, build execution, external parent/BOM downloading, submodules, Git LFS, deployment automation, paid API, or automatic publication. Expand these only through an explicit reviewed task.
