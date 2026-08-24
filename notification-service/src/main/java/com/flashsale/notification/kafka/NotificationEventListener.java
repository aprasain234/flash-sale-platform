package com.flashsale.notification.kafka;

import com.flashsale.common.events.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    /**
     * Mock notification: logs instead of sending real email/SMS. Swap the body of
     * this method for an actual provider integration (SES, Twilio, etc.) later —
     * the Kafka wiring and idempotency shape stay the same either way.
     */
    @KafkaListener(topics = "payment-completed", groupId = "notification-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event.success()) {
            log.info("[NOTIFY] Order {} confirmed for seat {} — sending confirmation to user",
                    event.orderId(), event.seatId());
        } else {
            log.info("[NOTIFY] Order {} failed for seat {} — sending failure notice to user",
                    event.orderId(), event.seatId());
        }
    }
}
