package com.flashsale.payment.service;

import com.flashsale.common.events.OrderCreatedEvent;
import com.flashsale.common.events.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String TOPIC_PAYMENT_COMPLETED = "payment-completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // In-memory de-dup set as a placeholder idempotency guard for this mock service.
    // A real implementation should persist processed orderIds (e.g. in Postgres or
    // Redis with a long TTL) since this set is lost on restart and doesn't work
    // across replicas — noted here rather than silently shipped as production-ready.
    private final Set<String> processedOrderIds = ConcurrentHashMap.newKeySet();

    public PaymentService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Mock payment authorization: no real payment gateway integration here.
     * Simulates a ~90% success rate so downstream failure-path handling
     * (order cancellation, seat release) actually gets exercised.
     */
    public void processPayment(OrderCreatedEvent event) {
        if (!processedOrderIds.add(event.orderId())) {
            log.info("Duplicate OrderCreatedEvent for orderId={}, skipping", event.orderId());
            return;
        }

        boolean success = Math.random() > 0.10;
        String failureReason = success ? null : "MOCK_DECLINE";

        log.info("Processed mock payment for orderId={} success={}", event.orderId(), success);

        kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, event.seatId(),
                new PaymentCompletedEvent(event.orderId(), event.reservationId(), event.seatId(),
                        success, failureReason, Instant.now()));
    }
}
