package book.companion.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisExamples {
  private final StringRedisTemplate redis;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  public RedisExamples(StringRedisTemplate redis) {
    this(redis, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  private final DefaultRedisScript<Long> script = new DefaultRedisScript<>();

  @org.springframework.beans.factory.annotation.Autowired
  public RedisExamples(
      StringRedisTemplate redis, io.micrometer.core.instrument.MeterRegistry metrics) {
    this.metrics = metrics;
    this.redis = redis;
    script.setLocation(new ClassPathResource("rate-limit.lua"));
    script.setResultType(Long.class);
  }

  private static String segment(String s) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException("Blank key segment");
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  public static String key(String tenant, String resource) {
    return "book:cache:v2:{" + segment(tenant) + ":" + segment(resource) + "}";
  }

  /** Ch 7.3: display data only; cache failures propagate (explicit fail-closed policy). */
  public String cached(String tenant, String resource, Duration ttl, Supplier<String> source) {
    if (ttl.toMillis() <= 0) throw new IllegalArgumentException("Positive TTL required");
    String key = key(tenant, resource);
    var snapshot = redis.opsForValue().multiGet(List.of(key, key + ":generation"));
    if (snapshot != null && snapshot.get(0) != null) return snapshot.get(0);
    String generation = snapshot == null || snapshot.get(1) == null ? "0" : snapshot.get(1);
    String value = Objects.requireNonNull(source.get());
    var cas =
        new DefaultRedisScript<Long>(
            "if (redis.call('GET',KEYS[2]) or '0') == ARGV[1] then redis.call('PSETEX',KEYS[1],ARGV[2],ARGV[3]); return 1 end return 0",
            Long.class);
    redis.execute(
        cas, List.of(key, key + ":generation"), generation, Long.toString(ttl.toMillis()), value);
    return value;
  }

  public void evict(String tenant, String resource) {
    String k = key(tenant, resource);
    redis.execute(
        new DefaultRedisScript<Long>(
            "redis.call('INCR',KEYS[2]); return redis.call('DEL',KEYS[1])", Long.class),
        List.of(k, k + ":generation"));
  }

  /** Ch 7.12: caller supplies a trusted policy-window identity, not an arbitrary client header. */
  public boolean allow(String subject, String window, int limit, Duration ttl) {
    var sample = io.micrometer.core.instrument.Timer.start(metrics);
    try {
      return doAllow(subject, window, limit, ttl);
    } catch (org.springframework.dao.DataAccessException e) {
      metrics.counter("book.redis.errors", "operation", "limit").increment();
      throw e;
    } finally {
      sample.stop(metrics.timer("book.redis.latency", "operation", "limit"));
    }
  }

  private boolean doAllow(String subject, String window, int limit, Duration ttl) {
    if (limit <= 0 || ttl.toMillis() <= 0)
      throw new IllegalArgumentException("Positive limit and TTL required");
    Long count =
        redis.execute(
            script,
            List.of("book:limit:" + segment(subject) + ":" + segment(window)),
            Long.toString(ttl.toMillis()));
    if (count == null) throw new IllegalStateException("No Redis script result");
    return count <= limit;
  }
}
