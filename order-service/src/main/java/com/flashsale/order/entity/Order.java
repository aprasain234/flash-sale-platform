package com.flashsale.order.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders", uniqueConstraints = {
        // The actual idempotency guard: a DB-level unique constraint, not just
        // an application-level check-then-insert (which races under concurrency).
        @UniqueConstraint(name = "uk_orders_idempotency_key", columnNames = "idempotency_key")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String reservationId;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String seatId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected Order() {
        // JPA
    }

    public Order(String reservationId, String eventId, String seatId, String userId,
                 BigDecimal amount, String idempotencyKey) {
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.seatId = seatId;
        this.userId = userId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getReservationId() { return reservationId; }
    public String getEventId() { return eventId; }
    public String getSeatId() { return seatId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markConfirmed() {
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = OrderStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
