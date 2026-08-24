package com.flashsale.order.kafka;

import com.flashsale.common.events.PaymentCompletedEvent;
import com.flashsale.common.events.SeatReservedEvent;
import com.flashsale.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderService orderService;

    public OrderEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "seat-reserved", groupId = "order-service",
            containerFactory = "seatReservedListenerFactory")
    public void onSeatReserved(SeatReservedEvent event) {
        orderService.handleSeatReserved(event);
    }

    @KafkaListener(topics = "payment-completed", groupId = "order-service",
            containerFactory = "paymentCompletedListenerFactory")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        orderService.handlePaymentCompleted(event);
    }
}
