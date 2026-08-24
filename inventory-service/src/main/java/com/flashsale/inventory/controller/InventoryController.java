package com.flashsale.inventory.controller;

import com.flashsale.inventory.dto.ReservationRequest;
import com.flashsale.inventory.dto.ReservationResponse;
import com.flashsale.inventory.service.ReservationService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ReservationService reservationService;

    public InventoryController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/reserve")
    @RateLimiter(name = "reserveSeat")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.reserve(request);
        return switch (response.status()) {
            case "RESERVED" -> ResponseEntity.status(HttpStatus.CREATED).body(response);
            case "ALREADY_HELD" -> ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            default -> ResponseEntity.status(HttpStatus.GONE).body(response); // SOLD_OUT
        };
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@RequestParam String eventId, @RequestParam String seatId) {
        reservationService.release(eventId, seatId);
        return ResponseEntity.noContent().build();
    }
}
