package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByPaymentNo(String paymentNo);

    Optional<PaymentRecord> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<PaymentRecord> findByOrderId(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<PaymentRecord> findByStatusAndPaidAtBetween(PaymentStatus status, LocalDateTime start, LocalDateTime end);

    List<PaymentRecord> findByPaidAtBetween(LocalDateTime start, LocalDateTime end);
}
