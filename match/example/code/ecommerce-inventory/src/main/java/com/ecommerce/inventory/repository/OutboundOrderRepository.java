package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.OutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    List<OutboundOrder> findByOrderId(Long orderId);
}
