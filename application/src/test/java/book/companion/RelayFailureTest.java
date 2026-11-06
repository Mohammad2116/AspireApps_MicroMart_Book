package book.companion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import book.companion.outbox.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class RelayFailureTest {
  OutboxStore store = mock(OutboxStore.class);

  @SuppressWarnings("unchecked")
  KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

  SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  OutboxStore.Claim c =
      new OutboxStore.Claim(UUID.randomUUID(), UUID.randomUUID(), "{}", UUID.randomUUID(), 1);

  @Test
  void staleAcknowledgmentIsNotCountedDelivered() {
    when(store.claim()).thenReturn(Optional.of(c), Optional.empty());
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    assertThat(new OutboxRelay(store, kafka, metrics).publishBatch()).isZero();
    assertThat(metrics.counter("book.outbox.stale.completions").count()).isEqualTo(1);
  }

  @Test
  void interruptionIsPreservedAndClaimIsReleased() throws Exception {
    @SuppressWarnings("unchecked")
    CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future =
        mock(CompletableFuture.class);
    when(store.claim()).thenReturn(Optional.of(c));
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
    when(future.get(anyLong(), any())).thenThrow(new InterruptedException());
    try {
      assertThatThrownBy(() -> new OutboxRelay(store, kafka, metrics).publishBatch())
          .isInstanceOf(IllegalStateException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(store).failed(c);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void batchWorkIsBounded() {
    when(store.claim()).thenReturn(Optional.of(c));
    when(store.delivered(c)).thenReturn(true);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    assertThat(new OutboxRelay(store, kafka, metrics).publishBatch()).isEqualTo(20);
    verify(store, times(20)).claim();
  }
}
