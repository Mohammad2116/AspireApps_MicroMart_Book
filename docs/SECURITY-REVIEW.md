# Security review and threat model

Trust boundaries: untrusted API client → bearer filter → scoped service transaction →
PostgreSQL; relay → Kafka; consumer → database; application → Redis; operator → metrics.
The issuer is trusted to assert tenant membership. Broker/database/Redis credentials,
network ACLs and TLS are deployment responsibilities. Local fixtures deliberately use
isolated loopback services and ephemeral credentials, not an internet-facing security setup.

| Threat | Control | Evidence |
|---|---|---|
| Forged/misissued token | RS256 verification, issuer/audience/time/subject/tenant validation | JWT negatives, local discovery and rotating JWK tests, real-container auth checks |
| Cross-tenant object access | Collision-safe tenant+subject owner predicates | Same subject in two tenants; denied reads and orders; unchanged stock |
| Duplicate order / oversell | Scoped unique key, advisory transaction lock, conditional stock decrement | Concurrent order/reservation and rollback tests |
| Refresh replay | Hash-only storage, family lock, atomic consumption, strict family revocation | Concurrent winner/replay, expiry and rollback tests |
| Duplicate event effects | Unique marker and effect in one transaction | Concurrent duplicate and real Kafka ACK/crash recovery |
| Stale relay worker | Random ownership token plus database-clock lease | Stale completion/failure rejection, claim recovery and final-attempt quarantine |
| Stale cache repopulation | Generation CAS on cache fill | Controlled in-flight invalidation race |
| Resource exhaustion | Bounded requests/pages, send/DB/Redis deadlines, pool/container limits | Validation and outage tests; local repeated workload |
| Sensitive logs / management | Safe Problem Details, metadata-only request logs, scoped metrics | Token/secret absence check, default-deny tests, authenticated scrape |
| Dependency compromise | Immutable Actions/tool checksums, pinned image, SBOM, scanner gate | Raw scan evidence and patch inventory |

CORS is not opened. CSRF is disabled only for the bearer-only API; no cookie credential flow
exists. Refresh methods do not constitute an authorization server or an account-bound login
product. Tokens are never issued by production HTTP endpoints. SQL uses bind parameters;
only application-owned constant diagnostic queries are selected for gauges.

## Dependency findings and treatment

Initial Boot 3.5.16 scan still reported known issues in Jackson, Netty, Tomcat, PostgreSQL,
OpenTelemetry, Log4j, Snappy and LZ4. Compatible patches are explicitly pinned in the POM;
the entire acceptance suite is rerun after those overrides. The full post-remediation scan
is retained rather than rewriting severity or hiding entries.

Two Spring MVC findings remain in the affected library version 6.2.19:

* CVE-2026-47884: the vendor describes XsltView plus implicit wildcard view resolution.
  This application has REST controllers, no XsltView or view-rendering route.
* CVE-2026-47890: the vendor describes SSE view fragments with attacker-controlled data.
  This application has no SSE or fragment rendering.

The scanner labels both CRITICAL; the vendor advisories label them MEDIUM and LOW,
respectively. Preserve both facts. Architecture tests guard absence of the required features,
and `security/advisory-review.json` is an expiring applicability review, **not a library
patch or proof of universal non-exploitability**. Vendor fixes on the 6.2 line require
commercial support; the public fix is on Framework 7. Boot 4 migration/paid support is not
silently undertaken for a Boot 3.5 teaching repository. Security scoring retains this gap.

Sources: https://spring.io/security/cve-2026-47884/ and
https://spring.io/security/cve-2026-47890/ (checked 2026-11-05).

The original Ubuntu runtime scan reported HIGH apt/libapt findings. A pinned minimal Alpine
Java 21 image replaces it. Its libexpat package is explicitly patched to 2.10.0-r0; the
release OS scan reports no vulnerabilities. See the final report for the exact image and scan evidence.
This is a code/configuration/scanner review, not penetration testing or security certification.
