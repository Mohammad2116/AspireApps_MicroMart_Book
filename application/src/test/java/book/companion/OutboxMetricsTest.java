package book.companion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import book.companion.outbox.OutboxMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxMetricsTest {
  @Test
  void unavailableDatabaseIsNotReportedAsEmptyQueue() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Double.class)))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
    var registry = new SimpleMeterRegistry();
    new OutboxMetrics(db, registry);
    assertThat(registry.get("book.outbox.pending").gauge().value()).isNaN();
  }

  @Test
  void queueGaugesReflectDatabase() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Double.class))).thenReturn(4.0);
    var registry = new SimpleMeterRegistry();
    new OutboxMetrics(db, registry);
    for (String name :
        List.of(
            "book.outbox.pending",
            "book.outbox.failed",
            "book.outbox.leases",
            "book.outbox.oldest.seconds"))
      assertThat(registry.get(name).gauge().value()).isEqualTo(4);
  }
}
