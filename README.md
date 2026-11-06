# AspireApps MicroMart Book Companion

Executable Java 21 / Spring Boot 3.5 companion for **Modern Java & Spring Boot — From Core
Concepts to Production-Ready Backend Systems**, Version 2.0.

This modular application connects inventory, tenant-scoped orders, PostgreSQL transactions,
a lease-based Kafka outbox, idempotent analytics, Redis examples and operational checks.
It is an educational reference with explicit limits, not an internet-ready deployment.

## Start here

Install Java 21, unzip on Unix, and Docker Engine 25+ with a reachable daemon. From the
repository root, run:

```sh
./mvnw -B -ntp spotless:check clean verify -Pintegration,sbom
```

The wrapper pins Maven 3.9.9 and verifies its distribution checksum. Fresh builds download
public dependencies and images. Missing Docker fails integration acceptance rather than
silently skipping it. `.mvn/jvm.config` normalizes build timezone to UTC.
`./mvnw test` runs only the unit/MVC/security/architecture subset.
Coverage gates are 85% line and 75% branch per production module without exclusions.

## Navigate with the book

Read [the chapter matrix](CHAPTER_MATRIX.md), [guided exercises](docs/EXERCISES.md),
[architecture decisions](docs/ARCHITECTURE_DECISIONS.md), [diagrams](docs/DIAGRAMS.md),
[API contract](docs/API.md), and [runbooks](docs/RUNBOOKS.md).
The book's source links use the immutable `book-v2.0.0` tag or an exact commit.
Conceptual Linker/Saga/cloud examples in the book are not claims of implemented services.
Historical MicroMart Lab07 test totals are separate from this companion's tests.

## Runtime validation

```sh
mkdir -p ops/health
javac -d ops/health ops/HealthProbe.java
docker build --target runtime-from-tested -t book-companion:excellence .
sh scripts/install_quality_tools.sh
python3 -m venv .quality-venv
.quality-venv/bin/pip install -r scripts/runtime-requirements.txt
.quality-venv/bin/python scripts/runtime_validation.py --k6 .tools/k6-v2.3.0-linux-amd64/k6
```

The harness is Linux-only and uses disposable loopback services, ephemeral OIDC keys,
resource-limited containers, real outage/recovery, monitoring and repeated load checks.
Run on a development machine with adequate available RAM, not a production host.
Default `docker build -t book-companion:source .` builds from source; release tests remain
a separate required step. Configure `.env.example` values for manual Compose use.
No public login or token-issuance endpoint is included. Legacy API routes and `/api/v1`
aliases coexist; scopes and tenant claims come from a trusted issuer.

## Evidence, security and license

See [test evidence](TEST_EVIDENCE.md), [engineering evidence](ENGINEERING_EXCELLENCE_REPORT.md),
[security review](SECURITY_REVIEW.md), [performance scope](PERFORMANCE_REPORT.md),
and [deployment checklist](PRODUCTION_READINESS_CHECKLIST.md).
Two reviewed Spring MVC advisories remain in the affected library; the policy expires
2026-12-05 and must be re-reviewed. At-least-once event delivery is explicit.

Project-authored code and repository docs use [MIT](LICENSE). The separately delivered book
retains its existing attribution and is not covered by this code license. See
[third-party notices](THIRD_PARTY_NOTICES.md) and [publication provenance](PUBLICATION_PROVENANCE.md).
