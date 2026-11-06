package book.companion.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Ch 5.9: strict reuse revokes the family, including concurrent legitimate retries. */
@Service
public class RefreshTokens {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokens(
      JdbcTemplate db, org.springframework.transaction.PlatformTransactionManager manager) {
    this.db = db;
    this.tx = new TransactionTemplate(manager);
  }

  public String issue() {
    return tx.execute(
        s -> {
          UUID id = UUID.randomUUID();
          db.update("insert into refresh_session(id) values(?)", id);
          return insert(id);
        });
  }

  public Optional<String> rotate(String raw) {
    if (raw == null || raw.length() > 256) return Optional.empty();
    return tx.execute(
        s -> {
          var families =
              db.query(
                  "select session_id from refresh_token where token_hash=?",
                  (r, n) -> r.getObject(1, UUID.class),
                  hash(raw));
          if (families.isEmpty()) return Optional.empty();
          UUID family = families.getFirst();
          boolean revoked =
              Boolean.TRUE.equals(
                  db.queryForObject(
                      "select revoked from refresh_session where id=? for update",
                      Boolean.class,
                      family));
          if (revoked) return Optional.empty();
          int consumed =
              db.update(
                  "update refresh_token set used=true where token_hash=? and used=false and"
                      + " expires_at>CURRENT_TIMESTAMP",
                  hash(raw));
          if (consumed != 1) {
            db.update("update refresh_session set revoked=true where id=?", family);
            return Optional.empty();
          }
          return Optional.of(insert(family));
        });
  }

  private String insert(UUID family) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update(
        "insert into refresh_token(token_hash,session_id,expires_at) values(?,?,?)",
        hash(token),
        family,
        java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
    return token;
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
