package com.flashsale.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record ReservationRequest(
        @NotBlank String eventId,
        @NotBlank String seatId,
        @NotBlank String userId
) {}
