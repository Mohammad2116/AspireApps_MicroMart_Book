package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.inventory.ProductService;
import book.companion.orders.OrderService;
import book.companion.security.SecurityConfig;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Real HTTP server + real signature validation + real PostgreSQL; no mock authentication. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import(HttpApiIT.Keys.class)
class HttpApiIT {
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          "postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
  }

  static KeyPair pair;

  static {
    try {
      var g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      pair = g.generateKeyPair();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @TestConfiguration
  static class Keys {
    @Bean
    JwtDecoder decoder() {
      var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
      decoder.setJwtValidator(SecurityConfig.validators("https://issuer.example", "orders-api"));
      return decoder;
    }
  }

  @Autowired TestRestTemplate http;

  HttpHeaders headers(String subject, String audience, String scope) throws Exception {
    return tenantHeaders(subject, audience, scope, null);
  }

  HttpHeaders tenantHeaders(String subject, String audience, String scope, String tenant)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("https://issuer.example")
            .audience(audience)
            .subject(subject)
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("scope", scope)
            .claim("tenant", tenant)
            .build();
    var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    token.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
    var h = new HttpHeaders();
    h.setBearerAuth(token.serialize());
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  @Test
  void signedHttpOrderRoundTripAndOwnership() throws Exception {
    var h = headers("alice", "orders-api", "products:read products:write");
    var created =
        http.exchange(
            "/api/products",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "HTTP book", "price", 10.50, "stock", 3), h),
            ProductService.View.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var product = created.getBody();
    assertThat(product).isNotNull();
    var stolen =
        http.exchange(
            created.getHeaders().getLocation(),
            HttpMethod.GET,
            new HttpEntity<>(headers("bob", "orders-api", "products:read")),
            String.class);
    assertThat(stolen.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    h.set("Idempotency-Key", UUID.randomUUID().toString());
    var body = Map.of("productId", product.id(), "quantity", 2);
    var placed =
        http.exchange(
            "/api/orders",
            HttpMethod.POST,
            new HttpEntity<>(body, h),
            OrderService.OrderView.class);
    assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(placed.getBody().total()).isEqualByComparingTo("21.00");
    var repeated =
        http.exchange(
            "/api/orders",
            HttpMethod.POST,
            new HttpEntity<>(body, h),
            OrderService.OrderView.class);
    assertThat(repeated.getBody().id()).isEqualTo(placed.getBody().id());
    var found =
        http.exchange(
            placed.getHeaders().getLocation(),
            HttpMethod.GET,
            new HttpEntity<>(h),
            OrderService.OrderView.class);
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void rejectsRealWrongAudienceAtFilter() throws Exception {
    var response =
        http.exchange(
            "/api/products",
            HttpMethod.GET,
            new HttpEntity<>(headers("alice", "wrong-api", "products:read")),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void authenticatedInsufficientScopeIs403() throws Exception {
    var response =
        http.exchange(
            "/api/products",
            HttpMethod.GET,
            new HttpEntity<>(headers("alice", "orders-api", "products:write")),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void actuatorDoesNotExposeEnvironment() {
    assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void livenessDoesNotRequireRedis() {
    assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void sameSubjectInAnotherTenantCannotReadOrOrder() throws Exception {
    var a =
        tenantHeaders("shared-subject", "orders-api", "products:read products:write", "tenant-a");
    var b =
        tenantHeaders("shared-subject", "orders-api", "products:read products:write", "tenant-b");
    var p =
        http.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "tenant item", "price", 2, "stock", 4), a),
                ProductService.View.class)
            .getBody();
    assertThat(p).isNotNull();
    assertThat(
            http.exchange(
                    "/api/v1/products/" + p.id(), HttpMethod.GET, new HttpEntity<>(b), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    b.set("Idempotency-Key", "same-key");
    assertThat(
            http.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("productId", p.id(), "quantity", 1), b),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var own =
        http.exchange(
            "/api/products/" + p.id(),
            HttpMethod.GET,
            new HttpEntity<>(a),
            ProductService.View.class);
    assertThat(own.getBody().stock()).isEqualTo(4);
  }

  @Test
  void securityErrorsAreProblemDetailsAndSafe() throws Exception {
    var h = new HttpHeaders();
    h.setBearerAuth("malformed-secret-sentinel");
    var response =
        http.exchange("/api/products", HttpMethod.GET, new HttpEntity<>(h), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).doesNotContain("sentinel");
  }

  @Test
  void requestIdIsBoundedAndTraceContextIsAccepted() throws Exception {
    var h = headers("alice", "orders-api", "products:read");
    h.set("X-Request-ID", "x".repeat(100));
    h.set("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    var result = http.exchange("/api/products", HttpMethod.GET, new HttpEntity<>(h), String.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getHeaders().getFirst("X-Request-ID")).hasSizeLessThanOrEqualTo(64);
  }
}
