package book.companion;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import book.companion.inventory.ProductService;
import book.companion.orders.OrderService;
import book.companion.outbox.OutboxRelay;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(properties = {"app.consumer.enabled=true", "spring.kafka.admin.auto-create=true"})
@ActiveProfiles("test")
@Testcontainers
@WithMockUser(authorities = "SCOPE_products:write")
class KafkaOutboxIT {
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          "postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(
          "apache/kafka@sha256:4ceccc577f03f51f6af8dbfda55194d0d892f4fa7913ffbded567ce3895622ed");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired ProductService products;
  @Autowired OrderService orders;
  @Autowired JdbcTemplate db;
  @Autowired OutboxRelay relay;
  @Autowired book.companion.outbox.OutboxStore store;
  @Autowired KafkaTemplate<String, String> producer;
  @Autowired MeterRegistry metrics;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean JwtDecoder decoder;

  OrderService.OrderView place() {
    var p = products.create("alice", "Book", new BigDecimal("5.25"), 2);
    return orders.place("alice", UUID.randomUUID().toString(), p.id(), 1);
  }

  int effects(UUID id) {
    return db.queryForObject("select count(*) from analytics where order_id=?", Integer.class, id);
  }

  double duplicates() {
    var c = metrics.find("book.consumer.duplicates").counter();
    return c == null ? 0 : c.count();
  }

  @Test
  void realRelayAndConsumedDuplicateHaveOneEffect() throws Exception {
    var order = place();
    relay.publishBatch();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(effects(order.id())).isEqualTo(1));
    String payload =
        db.queryForObject(
            "select payload from outbox where aggregate_id=?", String.class, order.id());
    double before = duplicates();
    producer.send("orders.v1", order.id().toString(), payload).get(10, TimeUnit.SECONDS);
    // Positive evidence that the duplicate actually reached application code, not a sleep-and-count
    // guess.
    await().atMost(Duration.ofSeconds(30)).until(() -> duplicates() > before);
    assertThat(effects(order.id())).isEqualTo(1);
  }

  @Test
  void crashWindowAfterKafkaAckBeforeDatabaseCommitIsRecoverable() throws Exception {
    var order = place();
    var claim = store.claim().orElseThrow();
    producer
        .send("orders.v1", claim.aggregate().toString(), claim.payload())
        .get(10, TimeUnit.SECONDS);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(effects(order.id())).isEqualTo(1));
    // Simulate process death after publication: no delivered() call; lease expires durably.
    db.update(
        "update outbox set lease_until=clock_timestamp()-interval '1 second' where id=?",
        claim.id());
    double before = duplicates();
    relay.publishBatch();
    await().atMost(Duration.ofSeconds(30)).until(() -> duplicates() > before);
    assertThat(effects(order.id())).isEqualTo(1);
    assertThat(store.delivered(claim)).isFalse();
  }

  @Test
  void poisonMessageReachesRealDeadLetterTopic() throws Exception {
    Properties config = new Properties();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    String poison = "invalid-json-" + UUID.randomUUID();
    try (var probe =
        new KafkaConsumer<String, String>(
            config, new StringDeserializer(), new StringDeserializer())) {
      probe.subscribe(List.of("orders.v1.DLT"));
      producer.send("orders.v1", "poison", poison).get(10, TimeUnit.SECONDS);
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                for (var record : probe.poll(Duration.ofMillis(200)))
                  if (record.value().equals(poison)) return true;
                return false;
              });
    }
  }
}
