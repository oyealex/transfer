package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OrderRepository} using JPA slice testing.
 * Each test runs in a transaction that is rolled back after the test,
 * providing isolation between tests.
 */
@DataJpaTest
@DisplayName("OrderRepository")
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 7, 10, 0, 0);

    // ======================== CRUD ========================

    @Test
    @DisplayName("save and findById: basic CRUD works")
    void testSaveAndFindById() {
        Order order = buildOrder("SO202606070001", 100L, OrderStatus.CREATED,
                new BigDecimal("102.00"), NOW.plusHours(1));

        Order saved = entityManager.persistFlushFind(order);

        assertThat(saved.getId()).isNotNull();

        Optional<Order> found = orderRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getOrderNo()).isEqualTo("SO202606070001");
        assertThat(found.get().getUserId()).isEqualTo(100L);
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(found.get().getPayableAmount()).isEqualTo(new BigDecimal("102.00"));
    }

    @Test
    @DisplayName("update: order status can be updated")
    void testUpdateOrderStatus() {
        Order order = buildOrder("SO202606070010", 100L, OrderStatus.CREATED,
                new BigDecimal("102.00"), NOW.plusHours(1));
        Order saved = entityManager.persistFlushFind(order);

        saved.setStatus(OrderStatus.PAID);
        saved.setPaymentNo("PAY001");
        saved.setPaidAmount(saved.getPayableAmount());
        entityManager.persistFlushFind(saved);

        Order updated = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updated.getPaymentNo()).isEqualTo("PAY001");
    }

    @Test
    @DisplayName("delete: order can be deleted")
    void testDeleteOrder() {
        Order order = buildOrder("SO202606070011", 100L, OrderStatus.CREATED,
                new BigDecimal("102.00"), NOW.plusHours(1));
        Order saved = entityManager.persistFlushFind(order);
        Long id = saved.getId();

        orderRepository.deleteById(id);
        entityManager.flush();

        assertThat(orderRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("findAll: returns all persisted orders")
    void testFindAll() {
        entityManager.persist(buildOrder("SO202606070020", 100L, OrderStatus.CREATED,
                new BigDecimal("50.00"), NOW.plusHours(1)));
        entityManager.persist(buildOrder("SO202606070021", 100L, OrderStatus.PAID,
                new BigDecimal("150.00"), NOW.plusHours(2)));
        entityManager.flush();

        List<Order> orders = orderRepository.findAll();

        assertThat(orders).hasSize(2);
    }

    // ======================== findByOrderNo ========================

    @Test
    @DisplayName("findByOrderNo: finds by exact order number")
    void testFindByOrderNo() {
        Order order = buildOrder("SO202606070030", 100L, OrderStatus.CREATED,
                new BigDecimal("80.00"), NOW.plusHours(1));
        entityManager.persistFlushFind(order);

        Optional<Order> found = orderRepository.findByOrderNo("SO202606070030");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(100L);

        Optional<Order> notFound = orderRepository.findByOrderNo("NONEXISTENT");
        assertThat(notFound).isEmpty();
    }

    // ======================== findByUserId ========================

    @Test
    @DisplayName("findByUserId: returns user orders paginated")
    void testFindByUserId_paginated() {
        entityManager.persist(buildOrder("SO202606070040", 100L, OrderStatus.CREATED,
                new BigDecimal("100.00"), NOW.plusHours(1)));
        entityManager.persist(buildOrder("SO202606070041", 100L, OrderStatus.PAID,
                new BigDecimal("200.00"), NOW.plusHours(2)));
        entityManager.flush();

        Page<Order> page = orderRepository.findByUserId(100L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        for (Order o : page.getContent()) {
            assertThat(o.getUserId()).isEqualTo(100L);
        }
    }

    @Test
    @DisplayName("findByUserId: returns empty page for user with no orders")
    void testFindByUserId_noOrders() {
        Page<Order> page = orderRepository.findByUserId(999L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getContent()).isEmpty();
    }

    // ======================== findByStatusAndExpiresAtBefore ========================

    @Test
    @DisplayName("findByStatusAndExpiresAtBefore: finds expired CREATED orders")
    void testFindByStatusAndExpiresAtBefore() {
        entityManager.persist(buildOrder("SO202606070050", 100L, OrderStatus.CREATED,
                new BigDecimal("100.00"), NOW.plusHours(1))); // not expired
        entityManager.persist(buildOrder("SO202606070051", 200L, OrderStatus.CREATED,
                new BigDecimal("50.00"), NOW.minusHours(1))); // expired
        entityManager.flush();

        List<Order> expired = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.CREATED, NOW);

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getOrderNo()).isEqualTo("SO202606070051");
    }

    @Test
    @DisplayName("findByStatusAndExpiresAtBefore: excludes non-CREATED orders")
    void testFindByStatusAndExpiresAtBefore_excludesNonCreated() {
        entityManager.persist(buildOrder("SO202606070052", 100L, OrderStatus.PAID,
                new BigDecimal("100.00"), NOW.minusHours(2))); // expired but PAID
        entityManager.flush();

        List<Order> expired = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.CREATED, NOW);

        assertThat(expired).isEmpty();
    }

    @Test
    @DisplayName("findByStatusAndExpiresAtBefore: no expired orders returns empty list")
    void testFindByStatusAndExpiresAtBefore_noExpired() {
        entityManager.persist(buildOrder("SO202606070053", 100L, OrderStatus.CREATED,
                new BigDecimal("100.00"), NOW.plusHours(1)));
        entityManager.flush();

        List<Order> expired = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.CREATED, NOW);

        assertThat(expired).isEmpty();
    }

    // ======================== findByExternalOrderNoAndUserId ========================

    @Test
    @DisplayName("findByExternalOrderNoAndUserId: finds by external order number")
    void testFindByExternalOrderNoAndUserId() {
        Order order = buildOrder("SO202606070060", 100L, OrderStatus.CREATED,
                new BigDecimal("80.00"), NOW.plusHours(1));
        order.setExternalOrderNo("EXT-002");
        entityManager.persistFlushFind(order);

        Optional<Order> found = orderRepository.findByExternalOrderNoAndUserId("EXT-002", 100L);
        assertThat(found).isPresent();
        assertThat(found.get().getOrderNo()).isEqualTo("SO202606070060");

        Optional<Order> notFound = orderRepository.findByExternalOrderNoAndUserId("EXT-999", 100L);
        assertThat(notFound).isEmpty();
    }

    // ======================== Order fields ========================

    @Test
    @DisplayName("order entity persists all fields correctly")
    void testOrder_persistsAllFields() {
        Order order = buildOrder("SO202606070070", 100L, OrderStatus.PAID,
                new BigDecimal("183.00"), NOW.plusHours(2));
        order.setExternalOrderNo("EXT-070");
        order.setPaymentNo("PAY0070");
        order.setPaidAmount(new BigDecimal("183.00"));
        order.setPaidAt(NOW.plusMinutes(10));

        Order saved = entityManager.persistFlushFind(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrderNo()).isEqualTo("SO202606070070");
        assertThat(saved.getUserId()).isEqualTo(100L);
        assertThat(saved.getExternalOrderNo()).isEqualTo("EXT-070");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getItemTotal()).isEqualTo(new BigDecimal("200.00"));
        assertThat(saved.getShippingFee()).isEqualTo(new BigDecimal("8.00"));
        assertThat(saved.getPackagingFee()).isEqualTo(new BigDecimal("2.00"));
        assertThat(saved.getDiscountAmount()).isEqualTo(new BigDecimal("20.00"));
        assertThat(saved.getPointsDeductionAmount()).isEqualTo(new BigDecimal("5.00"));
        assertThat(saved.getPayableAmount()).isEqualTo(new BigDecimal("183.00"));
        assertThat(saved.getPaidAmount()).isEqualTo(new BigDecimal("183.00"));
        assertThat(saved.getPaymentNo()).isEqualTo("PAY0070");
        assertThat(saved.getPaidAt()).isNotNull();
    }

    // ======================== Helper ========================

    private Order buildOrder(String orderNo, Long userId, OrderStatus status,
                              BigDecimal payableAmount, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStatus(status);
        order.setItemTotal(new BigDecimal("200.00"));
        order.setShippingFee(new BigDecimal("8.00"));
        order.setPackagingFee(new BigDecimal("2.00"));
        order.setDiscountAmount(new BigDecimal("20.00"));
        order.setPointsDeductionAmount(new BigDecimal("5.00"));
        order.setPayableAmount(payableAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setExpiresAt(expiresAt);
        order.setCreatedAt(NOW);
        order.setUpdatedAt(NOW);
        return order;
    }
}
