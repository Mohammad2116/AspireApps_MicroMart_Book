package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.security.SecurityConfig;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class OidcRotationTest {
  HttpServer server;
  String issuer;
  KeyPair oldKey, newKey;
  AtomicReference<String> keys = new AtomicReference<>();
  JwtDecoder decoder;

  KeyPair generate() throws Exception {
    var g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  String jwk(KeyPair key, String id) {
    return new RSAKey.Builder((RSAPublicKey) key.getPublic()).keyID(id).build().toJSONString();
  }

  @BeforeEach
  void start() throws Exception {
    oldKey = generate();
    newKey = generate();
    keys.set("{\"keys\":[" + jwk(oldKey, "old") + "]}");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    issuer = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext(
        "/",
        e -> {
          String data =
              e.getRequestURI().getPath().equals("/keys")
                  ? keys.get()
                  : "{\"issuer\":\""
                      + issuer
                      + "\",\"jwks_uri\":\""
                      + issuer
                      + "/keys\",\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
          byte[] b = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          e.getResponseHeaders().set("Content-Type", "application/json");
          e.sendResponseHeaders(200, b.length);
          try (var out = e.getResponseBody()) {
            out.write(b);
          }
        });
    server.start();
    decoder = new SecurityConfig().decoder(issuer, "orders-api");
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  String token(KeyPair key, String id, JWSAlgorithm algorithm) throws Exception {
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(algorithm).keyID(id).build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience("orders-api")
                .subject("alice")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build());
    jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
    return jwt.serialize();
  }

  @Test
  void newKeyRefreshesJwksAndOldKeyStillWorksDuringOverlap() throws Exception {
    String old = token(oldKey, "old", JWSAlgorithm.RS256);
    assertThat(decoder.decode(old).getSubject()).isEqualTo("alice");
    keys.set("{\"keys\":[" + jwk(oldKey, "old") + "," + jwk(newKey, "new") + "]}");
    assertThat(decoder.decode(token(newKey, "new", JWSAlgorithm.RS256)).getSubject())
        .isEqualTo("alice");
    assertThat(decoder.decode(old).getSubject()).isEqualTo("alice");
  }

  @Test
  void unrelatedKeyRejected() throws Exception {
    String t = token(newKey, "unknown", JWSAlgorithm.RS256);
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void otherRsaAlgorithmRejected() throws Exception {
    String t = token(oldKey, "old", JWSAlgorithm.RS512);
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void malformedTokenRejected() {
    assertThatThrownBy(() -> decoder.decode("not.a.jwt")).isInstanceOf(JwtException.class);
  }
}
