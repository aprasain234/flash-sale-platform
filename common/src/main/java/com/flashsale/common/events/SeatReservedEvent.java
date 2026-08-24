package com.flashsale.common.events;

import java.time.Instant;

/**
 * Published by inventory-service when a seat hold succeeds in Redis.
 * order-service consumes this to create a PENDING order.
 */
public record SeatReservedEvent(
        String reservationId,
        String eventId,
        String seatId,
        String userId,
        Instant reservedAt,
        Instant expiresAt
) {}
