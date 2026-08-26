package com.flashsale.common.events;

public record OrderFailedEvent(
        String orderId,
        String reservationId,
        String eventId,
        String seatId,
        String reason
) {}