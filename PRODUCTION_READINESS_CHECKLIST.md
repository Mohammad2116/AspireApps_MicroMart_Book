# Production-oriented readiness checklist

This is a tested educational reference, not a production certification.

## Verified locally

- [x] Java 21/Maven wrapper, module boundaries and formatting.
- [x] Strict signed bearer validation, negative claims/scope/tenant tests.
- [x] Hashed refresh rotation, replay and transaction rollback example.
- [x] PostgreSQL constraints, concurrent stock/idempotency, representative V1→V2 upgrade.
- [x] Lease outbox with fenced completion, bounded attempts and idempotent consumer effects.
- [x] Redis TTL/invalidation-race/atomicity/outage recovery.
- [x] Complete module coverage gates, controlled stale-owner fault detected.
- [x] Actual non-root/read-only bounded Docker runtime and health checks.
- [x] Service outages/recovery, logical backup restore, restart persistence and SIGTERM.
- [x] JSON correlation/trace logs, protected scrape, Grafana definition import/readback.
- [x] Alert syntax plus synthetic quarantine firing test.
- [x] Three warmed local k6 runs with latency/error/resource/backlog measurements.
- [x] SBOM, static analysis, secrets and container OS/dependency scanning.
- [x] Immutable Actions/tool/image references and minimum CI permissions.

## Deployment gates not satisfied by this local reference

- [ ] Resolve the two conditionally reviewed Spring MVC advisories with a supported patch
      or a separately assessed Boot major-version migration; re-review before expiry.
- [ ] Run hosted CI in the actual organization; no workflow was published or dispatched.
- [ ] Select trusted production issuer, tenant-membership lifecycle, TLS/network policy,
      secrets manager, credential rotation, and edge-wide abuse protection.
- [ ] Execute representative sustained/saturation load and define actual SLO/RTO/RPO.
- [ ] Exercise multi-node failover, partitions, backup retention/encryption and restore drills.
- [ ] Validate end-to-end exported traces and alert routing to real on-call recipients.
- [ ] Review retention and growth: outbox/consumer marker rows, cache generation keys, DLT.
- [ ] Establish safe manual replay procedures and accountable operating ownership.
- [ ] Do not deploy a pre-V2 binary against V2 owner identifiers; use forward repair or
      coordinated restoration of a pre-upgrade database backup. No zero-downtime downgrade
      compatibility is asserted.
- [ ] Perform organization-specific security review and representative penetration testing.

These gates require deployment policy, infrastructure or a separately scoped compatibility
migration. No external resources were changed to simulate their completion.
