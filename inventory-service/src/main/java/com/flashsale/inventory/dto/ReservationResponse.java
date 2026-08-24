package com.flashsale.inventory.dto;

import java.time.Instant;

public record ReservationResponse(
        String reservationId,
        String status,   // RESERVED | ALREADY_HELD | SOLD_OUT
        Instant expiresAt
) {}
