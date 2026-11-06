package book.companion.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper json;

  public ProblemSecurityHandler(ObjectMapper json) {
    this.json = json;
  }

  public void commence(HttpServletRequest r, HttpServletResponse s, AuthenticationException e)
      throws IOException {
    s.setHeader("WWW-Authenticate", "Bearer");
    write(s, 401, "Authentication required");
  }

  public void handle(HttpServletRequest r, HttpServletResponse s, AccessDeniedException e)
      throws IOException {
    write(s, 403, "Access denied");
  }

  private void write(HttpServletResponse s, int status, String detail) throws IOException {
    s.setStatus(status);
    s.setContentType("application/problem+json");
    json.writeValue(
        s.getOutputStream(),
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatus.valueOf(status), detail));
  }
}
