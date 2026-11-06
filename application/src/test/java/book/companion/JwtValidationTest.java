package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.security.SecurityConfig;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class JwtValidationTest {
  static KeyPair keys;
  NimbusJwtDecoder decoder;

  @BeforeAll
  static void keys() throws Exception {
    var g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    keys = g.generateKeyPair();
  }

  @BeforeEach
  void setup() {
    decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    decoder.setJwtValidator(SecurityConfig.validators("https://issuer.example", "orders-api"));
  }

  String token(
      String issuer,
      String audience,
      Instant expiry,
      KeyPair pair,
      String subject,
      Instant notBefore)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(Instant.now()))
            .notBeforeTime(Date.from(notBefore));
    if (expiry != null) claims.expirationTime(Date.from(expiry));
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
    jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
    return jwt.serialize();
  }

  String token(String issuer, String aud, Instant expiry) throws Exception {
    return token(issuer, aud, expiry, keys, "alice", Instant.now().minusSeconds(5));
  }

  @Test
  void acceptsSignedContract() throws Exception {
    assertThat(
            decoder
                .decode(
                    token("https://issuer.example", "orders-api", Instant.now().plusSeconds(300)))
                .getSubject())
        .isEqualTo("alice");
  }

  @Test
  void rejectsWrongAudience() throws Exception {
    String t = token("https://issuer.example", "other", Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsWrongIssuer() throws Exception {
    String t = token("https://wrong.example", "orders-api", Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsExpired() throws Exception {
    String t = token("https://issuer.example", "orders-api", Instant.now().minusSeconds(120));
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsMissingExpiry() throws Exception {
    String t = token("https://issuer.example", "orders-api", null);
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsFutureNotBefore() throws Exception {
    String t =
        token(
            "https://issuer.example",
            "orders-api",
            Instant.now().plusSeconds(600),
            keys,
            "alice",
            Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsMissingSubject() throws Exception {
    String t =
        token(
            "https://issuer.example",
            "orders-api",
            Instant.now().plusSeconds(300),
            keys,
            null,
            Instant.now());
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsWrongSignature() throws Exception {
    var g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    String t =
        token(
            "https://issuer.example",
            "orders-api",
            Instant.now().plusSeconds(300),
            g.generateKeyPair(),
            "alice",
            Instant.now());
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsMissingAudience() throws Exception {
    String t = token("https://issuer.example", null, Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
  }
}
