package book.companion.outbox;

import io.micrometer.core.instrument.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
  public OutboxMetrics(JdbcTemplate db, MeterRegistry metrics) {
    Gauge.builder(
            "book.outbox.pending",
            db,
            d ->
                value(
                    () ->
                        d.queryForObject(
                            "select count(*) from outbox where status in ('PENDING','CLAIMED')",
                            Double.class)))
        .register(metrics);
    Gauge.builder(
            "book.outbox.failed",
            db,
            d ->
                value(
                    () ->
                        d.queryForObject(
                            "select count(*) from outbox where status='FAILED'", Double.class)))
        .register(metrics);
    Gauge.builder(
            "book.outbox.leases",
            db,
            d ->
                value(
                    () ->
                        d.queryForObject(
                            "select count(*) from outbox where status='CLAIMED'", Double.class)))
        .register(metrics);
    Gauge.builder(
            "book.outbox.oldest.seconds",
            db,
            d ->
                value(
                    () ->
                        d.queryForObject(
                            "select coalesce(extract(epoch from clock_timestamp()-min(created_at)),0) from outbox where status in ('PENDING','CLAIMED')",
                            Double.class)))
        .register(metrics);
  }

  private static double value(Supplier<Double> query) {
    try {
      Double n = query.get();
      return n == null ? Double.NaN : n;
    } catch (org.springframework.dao.DataAccessException e) {
      return Double.NaN;
    }
  }
}
