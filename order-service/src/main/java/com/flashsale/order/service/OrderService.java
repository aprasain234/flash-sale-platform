package com.flashsale.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.events.OrderCreatedEvent;
import com.flashsale.common.events.PaymentCompletedEvent;
import com.flashsale.common.events.SeatReservedEvent;
import com.flashsale.order.entity.Order;
import com.flashsale.order.entity.OutboxEvent;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal FLAT_TICKET_PRICE = new BigDecimal("49.99");

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleSeatReserved(SeatReservedEvent event) {
        if (orderRepository.findByIdempotencyKey(event.reservationId()).isPresent()) {
            log.info("Duplicate SeatReservedEvent for reservationId={}, skipping", event.reservationId());
            return;
        }

        Order order = new Order(
                event.reservationId(), event.eventId(), event.seatId(),
                event.userId(), FLAT_TICKET_PRICE, event.reservationId()
        );

        try {
            // Save the order to Postgres
            orderRepository.save(order);

            OrderCreatedEvent outboxPayload = new OrderCreatedEvent(
                    order.getId(), order.getReservationId(), order.getEventId(),
                    order.getSeatId(), order.getUserId(), order.getAmount(),
                    order.getIdempotencyKey(), order.getCreatedAt()
            );

            // Save the event to the Outbox table in the same transaction
            OutboxEvent outboxEvent = new OutboxEvent(
                    "Order", order.getId().toString(), "OrderCreatedEvent",
                    objectMapper.writeValueAsString(outboxPayload)
            );
            outboxEventRepository.save(outboxEvent);

        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate order creation for reservationId={}, ignoring", event.reservationId());
            return;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if (order.getStatus() != com.flashsale.order.entity.OrderStatus.PENDING) {
                log.info("Order {} already in terminal state {}, ignoring duplicate payment event",
                        order.getId(), order.getStatus());
                return;
            }
            if (event.success()) {
                order.markConfirmed();
            } else {
                order.markFailed(event.failureReason());
            }
            orderRepository.save(order);
        }, () -> log.warn("PaymentCompletedEvent for unknown orderId={}", event.orderId()));
    }
}