package book.companion.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OutboxRelay {
  private final OutboxStore store;
  private final KafkaTemplate<String, String> kafka;
  private final MeterRegistry metrics;

  public OutboxRelay(
      OutboxStore store, KafkaTemplate<String, String> kafka, MeterRegistry metrics) {
    this.store = store;
    this.kafka = kafka;
    this.metrics = metrics;
  }

  /** Claims one row at a time, so later rows do not lose lease time waiting in a batch. */
  public int publishBatch() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Relay must run outside a caller transaction");
    int delivered = 0;
    for (int i = 0; i < 20; i++) {
      var next = store.claim();
      if (next.isEmpty()) break;
      var claim = next.get();
      try {
        kafka
            .send("orders.v1", claim.aggregate().toString(), claim.payload())
            .get(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        store.failed(claim);
        throw new IllegalStateException("Relay interrupted");
      } catch (Exception e) {
        store.failed(claim);
        metrics.counter("book.outbox.send.failures").increment();
        continue;
      }
      metrics.counter("book.outbox.acknowledged").increment();
      if (store.delivered(claim)) delivered++;
      else metrics.counter("book.outbox.stale.completions").increment();
    }
    return delivered;
  }
}
