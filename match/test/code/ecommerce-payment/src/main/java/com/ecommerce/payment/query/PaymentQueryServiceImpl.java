package com.ecommerce.payment.query;

import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentQueryServiceImpl(PaymentRecordRepository paymentRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
    }

    @Override
    public Optional<PaymentRecord> getPaymentByOrderId(Long orderId) {
        return paymentRecordRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS);
    }

    @Override
    public boolean isOrderPaid(Long orderId) {
        return paymentRecordRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS);
    }
}
