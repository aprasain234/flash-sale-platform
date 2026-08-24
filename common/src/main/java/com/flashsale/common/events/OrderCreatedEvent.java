package com.flashsale.common.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by order-service once a PENDING order row is durably written to Postgres.
 * payment-service consumes this to attempt payment.
 */
public record OrderCreatedEvent(
        String orderId,
        String reservationId,
        String eventId,
        String seatId,
        String userId,
        BigDecimal amount,
        String idempotencyKey,
        Instant createdAt
) {}
