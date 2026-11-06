package book.companion;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Starts the actual packaged JAR and exercises issuer discovery against a local fixture. */
@Testcontainers
class PackagedApplicationIT {
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          "postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777");

  @Test
  void packagedJarDiscoversKeysAndServesAuthenticatedRequest() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var issuer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String issuerUrl = "http://127.0.0.1:" + issuer.getAddress().getPort();
    String publicJwk =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .keyID("local-test")
            .build()
            .toJSONString();
    issuer.createContext(
        "/.well-known/openid-configuration",
        exchange -> {
          byte[] data =
              ("{\"issuer\":\""
                      + issuerUrl
                      + "\",\"jwks_uri\":\""
                      + issuerUrl
                      + "/jwks\",\"id_token_signing_alg_values_supported\":[\"RS256\"]}")
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, data.length);
          try (var body = exchange.getResponseBody()) {
            body.write(data);
          }
        });
    issuer.createContext(
        "/jwks",
        exchange -> {
          byte[] data =
              ("{\"keys\":[" + publicJwk + "]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, data.length);
          try (var body = exchange.getResponseBody()) {
            body.write(data);
          }
        });
    issuer.start();
    int port;
    try (var socket = new java.net.ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar",
            "target/application-1.0.0-SNAPSHOT.jar",
            "--server.address=127.0.0.1",
            "--server.port=" + port);
    // Restrict the child to fixture configuration. Do not inherit external service credentials.
    var env = builder.environment();
    for (String name : new ArrayList<>(env.keySet())) {
      if (name.startsWith("SPRING_")
          || name.startsWith("DB_")
          || name.startsWith("JWT_")
          || name.startsWith("KAFKA_")
          || name.startsWith("REDIS_")
          || name.equals("JAVA_TOOL_OPTIONS")
          || name.equals("JDK_JAVA_OPTIONS")) env.remove(name);
    }
    env.put("DB_URL", postgres.getJdbcUrl());
    env.put("DB_USER", postgres.getUsername());
    env.put("DB_PASSWORD", postgres.getPassword());
    env.put("JWT_ISSUER", issuerUrl);
    env.put("JWT_AUDIENCE", "orders-api");
    env.put("RELAY_ENABLED", "false");
    env.put("CONSUMER_ENABLED", "false");
    env.put("KAFKA_AUTO_CREATE", "false");
    builder.redirectErrorStream(true).redirectOutput(Path.of("target/packaged-smoke.log").toFile());
    Process process = null;
    try {
      process = builder.start();
      var child = process;
      var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      String base = "http://127.0.0.1:" + port;
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(300))
          .ignoreExceptions()
          .until(
              () -> {
                if (!child.isAlive())
                  throw new IllegalStateException("JAR exited: inspect target/packaged-smoke.log");
                return http.send(
                            HttpRequest.newBuilder(URI.create(base + "/actuator/health/liveness"))
                                .timeout(Duration.ofSeconds(2))
                                .GET()
                                .build(),
                            HttpResponse.BodyHandlers.ofString())
                        .statusCode()
                    == 200;
              });
      var claims =
          new JWTClaimsSet.Builder()
              .issuer(issuerUrl)
              .audience("orders-api")
              .subject("smoke-user")
              .expirationTime(Date.from(Instant.now().plusSeconds(300)))
              .claim("scope", "products:read")
              .build();
      var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("local-test").build(), claims);
      jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/api/products"))
                  .timeout(Duration.ofSeconds(5))
                  .header("Authorization", "Bearer " + jwt.serialize())
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("[]");
    } finally {
      if (process != null) {
        process.destroy();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        }
      }
      issuer.stop(0);
    }
  }
}
