package book.companion.web;

import book.companion.cache.RedisExamples;
import book.companion.security.OwnerScope;
import java.time.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class RateLimitController {
  private final RedisExamples redis;

  public RateLimitController(RedisExamples redis) {
    this.redis = redis;
  }

  @PostMapping("/api/v1/limits/check")
  public ResponseEntity<?> check(@AuthenticationPrincipal Jwt jwt) {
    boolean allowed =
        redis.allow(
            OwnerScope.from(jwt),
            Long.toString(Instant.now().getEpochSecond()),
            20,
            Duration.ofSeconds(2));
    return allowed
        ? ResponseEntity.ok(java.util.Map.of("allowed", true))
        : ResponseEntity.status(429)
            .header("Retry-After", "1")
            .body(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));
  }
}
