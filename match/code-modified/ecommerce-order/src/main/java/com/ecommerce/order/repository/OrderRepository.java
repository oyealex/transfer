package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Order} entities.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find an order by its unique order number.
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * Find all orders for a user, paginated.
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * Find orders with the given status that have expired.
     * Used by the timeout scanner to find orders past their expiry time.
     */
    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime now);

    /**
     * Find orders by external order number (used for deduplication in batch).
     */
    Optional<Order> findByExternalOrderNoAndUserId(String externalOrderNo, Long userId);

    /**
     * Sum of payable_amount for paid/delivered orders of a user after a given date.
     * Used by the loyalty module to calculate annual consumption for member levels.
     */
    @Query("SELECT COALESCE(SUM(o.payableAmount), 0) FROM Order o "
            + "WHERE o.userId = :userId "
            + "AND o.status IN ('PAID', 'SHIPPED', 'DELIVERED', 'COMPLETED') "
            + "AND o.paidAt >= :startOfYear")
    BigDecimal sumPaidAmountByUserIdAndStatusAndPaidAtAfter(
            @Param("userId") Long userId,
            @Param("startOfYear") LocalDateTime startOfYear);
}
