package com.flashsale.inventory.service;

import com.flashsale.common.events.SeatReservedEvent;
import com.flashsale.inventory.dto.ReservationRequest;
import com.flashsale.inventory.dto.ReservationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

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
     * under concurrent requests for the same seat or the same counter — Redis
     * executes scripts atomically, there is no read-then-write race here.
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

        // Publish so order-service can create a PENDING order against this hold.
        // NOTE: this is an at-least-once publish (no outbox pattern here yet) —
        // order-service's consumer must be idempotent on reservationId. See
        // order-service/service/OrderService for how duplicates are handled.
        kafkaTemplate.send(TOPIC_SEAT_RESERVED, request.seatId(),
                new SeatReservedEvent(reservationId, request.eventId(), request.seatId(),
                        request.userId(), now, expiresAt));

        return new ReservationResponse(reservationId, "RESERVED", expiresAt);
    }

    /**
     * Explicit release, used when an order is cancelled or payment fails.
     * If the hold has already expired naturally via Redis TTL, this is a no-op
     * (the Lua script returns 0 and nothing bad happens) — safe to call blindly.
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
