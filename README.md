# Repo X-Ray

A local-first **static build-module dependency explorer** for public GitHub repositories: Java 25 + Spring Boot backend, Vue 3 + TypeScript + Vite frontend, interactive Cytoscape graph. No paid service or database is required.

> This is a bounded MVP, not an effective Maven/Gradle resolver or a production multi-tenant service. It reads build descriptors; it never builds or executes the analyzed repository. The initial example is synthetic demo data, not a live GitHub analysis.

## Run locally

Prerequisites: Docker Engine with Docker Compose v2 and network access to pull build images/dependencies and reach GitHub.

```sh
cp .env.example .env           # optional: set backend-only GITHUB_TOKEN
docker compose up --build    # first build downloads dependencies
```

Open **http://localhost:8080**. Only the frontend is published, bound to loopback; `/api` is proxied to the backend. The backend can take a few seconds to start; retry if you initially see a gateway error.

```sh
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://github.com/OWNER/REPOSITORY","ref":"main"}'
docker compose logs -f
docker compose down
```

Replace the example owner/repository/ref. Omit `ref` to analyze the default branch. `REPO_XRAY_PORT` in `.env` changes the local port. No results database exists; stopping the backend discards its memory cache.

### Without Docker

Install JDK **25**, Maven 3.9+, and Node.js 22/npm. In separate terminals:

```sh
cd backend && mvn spring-boot:run
```

```sh
cd frontend && npm ci && npm run dev
```

Vite's development proxy forwards `/api` to port 8080. Keep development servers local; the supplied npm dev command can listen on all interfaces.

## What it analyzes

- Public `https://github.com/owner/repository` input, optional branch/tag/commit ref, resolved to one commit before fetching descriptors.
- Maven `pom.xml` files and literal dependencies in `build.gradle` / `build.gradle.kts` files.
- Declared internal module edges, external dependency labels/counts, cycle groups, file counts and extension-based language counts.
- Searchable module/graph inspection and warning messages for known limitations.

Read warnings before interpreting a graph. A missing edge is not proof that no dependency exists. Maven parents, profiles, imported BOMs and effective-model semantics are not fully resolved. Gradle is a heuristic literal subset: settings remapping, composite builds, scripts, plugins, computed dependencies and version catalogs are not fully evaluated. Language counts measure filenames, not lines of code. Cycle groups are not exhaustive ordered paths.

### Sharing and reproducibility

The share link encodes repository/ref inputs and **runs a fresh analysis** when opened. It is not a stored report or access-controlled document. A URL on `localhost` works only on the recipient's own running installation. Use an immutable commit SHA for repeatable source selection; branch names can move. Results are cached in memory for five minutes by request input, so a moving branch may temporarily show a previous snapshot. Results identify the resolved commit.

## Security boundaries and current limits

Repository data is untrusted. The backend uses fixed GitHub API endpoints, does not follow redirects, checks public visibility even with a token, and fetches tree/blob content instead of cloning or extracting archives. XML parsing disables external entity/DTD processing. No analyzed wrapper, plugin, build command or repository process is executed.

Current limits are defined in backend source:

| Budget | MVP limit |
|---|---:|
| Concurrent analyses | 2 (additional requests receive 429) |
| Build descriptors | First 100 in lexical path order |
| Descriptor decoded size | 512 KiB each |
| Total decoded descriptors | 8 MiB |
| GitHub tree response | 8 MiB |
| GitHub fetch budget | 45 seconds; per request up to 12 seconds |
| Cache | Up to 32 results; five-minute TTL |

Limits can yield partial results with warnings or an error. The fetch deadline is not a separately sandboxed hard CPU timeout for parsing. GitHub's own rate limits still apply; repositories with many descriptors can exhaust unauthenticated allowances. Optional `GITHUB_TOKEN` stays in the backend environment; never use a `VITE_*` token variable or commit `.env`.

Compose adds memory/CPU/process limits, dropped capabilities, nonroot application images and read-only runtime filesystems with bounded temporary mounts. These are defense in depth, not a claim of verified production isolation.

**Before public hosting:** add per-client abuse controls, trusted proxy/TLS configuration, request-body and parser resource-budget review, robust upstream failure tests, observability, retention policy and an immutable report design if durable share links are required. Public repository metadata is still potentially sensitive; opaque URLs are not authentication. Do not expose this local MVP directly to the internet.

## Tests and verification

```sh
(cd backend && mvn --batch-mode verify)
(cd frontend && npm ci && npm test && npm run build)
docker compose config --quiet
docker compose up --build -d
curl --fail http://localhost:8080/api/health
docker compose down
```

CI runs Java 25 backend verification, frontend tests/typecheck/build, and a Compose startup/health smoke test. CI configuration is not evidence that a run has passed. Live GitHub access is separate from offline unit tests; no token is required for those tests. The Java 25 and Docker checks require those tools locally; a lower-JDK compatibility check is not a substitute.

## Layout and next steps

- `backend/`: Spring Boot API, bounded GitHub client, static parsers and graph analysis.
- `frontend/`: Vue UI, graph view, local demo, tests and nginx proxy.
- `compose.yaml`: local deployment.
- `.github/workflows/ci.yml`: reproducible verification jobs, no deployment.
- `planning/`: requirement traceability, decisions, seven dependency-ordered implementation/hardening stages and acceptance checks.

Start with [the execution plan](planning/01-implementation-plan.md). Stage acceptance criteria describe the target bar, not an assertion that every listed check is implemented or passed. Publication, deployment and merging are separate actions, not part of local setup.

## License

[MIT](LICENSE).
