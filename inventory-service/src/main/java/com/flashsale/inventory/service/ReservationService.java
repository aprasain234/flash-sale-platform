package com.flashsale.inventory.service;

import com.flashsale.common.events.SeatReservedEvent;
import com.flashsale.inventory.dto.ReservationRequest;
import com.flashsale.inventory.dto.ReservationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final String TOPIC_SEAT_RESERVED = "seat-reserved";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> reserveSeatScript;
    private final DefaultRedisScript<Long> releaseSeatScript;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${inventory.hold-ttl-seconds:300}")
    private long holdTtlSeconds;

    public ReservationService(RedisTemplate<String, String> redisTemplate,
                              DefaultRedisScript<Long> reserveSeatScript,
                              DefaultRedisScript<Long> releaseSeatScript,
                              KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveSeatScript = reserveSeatScript;
        this.releaseSeatScript = releaseSeatScript;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Attempts to place a hold on a single seat.
     *
     * Concurrency note: the check-remaining / decrement / set-hold-key sequence
     * happens inside a single Lua script (see reserve_seat.lua), so this is safe
     * under concurrent requests for the same seat or the same counter.
     */
    public ReservationResponse reserve(ReservationRequest request) {
        String availableKey = "seats:available:" + request.eventId();
        String holdKey = "reservation:" + request.eventId() + ":" + request.seatId();

        Long result = redisTemplate.execute(
                reserveSeatScript,
                List.of(availableKey, holdKey),
                request.userId(),
                String.valueOf(holdTtlSeconds)
        );

        if (result == null || result == -1L) {
            return new ReservationResponse(null, "SOLD_OUT", null);
        }
        if (result == 0L) {
            return new ReservationResponse(null, "ALREADY_HELD", null);
        }

        String reservationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(holdTtlSeconds);

        SeatReservedEvent eventPayload = new SeatReservedEvent(
                reservationId, request.eventId(), request.seatId(),
                request.userId(), now, expiresAt
        );

        try {
            // Synchronously wait for Kafka acknowledgement with a strict timeout.
            // This prevents the user from receiving a false "RESERVED" response if the event is lost.
            kafkaTemplate.send(TOPIC_SEAT_RESERVED, request.seatId(), eventPayload)
                    .get(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to publish SeatReservedEvent for reservationId={}. Executing Redis rollback.", reservationId, e);

            // Compensating Transaction
            boolean released = release(request.eventId(), request.seatId());
            if (released) {
                log.info("Rollback successful: Seat {} for event {} returned to available pool.", request.seatId(), request.eventId());
            } else {
                log.error("Rollback failed: Seat {} is orphaned in Redis until TTL expires.", request.seatId());
            }

            // Return failure to the client so the UI does not proceed to checkout
            return new ReservationResponse(null, "RESERVATION_FAILED", null);
        }

        return new ReservationResponse(reservationId, "RESERVED", expiresAt);
    }

    /**
     * Explicit release, used when an order is cancelled, payment fails,
     * or the Kafka publish compensation transaction executes.
     */
    public boolean release(String eventId, String seatId) {
        String availableKey = "seats:available:" + eventId;
        String holdKey = "reservation:" + eventId + ":" + seatId;

        Long result = redisTemplate.execute(
                releaseSeatScript,
                List.of(availableKey, holdKey)
        );
        return result != null && result == 1L;
    }
}