# Backend

Java 25 / Spring Boot 4.0.0. Run `mvn verify` then `mvn spring-boot:run`, or build the included Dockerfile. Port 8080. Optional server-only `GITHUB_TOKEN` improves GitHub request allowance; never expose it in the frontend. No local storage or repository checkout is used.

## API

- `GET /api/health` → `{"status":"ok"}`
- `POST /api/analyze`, JSON `{"url":"https://github.com/owner/repo","ref":"optional branch, tag or commit SHA"}`.
- Success: `repository:{owner,name,url,ref}`, `nodes:[{id,label,path,buildTool,dependencies}]`, `edges:[{source,target}]`, `cycles:[[id]]`, `stats:{modules,edges,externalDependencies,cycles,filesScanned,totalFiles,languages}`, `warnings:[string]`.
- `repository.ref` is resolved immutable commit SHA. Use it in share links to reproduce a snapshot. Node IDs are `maven:path` / `gradle:path`, root path `.`. Edges point **from dependent to dependency**.
- `cycles` are strongly connected cycle groups, including self-loops, not every simple cycle. External dependencies count unique unresolved direct coordinates; unresolved Gradle project references are warned and excluded. `filesScanned` counts downloaded build files, including files failing parser validation; `totalFiles` counts blob entries in the available tree. `languages` maps selected filename extensions to file counts, not GitHub language bytes or full language detection.
- Errors `{error,message}`: 400 invalid input/public-only, 404 not found, 413 body too large, 429 busy/GitHub allowance, 502 upstream failure/oversize, 503 interrupted, 504 timeout. Partial results explicitly include warnings. GitHub 403 is deliberately surfaced as upstream access/rate-limit rather than claiming repository absence.

## Security and limits

Only exact HTTPS github.com owner/repository URLs accepted (no ports, credentials, query or fragment). Requests are constructed against fixed `https://api.github.com` only; caller URLs are never fetched. Redirects are disabled. Tree/blob IDs are validated, references encoded, private metadata refused even when a token can read it. Repository scripts, wrappers, plugins and dependencies are never run. XML DTDs and external entities disabled. Nothing is extracted to disk.

At most 2 analyses in flight. 32-entry in-memory result cache expires after 5 minutes; deployments should additionally use authenticated access or reverse-proxy per-client rate limiting if internet-exposed. Request body max 8 KiB. Upstream connect timeout 5 seconds, each full response max 12 seconds, analysis deadline 45 seconds, 8 MiB tree response, 100 build/settings files (settings first, then lexical order), each max 512 KiB decoded, total max 8 MiB source. Dependencies are deduplicated and capped at 500 per module / 10000 total, with partial warnings; unresolved project warnings capped at 20 plus count. XML depth capped at 128. Parsing adds a bounded amount of time after network deadline. Oversized trees fail safely; truncated trees and skipped files are clearly marked partial. No hidden unlimited retries. GitHub rate exhaustion after some build files yields partial results; exhaustion before analysis yields 429.

## Static-analysis limitations

This MVP discovers pom.xml/build.gradle/build.gradle.kts and settings.gradle/settings.gradle.kts, not necessarily only active reactor modules. Maven reads direct dependencies, inherits only parent groupId, and does not resolve properties, profiles, dependencyManagement, inherited dependencies or remote parents. Gradle recognizes literal project dependencies and common quoted external dependency coordinates; literal settings include declarations and projectDir=file('path') or File(rootDir/settingsDir, 'path') mappings are supported. Declared modules without build files are included. Nested settings scope aliases to the nearest build root. Settings contribute up to 400 declared modules and never access the filesystem. No DSL execution, computed settings/projectDir evaluation, version catalogs or convention plugin resolution. Triple-quoted/slashy strings and nested Kotlin comments are not fully tokenized. Conditional declarations are approximated. Dynamic builds can be incomplete. Mixed build files at the same directory produce separate modules. Dependency versions are intentionally not part of graph identity; Maven matching is groupId:artifactId only and explicitly warned. Ambiguous duplicate Maven coordinates do not create internal edges. Languages only cover common source extensions. No source-level class/call graph.

`CoreChecks` provides a dependency-free executable parser/URL regression suite; `StaticAnalyzerTest` invokes it under JUnit. Maven/Java25 and live GitHub integration verification must be reported separately from static inspection.

Local verification: `sh test-core.sh` passed on the available Java 21 compiler module. This is not a full Spring Boot build, filter JUnit execution, Java 25 validation, or live GitHub integration test.

Additional verified core cases: Kotlin/Groovy settings includes, buildless modules, projectDir aliases, outgoing dependencies from mapped modules, nested build scoping, unsafe paths, and dynamic-settings warnings.
