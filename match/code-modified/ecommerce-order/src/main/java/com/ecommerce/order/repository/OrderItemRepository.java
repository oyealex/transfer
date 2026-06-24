package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link OrderItem} entities.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Find all items belonging to a specific order.
     */
    List<OrderItem> findByOrderId(Long orderId);
}
