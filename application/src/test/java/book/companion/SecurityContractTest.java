package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityContractTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "blank-sub",
        "long-sub",
        "blank-tenant",
        "long-tenant",
        "numeric-tenant",
        "no-audience",
        "missing-audience"
      })
  void rejectsInvalidIdentityContract(String input) {
    var b =
        Jwt.withTokenValue("fixture")
            .header("alg", "RS256")
            .issuer("https://issuer.example")
            .subject("alice")
            .audience(List.of("orders-api"))
            .expiresAt(Instant.now().plusSeconds(300));
    switch (input) {
      case "blank-sub" -> b.subject(" ");
      case "long-sub" -> b.subject("x".repeat(129));
      case "blank-tenant" -> b.claim("tenant", " ");
      case "long-tenant" -> b.claim("tenant", "x".repeat(65));
      case "numeric-tenant" -> b.claim("tenant", 42);
      case "no-audience" -> b.audience(List.of());
      case "missing-audience" -> b.claims(c -> c.remove("aud"));
    }
    assertThat(
            SecurityConfig.validators("https://issuer.example", "orders-api")
                .validate(b.build())
                .hasErrors())
        .isTrue();
  }
}
