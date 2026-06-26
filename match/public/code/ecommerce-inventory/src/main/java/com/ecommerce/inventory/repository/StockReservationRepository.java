package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    List<StockReservation> findByOrderId(Long orderId);

    Optional<StockReservation> findByOrderIdAndSkuIdAndWarehouseId(Long orderId, Long skuId, Long warehouseId);
}
