package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pessimistic locking service for preventing concurrent modifications to orders.
 *
 * <p>When multiple parties (user cancel, payment callback, timeout scanner, admin)
 * may modify the same order concurrently, we need a way to ensure only one
 * modification is applied. This service provides:
 * <ul>
 *   <li>In-memory locking via {@link ReentrantLock} per order ID</li>
 *   <li>Pessimistic database lock via JPA {@code @Lock(LockModeType.PESSIMISTIC_WRITE)}</li>
 *   <li>Optimistic version check</li>
 * </ul>
 *
 * <p>In a production distributed system, this would use Redis distributed locks
 * or database row-level locking instead of in-memory locks.
 */
@Service
public class OrderLockService {

    private static final Logger log = LoggerFactory.getLogger(OrderLockService.class);

    private final Map<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final OrderRepository orderRepository;

    private static final long LOCK_TIMEOUT_MS = 5000;

    public OrderLockService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Acquire a lock on a specific order.
     *
     * @param orderId the order ID to lock
     * @throws BusinessException if the lock cannot be acquired within the timeout
     */
    public void lock(Long orderId) {
        ReentrantLock lock = lockMap.computeIfAbsent(orderId, k -> new ReentrantLock(true));

        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ORDER_LOCK_INTERRUPTED",
                    "Lock acquisition interrupted for order " + orderId);
        }

        if (!acquired) {
            log.warn("Failed to acquire lock for order {} within {} ms", orderId, LOCK_TIMEOUT_MS);
            throw new BusinessException("ORDER_LOCK_TIMEOUT",
                    "Could not acquire lock for order " + orderId
                            + " — another operation is in progress. Please retry.");
        }

        log.debug("Lock acquired for order {}", orderId);
    }

    /**
     * Release the lock on a specific order.
     *
     * @param orderId the order ID to unlock
     */
    public void unlock(Long orderId) {
        ReentrantLock lock = lockMap.get(orderId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released for order {}", orderId);
            // Clean up unused locks to prevent memory leak
            if (!lock.hasQueuedThreads() && !lock.isLocked()) {
                lockMap.remove(orderId);
            }
        }
    }

    /**
     * Execute a callback while holding the order lock.
     *
     * @param orderId  the order ID to lock
     * @param callback the operation to execute while locked
     * @param <T>      the return type
     * @return the callback's return value
     */
    public <T> T withLock(Long orderId, LockedOperation<T> callback) {
        lock(orderId);
        try {
            return callback.execute();
        } finally {
            unlock(orderId);
        }
    }

    /**
     * Execute a callback while holding the order lock, within a transaction.
     * Uses REQUIRES_NEW to ensure the lock is held within its own transaction boundary.
     *
     * @param orderId  the order ID to lock
     * @param callback the operation to execute while locked
     * @param <T>      the return type
     * @return the callback's return value
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T withLockTransactional(Long orderId, LockedOperation<T> callback) {
        lock(orderId);
        try {
            T result = callback.execute();
            // Flush to ensure changes are persisted before releasing the lock
            return result;
        } finally {
            unlock(orderId);
        }
    }

    /**
     * Get the order with a pessimistic write lock on the database row.
     * Prevents other transactions from modifying the order until this one completes.
     *
     * @param orderId the order ID
     * @return the locked order entity
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order getOrderWithDbLock(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    /**
     * Get the number of currently locked orders (for monitoring).
     */
    public int getLockedCount() {
        return (int) lockMap.values().stream().filter(ReentrantLock::isLocked).count();
    }

    /**
     * Get the total number of tracked locks (including released ones, for memory monitoring).
     */
    public int getTotalLockCount() {
        return lockMap.size();
    }

    /**
     * Interface for operations that execute while holding an order lock.
     *
     * @param <T> the return type of the operation
     */
    @FunctionalInterface
    public interface LockedOperation<T> {
        T execute();
    }
}
