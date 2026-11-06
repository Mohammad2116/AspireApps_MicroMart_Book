# Source provenance and adaptations

Read on 2026-11-04: the locally corrected `Modern_Java_Spring_Boot_Publishing_Final.docx`,
`Modern_Java_Spring_Boot_Final_QA_Report.md` and `Modern_Java_Spring_Boot_Change_Log.md`.
Their SHA-256 identities are recorded in [source-manifest.json](source-manifest.json).
The source documents are not modified or redistributed in this repository.

The QA baseline specifies Java 21, Boot 3.5.7, Spring Security 6.5, Hibernate 6.6,
Spring Kafka 3.3 and Testcontainers 1.21.3. The companion follows that baseline and completes
contextual fragments with domain types, schema, configuration, dependency wiring and tests.
Source report claims are not copied into current validation totals.

## Material adaptations

| Book sections | Adaptation / evidence |
|---|---|
| 2.3, 3.5–3.11 | Product DTO extended with initial stock, owner stored internally; exact monetary scale |
| 4.8 | Preserved conditional stock update + explicit version increment + flush/clear; PostgreSQL competition test |
| 5.7.2 | Composed issuer/time/audience validators; require subject and expiry; real signed-token tests |
| 5.9 | Executable strict refresh-family rotation; intentional concurrent-retry revocation policy |
| 7.3, 7.8, 7.12 | Scoped encoded keys, positive TTL, explicit failure propagation; real Redis tests |
| 8.9–8.11 | Short token-fenced lease claims, ACK-before-completion fault injection, atomic marker/effect, actual DLT consumption |
| 10.7, 11.7 | New evidence only; this does not rerun or certify old Lab07 reports |

## Official references consulted

* [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
* [Spring Security 6.5 JWT validation](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)
* [Testcontainers Docker 29 compatibility issue](https://github.com/testcontainers/testcontainers-java/issues/11210)
* [Docker Engine API](https://docs.docker.com/reference/api/engine/)

The rolling Boot documentation now describes a later 3.5 patch; it was used for compatibility
context, not to relabel the book's pinned baseline as current. Docker 29 rejected API 1.32;
`application/src/test/resources/docker-java.properties` explicitly selects API 1.44 while
preserving the book's Testcontainers baseline. Docker Engine 25+ is therefore required.

## Engineering maintenance revision

The engineering upgrade retains Java 21 and Boot 3.5, now at 3.5.16 with explicitly reviewed dependency patches. Version 2.0 adds repository-linked teaching material while retaining the original conceptual content. The prior teaching relay is replaced by the documented lease-based design. Baseline results, new test results and runtime evidence are separate datasets. See docs/BASELINE-AUDIT.md and the root engineering report.
