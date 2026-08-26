package com.flashsale.order.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.events.OrderCreatedEvent;
import com.flashsale.common.events.OrderFailedEvent;
import com.flashsale.order.entity.OutboxEvent;
import com.flashsale.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        List<OutboxEvent> events = repository.findAllByOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                // 1. Convert the raw DB string back into a typed Java Object
                Object typedPayload = switch (event.getEventType()) {
                    case "OrderCreatedEvent" -> objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);
                    case "OrderFailedEvent" -> objectMapper.readValue(event.getPayload(), OrderFailedEvent.class);
                    default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
                };

                String targetTopic = switch (event.getEventType()) {
                    case "OrderCreatedEvent" -> "order-created";
                    case "OrderFailedEvent" -> "order-failed";
                    default -> "unknown-topic";
                };

                // 2. Send the typed object so JsonSerializer attaches the correct __TypeId__ header
                kafkaTemplate.send(targetTopic, event.getAggregateId(), typedPayload).get();

                repository.delete(event);
            } catch (Exception e) {
                log.error("Outbox processing failed for event ID: {}", event.getId(), e);
                break;
            }
        }
    }
}