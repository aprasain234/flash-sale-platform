package com.flashsale.order.relay;

import com.flashsale.order.entity.OutboxEvent;
import com.flashsale.order.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        // Fetch oldest unprocessed events
        List<OutboxEvent> events = repository.findAllByOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                // Synchronous send (.get()) to ensure broker receives it before DB deletion
                kafkaTemplate.send("order-created", event.getAggregateId(), event.getPayload()).get();
                repository.delete(event);
            } catch (Exception e) {
                // If the broker is unreachable, break the loop.
                // The event remains in Postgres and will be retried on the next poll.
                break;
            }
        }
    }
}