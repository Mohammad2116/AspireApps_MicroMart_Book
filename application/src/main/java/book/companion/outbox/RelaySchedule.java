package book.companion.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.relay.enabled", havingValue = "true")
public class RelaySchedule {
  private final OutboxRelay relay;

  public RelaySchedule(OutboxRelay relay) {
    this.relay = relay;
  }

  @Scheduled(fixedDelayString = "${app.relay.delay-ms:250}")
  public void tick() {
    relay.publishBatch();
  }
}
