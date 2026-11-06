package book.companion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import book.companion.inventory.Product;
import book.companion.inventory.ProductRepository;
import book.companion.inventory.ProductService;
import book.companion.messaging.AnalyticsConsumer;
import book.companion.orders.OrderService;
import book.companion.outbox.OutboxRelay;
import book.companion.security.RefreshTokens;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@WithMockUser(authorities = "SCOPE_products:write")
class DatabaseIT {
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          "postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.kafka.admin.auto-create", () -> false);
  }

  @Autowired ProductService products;
  @Autowired ProductRepository repository;
  @Autowired OrderService orders;
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager manager;
  @Autowired AnalyticsConsumer consumer;
  @Autowired OutboxRelay relay;
  @Autowired book.companion.outbox.OutboxStore store;
  @Autowired RefreshTokens refresh;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean KafkaTemplate<String, String> kafka;

  @BeforeEach
  void clean() {
    db.execute(
        "truncate analytics,processed_event,outbox,orders,products,refresh_token,refresh_session"
            + " cascade");
  }

  ProductService.View product(int stock) {
    return products.create("alice", "Book", new BigDecimal("12.50"), stock);
  }

  int count(String table) {
    return db.queryForObject("select count(*) from " + table, Integer.class);
  }

  <T> List<T> race(int threads, Callable<T> action) throws Exception {
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var gate = new CountDownLatch(1);
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(
                              "alice",
                              "",
                              List.of(new SimpleGrantedAuthority("SCOPE_products:write"))));
                  try {
                    return action.call();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      gate.countDown();
      List<T> result = new ArrayList<>();
      for (var f : futures) result.add(f.get(30, TimeUnit.SECONDS));
      return result;
    }
  }

  @Test
  void migrationsAndJpaMappingWork() {
    var p = product(3);
    assertThat(products.get("alice", p.id()).price()).isEqualByComparingTo("12.50");
  }

  @Test
  void ownerIsolation() {
    var p = product(3);
    assertThatThrownBy(() -> products.get("bob", p.id()))
        .isInstanceOf(ProductService.NotFound.class);
    assertThat(products.list("bob", 0, 20)).isEmpty();
    assertThatThrownBy(() -> orders.place("bob", "key", p.id(), 1))
        .isInstanceOf(ProductService.NotFound.class);
  }

  @Test
  void orderUsesServerPriceAndOneTransaction() {
    var p = product(3);
    var order = orders.place("alice", "key", p.id(), 2);
    assertThat(order.total()).isEqualByComparingTo("25.00");
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(1);
    assertThat(count("orders")).isEqualTo(1);
    assertThat(count("outbox")).isEqualTo(1);
    assertThatThrownBy(() -> orders.get("bob", order.id()))
        .isInstanceOf(ProductService.NotFound.class);
  }

  @Test
  void insufficientStockRollsBack() {
    var p = product(1);
    assertThatThrownBy(() -> orders.place("alice", "key", p.id(), 2))
        .isInstanceOf(ProductService.Conflict.class);
    assertThat(count("orders")).isZero();
    assertThat(count("outbox")).isZero();
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(1);
  }

  @Test
  void outerFailureRollsBackAllWrites() {
    var p = product(3);
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .execute(
                        s -> {
                          orders.place("alice", "key", p.id(), 2);
                          throw new IllegalStateException("injected after writes");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("orders")).isZero();
    assertThat(count("outbox")).isZero();
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(3);
  }

  @Test
  void competingReservationsNeverOversell() throws Exception {
    var p = product(5);
    var successes =
        race(
            16,
            () -> {
              try {
                orders.place("alice", UUID.randomUUID().toString(), p.id(), 1);
                return true;
              } catch (ProductService.Conflict e) {
                return false;
              }
            });
    assertThat(successes.stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
    assertThat(products.get("alice", p.id()).stock()).isZero();
    assertThat(count("orders")).isEqualTo(5);
  }

  @Test
  void simultaneousSameRequestCreatesOneOrder() throws Exception {
    var p = product(9);
    var results = race(8, () -> orders.place("alice", "same", p.id(), 2).id());
    assertThat(new HashSet<>(results)).hasSize(1);
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(7);
    assertThat(count("outbox")).isEqualTo(1);
  }

  @Test
  void conflictingIdempotencyPayloadRejected() {
    var p = product(9);
    orders.place("alice", "same", p.id(), 1);
    assertThatThrownBy(() -> orders.place("alice", "same", p.id(), 2))
        .isInstanceOf(ProductService.Conflict.class);
  }

  @Test
  void bulkUpdateInvalidatesStaleVersion() {
    var p = product(3);
    Product stale = repository.findById(p.id()).orElseThrow();
    orders.place("alice", "key", p.id(), 1);
    assertThat(products.get("alice", p.id()).version()).isEqualTo(1);
    stale.rename("stale overwrite");
    assertThatThrownBy(() -> repository.saveAndFlush(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void duplicateEventHasOneEffect() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    String payload = db.queryForObject("select payload from outbox", String.class);
    consumer.receive(payload);
    consumer.receive(payload);
    assertThat(count("analytics")).isEqualTo(1);
    assertThat(count("processed_event")).isEqualTo(1);
  }

  @Test
  void failedEffectRollsBackDedupMarker() {
    UUID order = UUID.randomUUID(), event = UUID.randomUUID();
    db.update("insert into analytics values(?,?)", order, BigDecimal.ONE);
    String payload = "{\"eventId\":\"" + event + "\",\"orderId\":\"" + order + "\",\"total\":1}";
    assertThatThrownBy(() -> consumer.receive(payload))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("processed_event")).isZero();
    db.update("delete from analytics");
    consumer.receive(payload);
    assertThat(count("processed_event")).isEqualTo(1);
  }

  @Test
  void unacknowledgedPublicationStaysPending() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("injected send failure")));
    assertThat(relay.publishBatch()).isZero();
    assertThat(
            db.queryForObject(
                "select count(*) from outbox where published_at is null", Integer.class))
        .isEqualTo(1);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
    db.update("update outbox set next_attempt_at=clock_timestamp()-interval '1 second'");
    assertThat(relay.publishBatch()).isEqualTo(1);
    assertThat(relay.publishBatch()).isZero();
  }

  @Test
  void negativeQuantityCannotIncreaseStock() {
    var p = product(2);
    assertThatThrownBy(() -> orders.place("alice", "key", p.id(), -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(2);
  }

  @Test
  void refreshRotationAndReplayRevokeFamily() {
    String first = refresh.issue();
    String next = refresh.rotate(first).orElseThrow();
    assertThat(refresh.rotate(first)).isEmpty();
    assertThat(refresh.rotate(next)).isEmpty();
    assertThat(
            db.queryForObject(
                "select token_hash from refresh_token where token_hash=?",
                String.class,
                RefreshTokens.hash(first)))
        .isNotEqualTo(first);
  }

  @Test
  void concurrentRefreshHasSingleWinnerThenRevokesFamily() throws Exception {
    String first = refresh.issue();
    var results = race(2, () -> refresh.rotate(first));
    var winners = results.stream().flatMap(Optional::stream).toList();
    assertThat(winners).hasSize(1);
    assertThat(refresh.rotate(winners.getFirst())).isEmpty();
  }

  @Test
  void expiredRefreshRejected() {
    String token = refresh.issue();
    db.update("update refresh_token set expires_at=CURRENT_TIMESTAMP - interval '1 hour'");
    assertThat(refresh.rotate(token)).isEmpty();
  }

  @Test
  void unknownRefreshRejected() {
    assertThat(refresh.rotate("unknown")).isEmpty();
  }

  @Test
  void concurrentDuplicateConsumptionCommitsOneEffect() throws Exception {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    String payload = db.queryForObject("select payload from outbox", String.class);
    race(
        8,
        () -> {
          consumer.receive(payload);
          return true;
        });
    assertThat(count("analytics")).isEqualTo(1);
    assertThat(count("processed_event")).isEqualTo(1);
  }

  @Test
  void relaySkipsRowsLockedByAnotherTransaction() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              db.queryForList("select id from outbox for update");
              try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(pool.submit(() -> relay.publishBatch()).get(10, TimeUnit.SECONDS))
                    .isZero();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
              verifyNoInteractions(kafka);
            });
    assertThat(relay.publishBatch()).isEqualTo(1);
  }

  @Test
  void twoWorkersCannotOwnSameLease() throws Exception {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    var claims = race(2, () -> store.claim());
    assertThat(claims.stream().filter(Optional::isPresent).count()).isEqualTo(1);
  }

  @Test
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

  @Test
  void expiredFinalAttemptIsQuarantined() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    store.claim();
    db.update("update outbox set attempts=8,lease_until=clock_timestamp()-interval '1 second'");
    assertThat(store.claim()).isEmpty();
    assertThat(db.queryForObject("select status from outbox", String.class)).isEqualTo("FAILED");
  }

  @Test
  void finalFailedSendIsQuarantined() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    db.update("update outbox set attempts=7");
    var c = store.claim().orElseThrow();
    assertThat(store.failed(c)).isTrue();
    assertThat(store.claim()).isEmpty();
    assertThat(db.queryForObject("select status from outbox", String.class)).isEqualTo("FAILED");
  }

  @Test
  void kafkaSendRunsWithoutDatabaseTransaction() {
    var p = product(2);
    orders.place("alice", "key", p.id(), 1);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenAnswer(
            a -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return CompletableFuture.<SendResult<String, String>>completedFuture(null);
            });
    assertThat(relay.publishBatch()).isEqualTo(1);
  }

  @Test
  void relayRejectsAccidentalAmbientTransaction() {
    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(s -> relay.publishBatch()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refreshRotationRollbackDoesNotConsumeToken() {
    String first = refresh.issue();
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .execute(
                        s -> {
                          refresh.rotate(first).orElseThrow();
                          throw new IllegalStateException("fault");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(refresh.rotate(first)).isPresent();
    assertThat(refresh.rotate(null)).isEmpty();
    assertThat(refresh.rotate("x".repeat(257))).isEmpty();
  }

  @Test
  void databaseConstraintRejectsNegativeStock() {
    var p = product(2);
    assertThatThrownBy(() -> db.update("update products set stock=-1 where id=?", p.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(2);
  }

  @Test
  void paginationHasStableTieBreaker() {
    for (int i = 0; i < 8; i++) product(1);
    var first = products.list("alice", 0, 4);
    var second = products.list("alice", 1, 4);
    assertThat(first).hasSize(4);
    assertThat(second).hasSize(4);
    assertThat(first).doesNotContainAnyElementsOf(second);
    assertThat(products.list("alice", 0, 4)).isEqualTo(first);
  }

  @Test
  void oversizedOrderTotalIsAConflictAndDoesNotReserve() {
    var p = products.create("alice", "expensive", new BigDecimal("99999999999999999.99"), 2);
    assertThatThrownBy(() -> orders.place("alice", "k", p.id(), 2))
        .isInstanceOf(ProductService.Conflict.class);
    assertThat(products.get("alice", p.id()).stock()).isEqualTo(2);
  }
}
