# Architecture decisions

The system remains one modular deployable application. Its small framework-free `domain`
module holds validated money and reservation transitions. The application module uses
feature packages (`inventory`, `orders`, `security`, `outbox`, `messaging`, `cache`, `web`).
Spring transaction boundaries are explicit infrastructure choices; this is pragmatic
layering, not a claim that the entire application is framework-independent.

## ADR 001 — Preserve a modular monolith

Accepted. Avoid network services without independent scaling/ownership requirements.
ArchUnit checks the domain's independence, forbids web→database access and detects cycles
between feature packages. Controllers exchange records, never persistence entities.
The orders use case intentionally coordinates JPA and JDBC under one transaction manager.

## ADR 002 — Durable outbox leases

Accepted. Replace network-held row locks with a one-row claim in a short `REQUIRES_NEW`
transaction. The database clock owns expiry. Claiming increments attempts and assigns a
random token. Send occurs without a database transaction; acknowledged completion is a
separate conditional update requiring the same token and a live lease. Claims are acquired
one at a time so a later row does not lose lease time waiting behind a batch.

State machine: `PENDING → CLAIMED → PUBLISHED`; send failure returns to `PENDING` with
exponential bounded delay, or `FAILED` after eight attempts. An expired `CLAIMED` row can
be reclaimed with a new token; expiry on the final attempt quarantines it. A late worker
cannot finalize or reschedule a newer owner's row. The loop is bounded to twenty rows. A configurable 250 ms fixed delay avoids the
measured backlog growth caused by the previous 1-second pause at roughly 27 orders/s.
This increases idle polling to at most four claims/s; it is not a throughput guarantee.
Lease 30 seconds exceeds the 10-second send wait; claim/complete transactions time out in
five seconds. Attempts include crashed claims. `FAILED` rows require deliberate operator
review; there is no automatic infinite replay or silent deletion.

Delivery remains **at least once**. A send can succeed before the process dies or before
completion persistence fails. The consumer's marker and analytics effect commit together.
No distributed exactly-once, fairness, aggregate sequence guarantee or multi-broker HA is
claimed. Database leases prevent stale finalization, not duplicate Kafka publication.

## ADR 003 — Tenant-scoped identity

Accepted. A trusted issuer supplies `sub` and optional `tenant`. Without tenant the owner
is `legacy#subject`; with tenant it is a delimiter-safe encoded tenant/subject pair. V2
migrates previous owner values to the legacy namespace. All HTTP inventory/order predicates
use this identity, including idempotency keys; request bodies cannot select an owner.
The optional tenant contract preserves existing single-tenant tokens. Membership changes
and tenant authorization remain responsibilities of the issuer; this is not a membership
administration system. Refresh token methods remain internal examples, not public login.

## ADR 004 — API compatibility

Accepted. `/api/v1/products` and `/api/v1/orders` alias the original `/api/products` and
`/api/orders`. Existing response fields and Location paths remain valid. Removing a field,
changing meaning/status or changing authentication contracts requires a new major route
or a documented migration. New optional fields are additive. Filter and application errors
use RFC 9457 Problem Details with safe messages. The new limiter endpoint returns 429
with Retry-After; an unavailable Redis returns 503 and does not grant access.

## ADR 005 — Redis consistency and failure policy

Accepted. Display-cache invalidation atomically increments a generation and deletes the
entry. A loader may publish only if the generation still matches. An already-running read
may return its old source result, but cannot repopulate the cache after invalidation.
Generation keys intentionally do not expire (avoids ABA); plan cardinality and retire whole
namespaces deliberately. The generation guarantee assumes Redis retains those counters;
a destructive flush or restoration to older data requires coordinated cache namespace
rotation and restarting in-flight loaders. Cache and limiter errors propagate; there is no automatic database
fallback that could cause a stampede. Future optional noncritical caches may adopt bounded
fallback only with separately tested capacity and freshness budgets. Fixed windows still
permit boundary bursts. Rate identity/window comes from validated principal/server time.

## ADR 006 — Compatible security maintenance

Accepted. Preserve Spring Boot 3.5 for book alignment, upgrade its public patch baseline and
apply explicit compatible dependency patches supported by the full suite. Keep raw scanner
findings. Spring MVC view/SSE advisories are conditionally non-applicable to this REST-only
implementation; an expiring review and architecture rules guard that conclusion. The
library itself is not patched. A Boot 4 migration or supported commercial 6.2 patch is a
separate maintenance decision; neither paid access nor public publication is authorized.

## ADR 007 — Runtime and evidence

Accepted. Multi-stage source Docker build and a target for the already tested artifact;
non-root Alpine JRE runtime, read-only root, bounded resources and tmpfs in validation.
A portable Java health probe uses readiness. Readiness includes PostgreSQL; liveness does
not depend on downstream availability. Kafka outage must not prevent accepting durable
orders, while Redis limiter outage must reject the limiter operation.
Local OIDC fixture keys are generated in memory. Prometheus gets only an ops-scoped token.
No token is committed; no management env/heap endpoint is exposed. JSON logs contain bounded
request IDs and OTel trace IDs, but no application logging of credentials or payloads.
