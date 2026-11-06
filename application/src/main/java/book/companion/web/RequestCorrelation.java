package book.companion.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class RequestCorrelation extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestCorrelation.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Request-ID");
    String id =
        supplied != null && supplied.matches("[A-Za-z0-9_-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", id);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
      chain.doFilter(request, response);
      log.info("http_completed method={} status={}", request.getMethod(), response.getStatus());
    }
  }
}
