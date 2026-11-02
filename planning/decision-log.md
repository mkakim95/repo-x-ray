# Decision log

- Local-first MVP; no paid services required. GitHub's public API still has rate limits and availability constraints.
- Read-only analysis of repository descriptors; never execute analyzed project code.
- Separate implemented behavior from release targets in documentation. Public hosting requires additional hardening and explicit authorization.

- Corporate GitLab extension: one operator-owned HTTPS base URL and server-only read_api token. All GitLab reports bypass cache and sharing. Keep loopback deployment; require corporate network/CA/data-use approval. Earlier public-only planning language describes the original MVP, not a prohibition on this authorized extension.
