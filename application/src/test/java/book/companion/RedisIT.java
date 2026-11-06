package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.cache.RedisExamples;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class RedisIT {
  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(
              "redis@sha256:6ba19c50b56777f5c11c58eff8ddb69d0d5f27ba131cffb63f180ccfd43c66af")
          .withExposedPorts(6379);

  LettuceConnectionFactory connection;
  StringRedisTemplate template;
  RedisExamples example;

  @BeforeEach
  void init() {
    connection =
        new LettuceConnectionFactory(
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379)),
            org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .build());
    connection.afterPropertiesSet();
    connection.start();
    template = new StringRedisTemplate(connection);
    example = new RedisExamples(template);
    try (var c = connection.getConnection()) {
      c.serverCommands().flushDb();
    }
  }

  @AfterEach
  void close() {
    connection.destroy();
  }

  @Test
  void tenantIsolationAndCacheHit() {
    var calls = new AtomicInteger();
    assertThat(
            example.cached("a", "sku", Duration.ofSeconds(2), () -> "a" + calls.incrementAndGet()))
        .isEqualTo("a1");
    assertThat(example.cached("a", "sku", Duration.ofSeconds(2), () -> "wrong")).isEqualTo("a1");
    assertThat(example.cached("b", "sku", Duration.ofSeconds(2), () -> "b")).isEqualTo("b");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void ttlAndEvictionReload() {
    example.cached("a", "sku", Duration.ofMillis(150), () -> "old");
    Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .until(() -> !Boolean.TRUE.equals(template.hasKey(RedisExamples.key("a", "sku"))));
    assertThat(example.cached("a", "sku", Duration.ofSeconds(2), () -> "new")).isEqualTo("new");
    example.evict("a", "sku");
    assertThat(example.cached("a", "sku", Duration.ofSeconds(2), () -> "latest"))
        .isEqualTo("latest");
  }

  @Test
  void atomicLimiterUnderConcurrency() throws Exception {
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < 40; i++)
        futures.add(pool.submit(() -> example.allow("alice", "window", 7, Duration.ofSeconds(30))));
      int allowed = 0;
      for (var f : futures) if (f.get(10, TimeUnit.SECONDS)) allowed++;
      assertThat(allowed).isEqualTo(7);
    }
  }

  @Test
  void limiterExpires() {
    assertThat(example.allow("alice", "w", 1, Duration.ofMillis(150))).isTrue();
    assertThat(example.allow("alice", "w", 1, Duration.ofMillis(150))).isFalse();
    Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .until(() -> example.allow("alice", "w", 1, Duration.ofMillis(150)));
  }

  @Test
  void invalidTtlRejectedBeforeWrite() {
    assertThatThrownBy(() -> example.allow("alice", "w", 1, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(template.keys("book:*")).isEmpty();
  }

  @Test
  void delimitersCannotCollide() {
    assertThat(RedisExamples.key("a:b", "c")).isNotEqualTo(RedisExamples.key("a", "b:c"));
  }

  @Test
  void invalidationFencesAnOldLoader() throws Exception {
    var loaded = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var old =
          pool.submit(
              () ->
                  example.cached(
                      "tenant",
                      "sku",
                      Duration.ofSeconds(5),
                      () -> {
                        loaded.countDown();
                        try {
                          if (!finish.await(5, TimeUnit.SECONDS))
                            throw new AssertionError("loader timeout");
                        } catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return "old";
                      }));
      assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
      example.evict("tenant", "sku");
      finish.countDown();
      assertThat(old.get(5, TimeUnit.SECONDS)).isEqualTo("old");
      assertThat(example.cached("tenant", "sku", Duration.ofSeconds(5), () -> "new"))
          .isEqualTo("new");
    }
  }

  @Test
  void outageFailsClosedAndConnectionRecovers() throws Exception {
    var client = redis.getDockerClient();
    String id = redis.getContainerId();
    client.pauseContainerCmd(id).exec();
    try {
      assertThatThrownBy(() -> example.allow("a", "w", 1, Duration.ofSeconds(1)))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      client.unpauseContainerCmd(id).exec();
    }
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .until(() -> example.allow("recovered", "w", 10, Duration.ofSeconds(10)));
  }
}
