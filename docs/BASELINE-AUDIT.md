# Independent baseline audit

Audit date: 2026-11-05. Baseline commit: `5c34cb23ef1a19df9d466c93f7b613112de5317c`.
The actual Git tree, Java classes, migration, POMs, workflow, Dockerfile, Compose, test
methods and saved chapter mapping were inspected before implementation changes.
`./mvnw -B -ntp clean verify -Pintegration` ran against the unchanged application:
**61 tests, 0 failures, 0 errors, 0 skips**. Fresh evidence: `reports/baseline-2026-11-05.json`.
This is a separate audit pass by the implementing agent, not independent third-party certification.

Locations below refer to the baseline tree; class names remain traceable after package moves.
No Critical finding was established. Missing evidence is not proof of an exploitable defect.

| ID / Severity | Location | Why it matters and supporting evidence | Remediation | Acceptance criterion |
|---|---|---|---|---|
| A01 High | `application/src/main/java/book/companion/OutboxRelay.java:29` | Transaction surrounds bounded Kafka send; one slow broker occupies locks and pooled connection. Existing crash test explicitly rolls back that transaction. | Short durable claims, lease token, independently committed completion and retries | Competing workers, expiry, stale-token fencing, ACK/commit crash and outage tests; no transaction during send |
| A02 High | `SecurityConfig.java:51`, `ProductController.java:32`, `OrderController.java` | Owner is only JWT subject; no tenant claim participates in SQL. Safe for documented single-owner namespace but not multi-tenant readiness. | Derive a collision-resistant tenant+subject scope from authenticated claims with documented legacy default; test same subject in different tenants | Cross-tenant read/write/idempotency tests and scoped cache keys |
| A03 Medium | `SecurityConfig.java:47`, `JwtValidationTest.java:27` | Signature/claim negatives exist; production decoder discovery and rotation only have one static key smoke test. Algorithm policy is implicit. | Explicit RS256 and rotating local JWK fixture | New and overlapping old keys accepted; unrelated key and wrong algorithm rejected |
| A04 Medium | `RefreshTokens.java:25–84` | Hash/row-lock rotation works, but issued family has no account binding, expiry is fixed, and no rollback/revoked-state test. It is explicitly a service example, not login. | Preserve non-public API, add rollback/expiry/revocation evidence and document issuance boundary | Transaction failure cannot consume old token; replay revocation persists; no claim of full auth server |
| A05 Medium | `ApiErrors.java:11`, `SecurityConfig.java:14` | 400/404/409 are Problem Details; filter-generated 401/403 are a different contract; missing header and unexpected error coverage absent. | Uniform safe RFC 9457 boundary, no exception details | Auth and application failure content-type/status tests; malformed input never leaks internals |
| A06 Medium | `RedisExamples.java:37–47` | A loader started before eviction can write stale data after eviction. Only happy-path Redis tests exist; no outage/reconnect proof. | Versioned cache invalidation/CAS and explicit per-use failure policy | Controlled stale-load race; real interruption and reconnect; limiter remains fail-closed |
| A07 Medium | `ProductRepository.java:10`, `V1__schema.sql:1`, `OrderService.java:54` | Correct stock and dedup tests, but no upgrade test, query-plan evidence, explicit pool budget or upper total bound. | Additive V2 migration, constraints/upgrade/EXPLAIN tests and bounded pool | Old rows preserved; Hibernate validates; plan/index and overflow outcomes recorded |
| A08 High | `Dockerfile:1`, `compose.yml:1` | Runtime image only built; no actual app-container, read-only FS, graceful stop/restart or dependency outage evidence. | Multi-stage/runtime targets, isolated runtime harness and resource restrictions | Start/readiness/auth/order/cache/Kafka, restart and SIGTERM checks with saved evidence |
| A09 Medium | `application.yml:51`, `OutboxRelay.java:45` | Health only; counters do not show backlog/lease age, logs unstructured, no request correlation or scraping evidence. | JSON logging, bounded IDs, tracing bridge, metrics and monitoring definitions | Actual scrape/query, alert-rule syntax, dashboard load and redaction checks where tools permit |
| A10 High | `.github/workflows/ci.yml:10`, `pom.xml:8` | Mutable action tags, one job, historical dependency baseline, no SBOM or vulnerability scan | Pin actions, separated stages, maintained compatible baseline, explicit scanner failure policy | Immutable refs; local scans/SBOM and findings recorded; hosted execution remains unclaimed |
| A11 Medium | `pom.xml:60`, `application/src/test/java` | Strong 61-test scenarios but no coverage measurement, architectural rule or mutation evidence | JaCoCo, architecture constraints, focused faults and repeated suite | Report actual line/branch coverage; critical gaps remain visible rather than excluded |
| A12 Medium | `docs/VALIDATION-MATRIX.md`, `README.md` | Honest limits but no performance harness, ADR/C4/runbooks, API compatibility policy or release scoring rubric | Add traceable documentation and repeated workload measurements | Every new feature mapped; all five scores tied to explicit gates, no automatic 9/10 |

## Priority and retained strengths

First fix durable publication ownership and tenant/security contracts, then test infrastructure
failure boundaries, container runtime and coverage. Preserve immutable framework-free domain,
server-priced orders, parameterized SQL, migration checks, atomic stock/version semantics,
transactional deduplication, strict refresh-family replay handling and secret-free source inputs.
Do not split this compact application into network services merely for appearance.
