package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.InvoiceRecord;
import com.ecommerce.payment.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, Long> {

    Optional<InvoiceRecord> findByInvoiceNo(String invoiceNo);

    List<InvoiceRecord> findByOrderId(Long orderId);

    List<InvoiceRecord> findByUserId(Long userId);

    List<InvoiceRecord> findByOrderIdAndStatus(Long orderId, InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.invoiceAmount), 0) FROM InvoiceRecord i WHERE i.orderId = :orderId AND i.status = :status")
    BigDecimal sumInvoiceAmountByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") InvoiceStatus status);
}
