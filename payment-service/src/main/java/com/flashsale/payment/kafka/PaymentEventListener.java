package com.flashsale.payment.kafka;

import com.flashsale.common.events.OrderCreatedEvent;
import com.flashsale.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private final PaymentService paymentService;

    public PaymentEventListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "order-created", groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        paymentService.processPayment(event);
    }
}
