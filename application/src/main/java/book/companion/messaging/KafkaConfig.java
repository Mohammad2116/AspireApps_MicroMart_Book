package book.companion.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
  @Bean
  NewTopic ordersTopic() {
    return TopicBuilder.name("orders.v1").partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic deadLetterTopic() {
    return TopicBuilder.name("orders.v1.DLT").partitions(1).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler errorHandler(
      KafkaTemplate<String, String> kafka, io.micrometer.core.instrument.MeterRegistry metrics) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            kafka, (record, ex) -> new TopicPartition("orders.v1.DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(200L, 2L));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    handler.setRetryListeners(
        new org.springframework.kafka.listener.RetryListener() {
          public void failedDelivery(
              org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> r, Exception e, int attempt) {
            metrics.counter("book.consumer.failures").increment();
          }

          public void recovered(
              org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> r, Exception e) {
            metrics.counter("book.consumer.dlt").increment();
          }
        });
    return handler;
  }
}
