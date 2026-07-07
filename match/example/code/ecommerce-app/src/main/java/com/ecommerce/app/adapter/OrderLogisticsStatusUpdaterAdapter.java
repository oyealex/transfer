package com.ecommerce.app.adapter;

import com.ecommerce.logistics.query.OrderLogisticsStatusUpdater;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Component
public class OrderLogisticsStatusUpdaterAdapter implements OrderLogisticsStatusUpdater {

    private final OrderRepository orderRepository;

    public OrderLogisticsStatusUpdaterAdapter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void updateLogisticsStatus(Long orderId, String logisticsStatus) {
        if (orderId == null || logisticsStatus == null || logisticsStatus.isBlank()) {
            return;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        order.setLogisticsStatus(logisticsStatus);
        orderRepository.save(order);
    }
}
