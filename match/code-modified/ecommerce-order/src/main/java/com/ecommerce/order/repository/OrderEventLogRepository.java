package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link OrderEventLog} entities.
 */
@Repository
public interface OrderEventLogRepository extends JpaRepository<OrderEventLog, Long> {

    /**
     * Find all event logs for an order, ordered by creation time ascending.
     */
    List<OrderEventLog> findByOrderIdOrderByCreatedAtLogAsc(Long orderId);
}
