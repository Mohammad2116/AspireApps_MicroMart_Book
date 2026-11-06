package book.companion.messaging;

import book.companion.orders.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsConsumer {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final MeterRegistry metrics;

  public AnalyticsConsumer(JdbcTemplate db, ObjectMapper json, MeterRegistry metrics) {
    this.db = db;
    this.json = json;
    this.metrics = metrics;
  }

  @KafkaListener(
      topics = "orders.v1",
      groupId = "book-analytics",
      autoStartup = "${app.consumer.enabled:false}")
  @Transactional
  public void receive(String payload) {
    final OrderService.OrderEvent event;
    try {
      event = json.readValue(payload, OrderService.OrderEvent.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Malformed order event", e);
    }
    if (event == null
        || event.eventId() == null
        || event.orderId() == null
        || event.total() == null
        || event.total().signum() < 0) throw new IllegalArgumentException("Invalid order event");
    int inserted =
        db.update(
            "insert into processed_event(consumer_name,event_id) values('analytics',?) on conflict"
                + " do nothing",
            event.eventId());
    if (inserted == 0) {
      metrics.counter("book.consumer.duplicates").increment();
      return;
    }
    db.update("insert into analytics(order_id,total) values(?,?)", event.orderId(), event.total());
  }
}
