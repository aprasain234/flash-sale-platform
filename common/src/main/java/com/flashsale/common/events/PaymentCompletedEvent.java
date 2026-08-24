package com.flashsale.common.events;

import java.time.Instant;

/**
 * Published by payment-service after a payment attempt resolves (success or failure).
 * order-service consumes this to confirm or cancel the order.
 * inventory-service consumes this to release the seat on failure.
 */
public record PaymentCompletedEvent(
        String orderId,
        String reservationId,
        String seatId,
        boolean success,
        String failureReason,
        Instant completedAt
) {}
