package com.flashsale.inventory.config;

import com.flashsale.common.events.OrderFailedEvent;
import com.flashsale.inventory.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderFailureListener {

    private static final Logger log = LoggerFactory.getLogger(OrderFailureListener.class);
    private final ReservationService reservationService;

    public OrderFailureListener(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @KafkaListener(topics = "order-failed", groupId = "inventory-service")
    public void onOrderFailed(OrderFailedEvent event) {
        log.warn("Received OrderFailedEvent for order {}. Reason: {}. Executing immediate Redis release for seat {}.",
                event.orderId(), event.reason(), event.seatId());

        boolean released = reservationService.release(event.eventId(), event.seatId());

        if (released) {
            log.info("SUCCESS: Seat {} for event {} actively returned to the available pool.", event.seatId(), event.eventId());
        } else {
            log.info("SKIPPED: Seat {} hold had already expired naturally via Redis TTL.", event.seatId());
        }
    }
}
