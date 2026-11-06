package book.companion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import book.companion.messaging.AnalyticsConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class InvalidEventTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "{}",
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\"}",
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"orderId\":\"00000000-0000-0000-0000-000000000001\"}",
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"orderId\":\"00000000-0000-0000-0000-000000000001\",\"total\":-1}"
      })
  void invalidEnvelopeNeverWritesMarker(String json) {
    var db = mock(JdbcTemplate.class);
    var consumer = new AnalyticsConsumer(db, new ObjectMapper(), new SimpleMeterRegistry());
    assertThatThrownBy(() -> consumer.receive(json)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(db);
  }
}
