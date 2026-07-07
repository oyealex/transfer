package com.ecommerce.payment.query;

import com.ecommerce.payment.entity.PaymentRecord;

import java.util.Optional;

public interface PaymentQueryService {

    Optional<PaymentRecord> getPaymentByOrderId(Long orderId);

    boolean isOrderPaid(Long orderId);
}
