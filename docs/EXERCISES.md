# Chapter exercises and evidence

1. **Ch 1.4 / 1.6:** Remove `domain` from the application's dependencies while leaving it in
   `<modules>`. Explain why reactor membership alone does not put `Money` on the classpath.
   Restore the dependency and run `./mvnw test`.
2. **Ch 2.3:** Try `Money(1.001)` and quantity zero. Explain why silently rounding input could
   conceal an upstream contract error. `MoneyTest` documents canonical scale and arithmetic.
3. **Ch 3.5–3.11:** Add a new DTO field and preserve the validation/error/Location contract.
   Use `ProductControllerTest` for the HTTP boundary and `HttpApiIT` for complete wiring.
4. **Ch 4.8 / 4.13:** Read `competingReservationsNeverOversell`. Sixteen callers compete for
   five units. Predict orders, stock and outbox count. Replace the atomic update with a
   check-then-save in a disposable branch and explain the resulting race.
5. **Ch 5.7–5.9:** Distinguish a valid signature from a valid issuer/audience/expiry/subject.
   Explain why strict refresh reuse may log out a legitimate retrying client.
6. **Ch 6.11:** `Reservation` models idempotent local state transitions. Design the missing
   persisted version check, timeout and compensation contract before extracting a service.
7. **Ch 7.12:** Explain why 40 concurrent limiter calls allow exactly seven here, yet a fixed
   window can still permit a boundary burst. Design a test for stale cache repopulation.
8. **Ch 8.9–8.11:** Inspect the ACK-before-commit test. Explain why one analytics row alone
   does not prove a duplicate was consumed. Then inspect the positive duplicate counter.
9. **Ch 9:** Build the tested JAR and runtime image. Explain why the caller's `localhost`
   changes meaning in Docker. Never claim a successful image build proves healthy startup.
10. **Ch 10.7:** Regenerate test evidence with `scripts/test_report.py`. Distinguish old
    reports, current execution, a compiled test and a test that actually ran.
11. **Ch 11:** Map MicroMart to this order/outbox slice. Linker and CopyTrader remain design
    case studies; this repository does not execute redirects or trades.
12. **Ch 12:** Propose a production acceptance plan: identity-provider integration, tenancy,
    migration compatibility, load/latency targets, broker outage/restart, observability,
    deployment rollback and DLT replay. Name which items have no evidence here.

Run a targeted integration test from the reactor, for example:
`./mvnw -Pintegration -Dit.test=DatabaseIT -Dfailsafe.failIfNoSpecifiedTests=false verify`.
The complete acceptance command remains `./mvnw clean verify -Pintegration`.

## Engineering upgrade exercises

* Chapters 4/8: claim a row, expire its lease using database time, then attempt completion with the old token. Explain why fencing does not prevent duplicate publication. See DatabaseIT and OutboxStore.
* Chapter 5: use the same subject in two signed tenant tokens. Explain why owner values are encoded and why the issuer must validate membership. See HttpApiIT.
* Chapter 7: block a source loader, invalidate the key, release the loader. Explain why the original reader may see old data but the cache may not be repopulated. See RedisIT.
* Chapters 9/10: run the runtime harness. Stop/restart Kafka, then compare the durable order, outbox and effect. Explain why readiness includes PostgreSQL but liveness does not.
* Chapter 10: inspect JaCoCo and static/security findings. Explain the difference between conditional advisory applicability, a patched library and a false positive.
* Chapter 12: compare the three k6 distributions and their resource samples. Do not generalize these short local runs into a production SLA.
