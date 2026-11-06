# Operational runbooks

These runbooks apply to the local reference implementation. Adapt thresholds, credentials,
retention, backups and access controls to the actual deployment before production use.
Never log bearer tokens or event payloads when diagnosing failures.

## Kafka

Trigger: send failures, growing pending age, consumer lag. Confirm broker reachability and
DNS from the application network, broker disk/controller health and topic ACLs. Orders
remain durable in PostgreSQL. Restore the broker, observe pending count/age decline and
compare accepted orders with analytics effects. Do not clear outbox or dedup markers.
Local evidence includes actual broker stop/restart and pending-order recovery. It does not
prove multi-broker partition failover or disk-loss recovery.

## PostgreSQL

Trigger: readiness DOWN, pool pending, 503s. Liveness should remain UP. Check connection
limits, pool saturation, storage, lock waits and statement timeouts. Restore connectivity
and require readiness recovery before restoring traffic. Review ambiguous client failures
using the original idempotency key; do not invent a new key for retries. Restore backups
into an isolated database first and compare migrations and business counts.

## Redis

Trigger: `book_redis_errors_total` or limiter 503s. The limiter fails closed. Check Redis
network, memory/eviction and latency; restore service, then verify the limiter recovers.
Cache generation keys must not be selectively deleted while loaders are active; use a
new namespace or a coordinated maintenance window. Never solve an outage by silently
allowing all security-sensitive requests.

## Outbox

Trigger: pending age >60 seconds for two minutes, FAILED rows, or stale completion count.
Inspect status, attempts, lease_until and last_error, without dumping payloads. Distinguish
healthy CLAIMED work from expired claims and poison records. Fix the dependency/data defect
first. Requeue a reviewed subset using a transaction, retaining event IDs, setting PENDING,
clearing token/lease and scheduling next_attempt_at; reset attempts only with an audit record.
This reference has no authenticated administrative requeue API; SQL access requires operator
controls. Monitor duplicate counts and business effects after recovery.

## DLT

Trigger: increased `book_consumer_dlt_total`. Retain original event ID, classify permanent
input defects versus exhausted transient errors, fix the cause, and select a bounded replay.
Never replay the whole topic automatically. A DLT send acknowledgment is not evidence of a
completed business effect. The repo tests poison routing; it does not provide a replay UI.

## Authentication

Trigger: elevated 401/403 or scrape `up=0`. Check issuer, audience, expiry, clock and key ID
without copying tokens into logs. 403 indicates missing scope or denied capability; 404 can
represent owner/tenant isolation. Confirm overlap during JWK rotation. An expired ops token
can look like a monitoring outage. Security advisory applicability review expires after
30 days and blocks the scan gate until reviewed again.

## Latency

Trigger: workload-specific p95/p99 regression. Compare request rate, pool wait, Kafka lag,
outbox age, Redis latency, CPU and GC. Reproduce with three warmed runs and a fixed workload.
Do not call a single local result a production SLA. Benchmark targets in this repo are local
regression budgets, not capacity guarantees.

## Deployment rollback

Build once, retain JAR/image digest and backup before migration. V2 changes the owner
namespace; **old pre-V2 binaries are not compatible with the upgraded owner data**. Prefer a
forward fix. A rollback across that boundary requires restoring the pre-upgrade database
snapshot and replay/reconciliation policy for writes accepted after the snapshot. Never
roll back only the application and assume Flyway undoes DDL. The upgrade path is tested;
old-version downgrade and production point-in-time recovery are not certified.


## Cache namespace reset

Generation fencing assumes counters survive. Do not flush a live namespace while loaders
are in flight. For destructive reset/old Redis backup restore, quiesce callers, rotate the
cache namespace or restart all loaders before resuming. Redis persistence/HA and recovery
from a regressed generation counter are not established by the pause/reconnect tests.
