# Book v2.0 companion walkthroughs


These are repository-linked additions for all twelve chapters. Links target the fixed book-v2.0.0 tag. Run commands from the repository root. Integration chapters deliberately run the full suite so coverage gates are not invalidated by a filtered test subset.


## Chapter 1 — Build the same application from a clean checkout


### Concept
A build is an executable statement about the project, not a collection of IDE settings. The companion has two modules: domain contains independent Java values and transitions; application contains the Spring runtime, adapters and tests. The root POM is both aggregator and parent. Maven orders modules by dependencies, so application can use domain without installing it manually. This actual two-module reactor is the running project throughout Version 2.0; the larger Linker layouts retained in this chapter are conceptual design exercises.


### Real implementation
[pom.xml](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/pom.xml), [application/pom.xml](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/pom.xml), [.mvn/wrapper/maven-wrapper.properties](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/.mvn/wrapper/maven-wrapper.properties), [.mvn/jvm.config](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/.mvn/jvm.config), [.github/workflows/ci.yml](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/.github/workflows/ci.yml)


Excerpt from [pom.xml, lines 14–17](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/pom.xml#L14-L17):

```
  <modules>
    <module>domain</module>
    <module>application</module>
  </modules>
```


### Code walkthrough
Read the modules element, then follow application/pom.xml to its dependency on domain. The Java 21 Enforcer rule rejects incompatible toolchains. The wrapper pins Maven 3.9.9 and checks the downloaded distribution; unzip is a Unix prerequisite because the wrapper must use the ZIP whose checksum was pinned. A fixed output timestamp and UTC Maven timezone make the observed host, extracted and Docker JARs byte-identical. Neither a cached build nor a successful package alone establishes integration correctness. The integration profile invokes Failsafe and whole-module coverage gates; the SBOM profile inventories dependencies. CI separates unit, integration, security and packaging work, but its actual hosted result must be read from the workflow run, not inferred from the YAML.


### Run the test

[application/src/test/java/book/companion/ArchitectureTest.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/ArchitectureTest.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
With Java 21, unzip and a working Docker daemon, the full command should end in BUILD SUCCESS. The publication acceptance contains 106 tests with no failures, errors or skips. Domain is built before application; unavailable Docker makes integration acceptance fail. This result belongs to the companion, not historical Lab07.


### Reader experiment
Create a disposable branch. Add a Spring type reference inside a domain class, with the dependency needed to compile it, and rerun the suite. Explain which architecture rule detects it and why merely building successfully would be insufficient. Revert the experiment before other chapters. Separately compare two JAR checksums after repeated packaging; record toolchain and timezone before interpreting a difference.


### Architectural connection
The build boundary supports every later promise: transaction and security tests are meaningful only when the reader runs the same source and profiles. Chapter 9 turns this artifact into a container, while Chapter 10 explains the distinct evidence collected at each stage.


Limit: Fresh builds require public downloads. Hosted CI success is a separately observable result; no universal cross-JDK or OCI-image reproducibility is promised.

## Chapter 2 — Make invalid monetary states difficult to represent


### Concept
Money is a value with invariants, not an arbitrary floating-point number. The companion uses BigDecimal and a record so equality and value semantics are explicit. The compact constructor is the single point where null, scale and sign are checked. Reservation supplies a second example: a transition returns a new value rather than mutating shared state. Neither class imports Spring, JDBC or HTTP types.


### Real implementation
[domain/src/main/java/book/companion/domain/Money.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/main/java/book/companion/domain/Money.java), [domain/src/main/java/book/companion/domain/Reservation.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/main/java/book/companion/domain/Reservation.java)


Excerpt from [domain/src/main/java/book/companion/domain/Money.java, lines 8–18](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/main/java/book/companion/domain/Money.java#L8-L18):

```
public record Money(BigDecimal amount) {
  public Money {
    Objects.requireNonNull(amount);
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    if (amount.signum() < 0) throw new IllegalArgumentException("Negative amount");
  }

  public Money times(int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("Positive quantity required");
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
  }
```


### Code walkthrough
The scale operation uses UNNECESSARY, so excess fractional precision is rejected rather than silently rounded. A nonnegative amount permits a zero Money value, while product creation separately requires a positive price; these are deliberately different domain and API rules. times rejects nonpositive quantities and constructs another Money, reapplying the invariant. The record does not solve every financial modeling problem: there is no currency identifier, exchange rate or configurable rounding policy. Those omissions make the example focused rather than universally suitable.


### Run the test

[domain/src/test/java/book/companion/MoneyTest.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/test/java/book/companion/MoneyTest.java)

```sh
./mvnw -B -ntp -pl domain -Dtest=MoneyTest test
```


### Expected result
MoneyTest passes exact arithmetic and invalid-value cases. Constructing a negative amount or multiplying by zero must fail; a value requiring rounding must not quietly become a nearby amount. The focused command runs five Money tests without Docker. ReservationTest is a separate five-test class.


### Reader experiment
Add a currency field in a disposable branch. Define equality and multiplication behavior before editing the record, and add a test that rejects adding unlike currencies. Do not change the existing monetary contract by silently introducing HALF_UP rounding. Explain where an explicit settlement rounding policy would belong.


### Architectural connection
Chapter 4 and the order service reuse Money to compute totals from the server-side product price. A small independent domain value therefore protects a database transaction without knowing about persistence. Chapter 6 reuses the same independence when discussing state transitions and extraction boundaries.


Limit: Money has no currency model. Immutable values do not by themselves supply persistence concurrency control.

## Chapter 3 — Follow a request across the HTTP boundary


### Concept
The controller translates an authenticated HTTP request into a use case. It should not expose JPA entities or accept a client-selected owner. The product request record defines the public input contract; a separate view record defines the response. This keeps persistence version fields and ownership internals out of the API while allowing validation close to the input boundary.


### Real implementation
[application/src/main/java/book/companion/web/ProductController.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/ProductController.java), [application/src/main/java/book/companion/inventory/ProductService.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/inventory/ProductService.java), [application/src/main/java/book/companion/web/ApiErrors.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/ApiErrors.java)


Excerpt from [application/src/main/java/book/companion/web/ProductController.java, lines 25–28](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/ProductController.java#L25-L28):

```
  public record Create(
      @NotBlank @Size(max = 120) String name,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
      @Min(0) int stock) {}
```


### Code walkthrough
The Create record rejects blank or oversized names, null or nonpositive prices, excessive precision and negative stock. The controller derives ownership from the verified JWT and delegates to ProductService. Both legacy routes and /api/v1 aliases map to the same implementation; Location remains a valid legacy URL for compatibility. List requests have bounded page sizes, and the service orders by name plus id so equal names do not make page ordering ambiguous. ApiErrors produces safe Problem Details; the security filter uses its own handler because an authentication failure can occur before MVC.


### Run the test

[application/src/test/java/book/companion/HttpApiIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/HttpApiIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
ProductControllerTest checks DTO behavior; HttpApiIT sends actual signed requests through a running HTTP server. Invalid input yields 400, an absent token 401, insufficient scope 403, and an object outside the caller's ownership namespace is not returned. A successful create returns 201 and a usable Location. DatabaseIT checks stable pagination ties.


### Reader experiment
Create two products with the same name and request one-item pages. Record their ids and verify the tie-break ordering. In a disposable branch, remove the id tie-breaker and explain why a test must exercise duplicate names rather than only distinct names. Add a compatibility assertion that both route versions return the same response shape.


### Architectural connection
Validation protects the edge; it does not replace the stock predicate or database constraints in Chapter 4. Authentication establishes a principal, while Chapter 5 explains why object-level ownership must still participate in queries. The HTTP response is only the start of the eventual event flow traced in Chapter 11.


Limit: No browser frontend is included. Pagination is bounded offset pagination, not a snapshot guarantee while concurrent writes occur.

## Chapter 4 — Keep the inventory invariant inside the transaction


### Concept
Reading stock and later writing a reduced value can oversell under concurrency. The companion instead performs a conditional database update: decrement only if the owner matches and enough stock remains. The affected-row count is part of the business decision. This is distinct from optimistic locking with an expected version; both mechanisms appear in the project and should not be confused.


### Real implementation
[application/src/main/java/book/companion/inventory/ProductRepository.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/inventory/ProductRepository.java), [application/src/main/java/book/companion/orders/OrderService.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/orders/OrderService.java), [application/src/main/resources/db/migration/V1__schema.sql](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/resources/db/migration/V1__schema.sql), [application/src/main/resources/db/migration/V2__leases_and_owner_scope.sql](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/resources/db/migration/V2__leases_and_owner_scope.sql)


Excerpt from [application/src/main/java/book/companion/inventory/ProductRepository.java, lines 14–19](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/inventory/ProductRepository.java#L14-L19):

```
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Product p set p.stock=p.stock-:quantity, p.version=p.version+1 where p.id=:id and"
          + " p.owner=:owner and p.stock>=:quantity")
  int reserveStock(
      @Param("id") UUID id, @Param("owner") String owner, @Param("quantity") int quantity);
```


### Code walkthrough
reserveStock increments the version explicitly because bulk JPQL bypasses ordinary managed-entity updates. Automatic flush and clear prevent a stale persistence-context value from masking the database change. OrderService.place owns the surrounding transaction: idempotency handling, server-side price calculation, stock reservation, order insertion and outbox insertion succeed or roll back together. A transaction-scoped advisory lock serializes the same owner/request key, while a unique constraint remains the durable safeguard. Flyway V1 defines the base schema; V2 adds lease state, owner namespaces and constraints. Hibernate validates rather than silently modifying that schema.


### Run the test

[application/src/test/java/book/companion/DatabaseIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/DatabaseIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
DatabaseIT proves no oversell under competing reservations, one order for simultaneous identical requests, conflict on a reused key with different input, rollback after an outer failure and invalidation of a stale version. MigrationIT seeds a V1 database, upgrades to V2 and checks preserved rows and index use with representative data.


### Reader experiment
In a disposable branch remove the stock>=quantity predicate, then run the full acceptance suite. Identify the failed invariant before reading the stack trace. Restore it, then inspect the query plan produced by MigrationIT. Explain why an index observed with 10,000 rows is evidence for that data distribution rather than proof the planner will always choose it.


### Architectural connection
The transaction writes durable publication intent but never claims that PostgreSQL and Kafka share an atomic commit. Chapter 8 handles the second boundary with leases and idempotency. The migration changes owner identifiers, so Chapter 9 must treat rollback as a compatibility decision rather than simply restarting an old binary.


Limit: Pre-V2 binaries are not compatible with V2 owner namespaces. A representative forward upgrade was tested; a zero-downtime downgrade was not.

## Chapter 5 — Separate a valid token from permission to act


### Concept
A signature is necessary but not sufficient. The resource server also validates the issuer, audience, time bounds, subject and optional tenant claim. After authentication, scopes control operations and the owner namespace controls which objects may be read or changed. The trusted issuer is responsible for asserting legitimate tenant membership; an arbitrary request body or header cannot grant it.


### Real implementation
[application/src/main/java/book/companion/security/SecurityConfig.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/security/SecurityConfig.java), [application/src/main/java/book/companion/security/OwnerScope.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/security/OwnerScope.java), [application/src/main/java/book/companion/security/RefreshTokens.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/security/RefreshTokens.java), [application/src/main/java/book/companion/security/ProblemSecurityHandler.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/security/ProblemSecurityHandler.java)


Excerpt from [application/src/main/java/book/companion/security/SecurityConfig.java, lines 58–64](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/security/SecurityConfig.java#L58-L64):

```
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withIssuerLocation(issuer)
            .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(validators(issuer, audience));
    return decoder;
  }
```


### Code walkthrough
SecurityConfig builds an RS256 decoder and composes the default issuer/time validator with the companion claim contract. Missing audience must produce a rejected token rather than a null-pointer failure. OwnerScope encodes tenant and subject without delimiter ambiguity, or uses a documented legacy namespace when tenant is absent. Default-deny routing closes unspecified endpoints. RefreshTokens is an internal persistence example: only hashes are stored, rotation consumes a token atomically and replay revokes the family. Its strict policy means a duplicate concurrent refresh can revoke the winning successor; clients must understand this trade-off. It is not a public login system or an authorization server.


### Run the test

[application/src/test/java/book/companion/JwtValidationTest.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/JwtValidationTest.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
JwtValidationTest and SecurityContractTest reject wrong or absent claims. OidcRotationTest uses local discovery and JWK fixtures for overlapping keys, unknown keys and algorithm restrictions. HttpApiIT verifies 401/403 behavior and same-subject cross-tenant isolation. DatabaseIT covers rotation concurrency, expiry, replay and rollback.


### Reader experiment
Construct two signed fixture tokens with the same subject but different tenants. Create an object with one, then attempt to read and order it with the other; verify stock remains unchanged. In a disposable test copy remove audience validation and demonstrate which negative tests turn red. Never print access or refresh tokens while diagnosing the failure.


### Architectural connection
Chapter 3's DTO boundary and Chapter 4's owner predicates make these decisions operational. Chapter 10 exposes safe diagnostics without turning management endpoints into a secret-disclosure channel. The security review retains two conditionally applicable Spring MVC advisories; passing functional tests does not erase affected-library maintenance obligations.


Limit: No public token issuance, tenant administration or production identity-provider deployment is included. CSRF is disabled for this bearer-only API, not as general browser advice.

## Chapter 6 — Use local transitions to reason about distributed boundaries


### Concept
The companion is a modular application, not a fleet of microservices. Its feature packages and independent domain module make responsibilities visible without adding remote failure modes. Reservation is a local immutable transition model useful for discussing a reservation workflow. It is not persisted Saga orchestration and is not wired into the order path.


### Real implementation
[domain/src/main/java/book/companion/domain/Reservation.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/main/java/book/companion/domain/Reservation.java), [application/src/test/java/book/companion/ArchitectureTest.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/ArchitectureTest.java)


Excerpt from [domain/src/main/java/book/companion/domain/Reservation.java, lines 19–29](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/main/java/book/companion/domain/Reservation.java#L19-L29):

```
  public Reservation confirm() {
    if (state == State.RELEASED)
      throw new IllegalStateException("Released reservation cannot be confirmed");
    return new Reservation(id, State.CONFIRMED);
  }

  public Reservation release() {
    if (state == State.CONFIRMED)
      throw new IllegalStateException("Confirmed reservation requires a separate compensation");
    return new Reservation(id, State.RELEASED);
  }
```


### Code walkthrough
confirm rejects a released reservation; release rejects a confirmed one because compensation is a separate business action. Repeating an already accepted state produces the same logical state. These checks describe permitted transitions but cannot prevent two processes from writing competing persistent states. A distributed extraction would additionally need durable state/version checks, correlation, timeouts, retry semantics and compensating actions. The chapter's discovery, configuration-server and Saga diagrams remain conceptual alternatives, not services present in the tagged repository.


### Run the test

[domain/src/test/java/book/companion/ReservationTest.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/domain/src/test/java/book/companion/ReservationTest.java)

```sh
./mvnw -B -ntp -pl domain -Dtest=ReservationTest test
```


### Expected result
ReservationTest verifies permitted, repeated and forbidden local transitions. The focused command runs five tests without Docker. ArchitectureTest separately guards dependency boundaries in the complete application. Neither result demonstrates distributed consensus, service discovery, network partitions or a completed Saga.


### Reader experiment
Write a state table for RESERVED, CONFIRMED and RELEASED, including invalid transitions. Add a proposed EXPIRED state on a disposable branch and define what a late confirm should do. Then sketch two concurrent database updates and explain where an expected-version predicate would be required. Keep the proposed persistence adapter explicitly separate from the existing implementation.


### Architectural connection
Chapter 4 supplies the transaction and concurrency vocabulary that a future persistent workflow would need. Chapter 8 supplies a publication mechanism, but an outbox alone does not implement compensation. The useful extraction question is which invariant can move without making a formerly local transaction an undocumented distributed promise.


Limit: No Eureka/Config Server deployment, distributed Saga coordinator or microservice extraction is implemented.

## Chapter 7 — Make cache invalidation and failure policy explicit


### Concept
A cache can return stale data even when eviction is called correctly. If a loader reads old source data, eviction occurs, and that loader later stores its result, it repopulates a stale entry. The companion uses a generation counter: a fill is accepted only if the generation observed before loading still matches. Tenant and resource segments are encoded so separators cannot create namespace collisions.


### Real implementation
[application/src/main/java/book/companion/cache/RedisExamples.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/cache/RedisExamples.java), [application/src/main/resources/rate-limit.lua](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/resources/rate-limit.lua), [application/src/main/java/book/companion/web/RateLimitController.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/RateLimitController.java)


Excerpt from [application/src/main/java/book/companion/cache/RedisExamples.java, lines 60–66](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/cache/RedisExamples.java#L60-L66):

```
  public void evict(String tenant, String resource) {
    String k = key(tenant, resource);
    redis.execute(
        new DefaultRedisScript<Long>(
            "redis.call('INCR',KEYS[2]); return redis.call('DEL',KEYS[1])", Long.class),
        List.of(k, k + ":generation"));
  }
```


### Code walkthrough
cached reads value and generation together, loads on a miss and uses Lua compare-and-set before storing with a TTL. evict increments generation and removes the cached value atomically. Both keys share a Redis hash tag for script key locality. An in-flight caller can still receive its already-loaded old value; the guarantee is that it cannot repopulate the cache after invalidation. Generation keys intentionally do not expire to avoid an ABA reset. The rate limiter uses one atomic script and a trusted principal/server window. Its fixed-window boundary can permit bursts, and the executable endpoint is not a global API firewall.


### Run the test

[application/src/test/java/book/companion/RedisIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/RedisIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
RedisIT checks cache hit/miss, TTL, eviction, namespace collisions, concurrent limiting and a controlled stale-loader race. It also pauses a real Redis container, observes failure and verifies recovery after unpause. The limiter fails closed; errors are observable through a counter and latency timer.


### Reader experiment
Pause the loader between reading source data and publishing its value, evict the key, then release the loader. Assert that the next cache lookup reloads rather than reading the stale fill. Compare this with merely asserting that DEL was called. Design a bounded fallback policy for noncritical display data and state how database overload would be prevented before implementing it.


### Architectural connection
Tenant identity comes from Chapter 5, while Chapter 10 provides the metrics needed to distinguish denied requests from infrastructure failure. A destructive Redis flush or restoration to older counters breaks generation assumptions; the runbook requires coordinated namespace reset and loader restart. Persistence and HA guarantees cannot be inferred from a pause/reconnect test.


Limit: The cache helper is a standalone executable example, not a cache transparently applied to every product route. Redis HA and old-backup counter rollback are not validated.

## Chapter 8 — Publish after a short durable claim


### Concept
The outbox closes the business-transaction publication gap by storing an event with the order. It does not make PostgreSQL and Kafka one transaction. A worker can publish successfully and crash before recording success, so duplicate delivery is an expected recovery outcome. The companion chooses a short durable lease rather than holding database locks while awaiting Kafka.


### Real implementation
[application/src/main/java/book/companion/outbox/OutboxRelay.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/outbox/OutboxRelay.java), [application/src/main/java/book/companion/outbox/OutboxStore.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/outbox/OutboxStore.java), [application/src/main/java/book/companion/messaging/AnalyticsConsumer.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/messaging/AnalyticsConsumer.java), [application/src/main/java/book/companion/messaging/KafkaConfig.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/messaging/KafkaConfig.java)


Excerpt from [application/src/main/java/book/companion/outbox/OutboxRelay.java, lines 31–40](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/outbox/OutboxRelay.java#L31-L40):

```
      try {
        kafka
            .send("orders.v1", claim.aggregate().toString(), claim.payload())
            .get(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        store.failed(claim);
        throw new IllegalStateException("Relay interrupted");
      } catch (Exception e) {
        store.failed(claim);
```


### Code walkthrough
OutboxStore claims one eligible row in REQUIRES_NEW, increments attempts and records a random ownership token with a database-clock expiry. OutboxRelay sends outside a transaction and waits at most ten seconds for acknowledgement. Completion is another conditional transaction requiring the same live lease token; a stale worker cannot finish a newer worker's claim. Expired claims can be recovered. Failed sends receive bounded retry delay, and exhausted claims enter FAILED for deliberate operator review. The consumer commits its deduplication marker and analytics effect together. Producer retry quarantine and consumer DLT are different mechanisms and need different runbooks.


### Run the test

[application/src/test/java/book/companion/KafkaOutboxIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/KafkaOutboxIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
DatabaseIT verifies competing claims, expired recovery, stale-token rejection, final-attempt quarantine and absence of a transaction during send. KafkaOutboxIT uses a real broker to check duplicate delivery, a publish-before-completion crash window and poison-message DLT. One business effect is expected even when a message is received again.


### Reader experiment
Use a disposable branch to remove ownership-token equality from completion SQL. The stale-worker test must fail; restore the condition before continuing. Next pause the broker with the runtime harness and observe that accepting a durable order does not require immediate publication. Explain why retrying a quarantined event requires understanding whether a consumer effect already happened.


### Architectural connection
Chapter 4 provides the atomic order/outbox write, Chapter 9 provides restart behavior and Chapter 10 exposes backlog age. The measured reference workload originally accumulated backlog with a one-second relay delay; a 250 ms delay was validated with a sampled age budget. That measurement improves this configuration without promising universal throughput or event ordering.


Limit: Delivery is at least once. No distributed exactly-once, per-aggregate publication ordering, multi-broker HA or infinite automatic retry is promised.

## Chapter 9 — Validate the running container rather than the image alone


### Concept
An image that builds can still fail to start, connect, become ready or shut down safely. The companion separates a source build from a target that packages an already-tested JAR. Both use a non-root Java 21 runtime; validation adds a read-only root, bounded memory/CPU/PIDs, dropped capabilities and temporary writable storage.


### Real implementation
[Dockerfile](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/Dockerfile), [compose.yml](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/compose.yml), [ops/HealthProbe.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/ops/HealthProbe.java), [scripts/runtime_validation.py](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/scripts/runtime_validation.py)


Excerpt from [Dockerfile, lines 6–10](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/Dockerfile#L6-L10):

```
WORKDIR /app
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=3 CMD ["java","-cp","/app/health","HealthProbe"]
ENTRYPOINT ["java","-jar","/app/application.jar"]
```


### Code walkthrough
The Dockerfile pins base images and installs an explicit OS security patch. Its Java readiness probe avoids adding a shell HTTP client solely for health checks. Compose waits for infrastructure health, but application readiness and security still require a reachable configured issuer. The source builder installs unzip so checksum-pinned Maven ZIP extraction works on a clean image. The runtime harness starts isolated loopback fixtures, checks actual HTTP and integrations, injects outages, restores a logical backup into another database and sends SIGTERM. Liveness remains independent of downstream failure; readiness reflects PostgreSQL availability.


### Run the test

[application/src/test/java/book/companion/HttpApiIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/HttpApiIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
The full Maven suite covers application behavior; the separate runtime command in README executes container-level assertions. The earlier engineering acceptance had 43 successful runtime assertions, including restart persistence and graceful shutdown. A reader must run that harness to establish the same result on their own host. Docker build uses skipped tests only because testing is a separately required release stage.


### Reader experiment
Run the container harness on a development Linux machine. Compare readiness and liveness during PostgreSQL outage, then inspect the shutdown result. Attempt a harmless write outside /tmp and explain why it should fail. Before proposing rollback, inspect V2 owner migration and state whether a pre-V2 binary can safely interpret those rows.


### Architectural connection
The reproducible artifact from Chapter 1 becomes an operational process here. The order/outbox invariant from Chapters 4 and 8 must survive process restarts. Chapter 10 turns health and metrics into diagnostics, while production TLS, secret delivery, HA and recovery objectives remain deployment responsibilities.


Limit: The runtime harness uses Linux host networking. No cloud deployment, production TLS policy, HA failover or pre-V2 binary downgrade is established.

## Chapter 10 — Use different evidence for different promises


### Concept
A unit test, integration test, runtime probe and production alert answer different questions. The companion intentionally retains each layer. Unit tests cover local invariants and adapter contracts; Testcontainers supplies real PostgreSQL, Redis and Kafka; the runtime harness validates the packaged process. JaCoCo gates are useful execution evidence, not a proof that all security or transaction paths are correct.


### Real implementation
[application/src/main/java/book/companion/web/RequestCorrelation.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/RequestCorrelation.java), [application/src/main/java/book/companion/outbox/OutboxMetrics.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/outbox/OutboxMetrics.java), [ops/alerts.yml](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/ops/alerts.yml), [ops/grafana-dashboard.json](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/ops/grafana-dashboard.json), [docs/RUNBOOKS.md](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/docs/RUNBOOKS.md)


Excerpt from [application/src/main/java/book/companion/web/RequestCorrelation.java, lines 21–28](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/RequestCorrelation.java#L21-L28):

```
    String supplied = request.getHeader("X-Request-ID");
    String id =
        supplied != null && supplied.matches("[A-Za-z0-9_-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", id);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
      chain.doFilter(request, response);
```


### Code walkthrough
RequestCorrelation bounds a supplied request id or replaces it with a UUID, writes it to MDC and returns it in the response. Structured logs retain request and trace context without logging credentials or bodies. Micrometer exposes HTTP, pool, Redis, Kafka and outbox observations through protected Prometheus scraping. The outbox metrics distinguish pending age, active leases and failed rows. Grafana definitions and alert rules are supplied; the local harness tested scrape and dashboard import/readback, and promtool tested a quarantine alert. These facts do not prove that a production alert reached an on-call engineer or that an external trace collector received a complete distributed trace.


### Run the test

[application/src/test/java/book/companion/HttpApiIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/HttpApiIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
The complete suite executes 106 tests with zero failures/errors/skips in the recorded publication run. Whole-module line/branch coverage exceeded 85%/75% without exclusions. OTel HTTP trace correlation was observed in local logs; hosted workflow and production telemetry results must be read separately. A controlled stale-owner fault demonstrated one regression detector, not a broad mutation score.


### Reader experiment
Choose one promise, such as no duplicate analytics effect, and identify the smallest test that could disprove it. Then identify what that test cannot observe. For operations, deliberately cause a quarantined outbox row in a disposable database and predict the metric and alert before checking them. Record absent metrics distinctly from a measured zero.


### Architectural connection
The runbooks connect symptoms to the boundaries described throughout the book: an increasing backlog differs from an unavailable database, and a 401 differs from a Redis 503. Chapter 11 combines these signals into one order timeline. The repository's evidence should teach how to investigate rather than encourage copying a test count into a production-readiness claim.


Limit: Grafana visual appearance, real on-call routing, long-term telemetry retention and exported end-to-end traces are not established by local API checks.

## Chapter 11 — Trace one order from request to analytics


### Concept
The companion's MicroMart case study is an inventory and order workflow inside one modular application. Start with an authenticated tenant-scoped principal and a product with known stock and price. The server, not the request, computes the total. One order transaction reserves stock and writes both order and event intent. Publication and analytics happen later across a separate failure boundary.


### Real implementation
[application/src/main/java/book/companion/orders/OrderService.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/orders/OrderService.java), [application/src/main/java/book/companion/web/OrderController.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/web/OrderController.java), [application/src/main/java/book/companion/outbox/OutboxStore.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/outbox/OutboxStore.java), [application/src/main/java/book/companion/messaging/AnalyticsConsumer.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/messaging/AnalyticsConsumer.java)


Excerpt from [application/src/main/java/book/companion/orders/OrderService.java, lines 60–65](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/main/java/book/companion/orders/OrderService.java#L60-L65):

```
    BigDecimal total = new Money(p.price()).times(quantity).amount();
    if (total.precision() > 19)
      throw new ProductService.Conflict("Order total exceeds supported amount");
    if (products.reserveStock(productId, owner, quantity) != 1)
      throw new ProductService.Conflict("Insufficient stock");
    var order = new OrderView(UUID.randomUUID(), productId, quantity, total);
```


### Code walkthrough
Follow OrderController to OrderService.place. A scoped request key makes a replay return the existing compatible order; reuse with a different payload is a conflict. The selected excerpt shows the business decision immediately before the order write. Continue to the outbox insert, then OutboxStore.claim and OutboxRelay.publishBatch. The Kafka consumer records event identity and its analytics effect in one database transaction. A successful HTTP response proves the durable order was accepted, not that analytics is already visible. A complete walkthrough therefore checks stock, order identity, outbox state and consumer effect separately.


### Run the test

[application/src/test/java/book/companion/DatabaseIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/DatabaseIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
HttpApiIT verifies a signed HTTP order round trip and ownership. DatabaseIT validates price calculation, idempotency and rollback. KafkaOutboxIT verifies publication and exactly one business effect under duplicate delivery. These are the new companion's tests. Historical MicroMart Lab07's 13-test and later 14-test reports remain historical datasets and are never added to 106.


### Reader experiment
Write a timeline with four crash points: before order commit, after order commit before claim, after Kafka acknowledgement before completion, and during consumer effect. For each, predict which rows survive and what the next retry does. Reproduce the publish-before-completion scenario using the existing test rather than inferring it from happy-path logs. Add an assertion before changing implementation.


### Architectural connection
This timeline joins the book's central decisions: Chapter 2's values, Chapter 3's contract, Chapter 4's atomic invariant, Chapter 5's ownership and Chapter 8's delivery model. Linker and CopyTrader material retained in this chapter provides comparison and design exercises; it does not turn those products into implemented services of this repository.


Limit: The case study does not include payments, shipping, distributed inventory, Linker or CopyTrader services.

## Chapter 12 — Extend the system with a falsifiable acceptance plan


### Concept
A capstone is an opportunity to demonstrate a new guarantee, not to rename existing scaffolding as a finished product. Start from the verified companion tag and keep the baseline tests passing. Choose one extension whose scope can be expressed as an invariant, an observable result and a failure scenario. The Link Shortener capstone retained in this chapter is proposed work; it does not already exist in the companion.


### Real implementation
[application/src/test/java/book/companion/DatabaseIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/DatabaseIT.java), [docs/EXERCISES.md](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/docs/EXERCISES.md), [PRODUCTION_READINESS_CHECKLIST.md](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/PRODUCTION_READINESS_CHECKLIST.md)


Excerpt from [application/src/test/java/book/companion/DatabaseIT.java, lines 341–352](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/DatabaseIT.java#L341-L352):

```
  void abandonedLeaseIsRecoveredAndStaleOwnerCannotFinish() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    var first = store.claim().orElseThrow();
    assertThat(store.claim()).isEmpty();
    db.update("update outbox set lease_until=clock_timestamp()-interval '1 second'");
    var second = store.claim().orElseThrow();
    assertThat(second.token()).isNotEqualTo(first.token());
    assertThat(store.delivered(first)).isFalse();
    assertThat(store.failed(first)).isFalse();
    assertThat(store.delivered(second)).isTrue();
  }
```


### Code walkthrough
The stale-owner regression test offers a model for an engineering exercise. It captures an old claim, expires it, obtains a replacement and attempts completion with both tokens. The important assertion is that the stale owner cannot finalize the row. Similar exercises can target cross-tenant reads, overselling, stale cache repopulation or duplicate effects. Keep deliberate faults on a disposable branch and label every modified example as an exercise, never as the tagged release. A useful review explains both the test's power and its blind spots.


### Run the test

[application/src/test/java/book/companion/DatabaseIT.java](https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/application/src/test/java/book/companion/DatabaseIT.java)

```sh
./mvnw -B -ntp clean verify -Pintegration,sbom
```


### Expected result
The unchanged full suite must pass. After an intentional fault, the relevant assertion must fail for the predicted reason; restoring the invariant returns the suite to green. A new capstone feature is complete only after its own tests, documentation and operational limits are added. Existing coverage percentages do not automatically describe new code.


### Reader experiment
Choose one assignment: add a currency-aware money operation; add a tenant-safe order-list endpoint with deterministic paging; or design an outbox retention policy with a dry-run report. Specify authorization, data retention, concurrency and rollback expectations first. For a Link Shortener, separately design redirect semantics, uniqueness, abuse policy, expiry and analytics delivery. Do not claim a full Saga until durable orchestration, compensations, recovery and failure tests exist.


### Architectural connection
The final architectural connection is evidence discipline. Concepts become executable claims, tests challenge those claims, operations reveal environmental failures, and documentation explains the boundary. Preserve that chain when publishing your own versioned tag. An immutable release is a stable learning reference, not a substitute for future security maintenance.


Limit: The Link Shortener, full Saga orchestration and other suggested services are unimplemented capstones. One fault injection is not comprehensive mutation testing.
