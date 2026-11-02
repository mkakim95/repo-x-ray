# Repo X-Ray

A local-first **static build-module dependency explorer** for public GitHub repositories and an operator-configured corporate GitLab instance: Java 25 + Spring Boot backend, Vue 3 + TypeScript + Vite frontend, interactive Cytoscape graph. No paid API, subscription or database is required by the application. Local hardware/network and your existing GitLab infrastructure are still prerequisites.

> This is a bounded MVP, not an effective Maven/Gradle resolver or a production multi-tenant service. It reads build descriptors; it never builds or executes the analyzed repository. The initial example is synthetic demo data, not a live GitHub analysis.

## Run locally

Prerequisites: Docker Engine with Docker Compose v2 and network access to pull build images/dependencies and reach the selected Git provider. Corporate use additionally requires approved on-prem/VPN routing, DNS, TLS trust and permission to process the selected repository metadata. A browser reaching GitLab does not prove that the backend container can reach it.

```sh
cp .env.example .env           # optional: configure server-only provider credentials
chmod 600 .env                 # protect credentials on Unix-like hosts
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

Install JDK **25**, Maven 3.9+, and Node.js **22.12+**/npm. In separate terminals:

```sh
cd backend && mvn spring-boot:run
```

```sh
cd frontend && npm ci && npm run dev
```

For non-Docker use, configure provider environment variables in the backend process through your approved local secret mechanism; Maven does not automatically load the root `.env`. Vite's development proxy forwards `/api` to port 8080. Keep development servers local; the supplied npm dev command can listen on all interfaces.

## Corporate GitLab: local/on-prem setup

1. Obtain corporate approval for this tool, the repositories it reads, token issuance, Docker use and any container/dependency downloads. This setup does not bypass GitLab authorization, VPN, firewall or company data-handling rules.
2. Set `GITLAB_BASE_URL` in your protected `.env` to the canonical **HTTPS installation URL**, such as `https://gitlab.company.example`. Do not append `/api/v4` or a project path. A supported simple installation prefix and explicit HTTPS port may be included. Redirects are refused; configure the final canonical URL directly.
3. Set server-only `GITLAB_TOKEN` to an approved, short-lived/rotatable least-privilege token with `read_api` and access only to intended projects. No token entry exists in the UI. Do not paste credentials into repository URLs, chats, screenshots or logs. Use your company's secret process for deployment; `.env` is a local convenience, not a secret vault.
4. Ensure the **backend container** has approved routing/DNS to that instance through the corporate network/VPN. Run `docker compose up --build` locally, then enter the GitLab project's normal HTTPS URL and optional ref.

The backend sends the token only to its configured GitLab instance, resolves a commit and reads API tree/blob data; it does not clone, execute builds or upload source to a paid analysis service. GitLab source/derived metadata is processed in your deployment, not sent to GitHub. Instance administrators still see ordinary API access. Build-time dependency/image downloads are separate network activity.

**TLS:** certificate/hostname verification stays enabled. For a corporate/private CA, have your administrator install the approved CA chain in the runtime's trusted CA configuration. Host OS trust does **not** automatically configure a container or Java: the container/JVM trust store must also trust the chain. This project does not bundle your corporate CA or auto-import it; use an approved derived image or read-only mounted Java trust store configured by your administrator. Do not disable TLS validation, accept arbitrary certificates or substitute HTTP to work around trust failures.

**Access boundary:** the service has no per-user login or project authorization layer. Anyone who can reach its API can use the configured token's project access. Keep the supplied loopback-only binding and use a minimally scoped token; do not run a shared privileged token on an exposed endpoint. `docker compose config` (without `--quiet`), container inspection, process diagnostics and debug HTTP logging can expose environment secrets; never publish those outputs. Avoid request/response body logs and sanitize any incident evidence.

GitLab access failures, rate limits, large repositories and deadlines can cause an error or explicitly partial report. `401/403` requires checking authorized token scope/access; `404` can mean inaccessible project/ref; redirects require fixing the configured canonical URL. TLS/VPN errors need administrator/network fixes, not relaxed security. No automatic retry loop hides failures. Live compatibility with your corporate instance, its API version/pagination behavior and CA must be verified on your authorized network before relying on results.

## What it analyzes

- Public `https://github.com/owner/repository` input, optional branch/tag/commit ref, resolved to one commit before fetching descriptors.
- Approved GitLab project URLs, including nested groups, on the single server-configured HTTPS instance. The caller cannot choose another server.
- Maven `pom.xml` files, literal dependencies in `build.gradle` / `build.gradle.kts`, and supported literal settings declarations/remappings in `settings.gradle` / `settings.gradle.kts`.
- Declared internal module edges, external dependency labels/counts, cycle groups, file counts and extension-based language counts.
- Searchable module/graph inspection and warning messages for known limitations.

Read warnings before interpreting a graph. A missing edge is not proof that no dependency exists. Maven parents, profiles, imported BOMs and effective-model semantics are not fully resolved. Gradle is a heuristic literal subset: literal includes and common projectDir remappings are supported, while dynamic settings, composite builds, scripts, plugins, computed dependencies and version catalogs are not fully evaluated. Language counts measure filenames, not lines of code. Cycle groups are not exhaustive ordered paths.

### Sharing and reproducibility

For **public GitHub only**, the share link encodes repository/ref inputs and **runs a fresh analysis** when opened. It is not a stored report or access-controlled document. A URL on `localhost` works only on the recipient's own running installation. Use an immutable commit SHA for repeatable source selection; branch names can move. Public GitHub results are cached in memory for five minutes by request input, so a moving branch may temporarily show a previous snapshot. Results identify the resolved commit. **All GitLab analyses are treated as private/local:** responses carry `repository.provider="gitlab"` and `shareable=false`; sharing and server result caching are disabled, even if a particular GitLab project happens to be public. The report still exists transiently in server/browser memory; no-cache is not secure erasure and does not prevent screenshots or copying.

## Security boundaries and current limits

Repository data is untrusted. The backend uses fixed GitHub API endpoints or the single operator-configured GitLab HTTPS API, does not follow redirects, checks GitHub public visibility even with a token, and fetches tree/blob content instead of cloning or extracting archives. XML parsing disables external entity/DTD processing. No analyzed wrapper, plugin, build command or repository process is executed.

Current limits are defined in backend source:

| Budget | MVP limit |
|---|---:|
| Concurrent analyses | 2 (additional requests receive 429) |
| Build/settings descriptors | First 100, settings first then lexical order |
| Descriptor decoded size | 512 KiB each |
| Total decoded descriptors | 8 MiB |
| GitHub tree response | 8 MiB |
| GitLab tree | Up to 100 pages × 100 entries; 8 MiB total; 1 MiB per page |
| API request body | 8 KiB |
| Dependencies | 500 per module / 10,000 total |
| XML nesting | 128 |
| Provider fetch budget | 45 seconds; per request up to 12 seconds |
| Public GitHub cache | Up to 32 results; five-minute TTL |
| GitLab result cache | Disabled |

Limits can yield partial results with warnings or an error. The fetch deadline is not a separately sandboxed hard CPU timeout for parsing. Each provider's own rate limits still apply; repositories with many descriptors can exhaust unauthenticated allowances. Optional `GITHUB_TOKEN` and configured `GITLAB_TOKEN` stay in the backend environment; never use a `VITE_*` token variable or commit `.env`.

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
