package bank.account.integration;

import bank.account.domain.events.AccountDebitedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final MeterRegistry registry;

  public KafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry registry) {
    this.kafkaTemplate = kafkaTemplate;
    this.registry = registry;
  }

  @EventHandler
  public void on(AccountDebitedEvent evt) {
    kafkaTemplate.send("account.events.v1", evt.accountId, evt);
    registry.counter("domain.integration.event.published", "service", "account", "topic", "account.events.v1").increment();
  }
}
