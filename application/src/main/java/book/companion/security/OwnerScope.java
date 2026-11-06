package book.companion.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.security.oauth2.jwt.Jwt;

/** A trusted issuer asserts tenant; clients cannot choose identity in request bodies. */
public final class OwnerScope {
  private OwnerScope() {}

  public static String from(Jwt jwt) {
    String tenant = jwt.getClaimAsString("tenant");
    return tenant == null
        ? "legacy#" + jwt.getSubject()
        : "tenant#" + encode(tenant) + ":" + encode(jwt.getSubject());
  }

  private static String encode(String s) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }
}
