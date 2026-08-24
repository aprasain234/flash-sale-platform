package com.flashsale.order.repository;

import com.flashsale.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    Optional<Order> findByReservationId(String reservationId);
}
