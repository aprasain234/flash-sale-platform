package com.flashsale.order.repository;

import com.flashsale.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches outbox events in the order they were created.
     * In a production environment with high volume, you would want to paginate this
     * (e.g., using Pageable or findTop100By...) to prevent memory exhaustion.
     */
    List<OutboxEvent> findAllByOrderByCreatedAtAsc();
}