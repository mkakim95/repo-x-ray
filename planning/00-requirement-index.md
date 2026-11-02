# Requirement index

This pack separates the local MVP from release-hardening work. A stage is not a claim that every acceptance check already passes. Inspect current source and test output before extending it.

| ID | Requirement | Primary stage | Evidence required |
|---|---|---|---|
| R01 | Java 25 / Spring Boot API, Vue 3 / Vite UI | 01 | Backend tests; frontend typecheck/build |
| R02 | Public GitHub input and immutable source ref | 02 | URL/ref/visibility tests; SHA in result |
| R03 | Static Maven module/dependency analysis | 03 | Golden fixture graphs and XXE negatives |
| R04 | Static Gradle subset, no execution | 04 | Literal fixtures and dynamic-script warnings |
| R05 | Graph, cycles, useful diagnostics | 05 | Deterministic SCC fixtures; partial coverage labels |
| R06 | Interactive graph and share links | 06 | Search/details/filter/route reload checks |
| R07 | Safe intake, explicit resource budgets | 02 | Archive attack tests; overload/deadline checks |
| R08 | Free local Compose deployment | 07 | Clean build/start/restart smoke test |
| R09 | Automated tests and truthful documentation | 07 | CI pass and support matrix reconciled with code |

Non-goals: executing analyzed repositories; private repositories; source-level call graphs; arbitrary Git hosts; remote dependency resolution; billing or paid services. No push, merge, or public deployment is implied by local implementation.
