package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryReservationServiceImpl")
@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceImplTest {

    @Mock
    private InventoryStockRepository stockRepo;

    @Mock
    private StockReservationRepository stockReservationRepo;

    @InjectMocks
    private InventoryReservationServiceImpl reservationService;

    private InventoryStock stock;

    @BeforeEach
    void setUp() {
        stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(200);
        stock.setReservedStock(0);
        stock.setSafetyStock(5);
    }

    // ---- reserve tests ----

    @Test
    @DisplayName("reserve decreases onHandStock and increases reservedStock")
    void testReserve_decreasesOnHandStock() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(stockReservationRepo.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ReserveItem> items = List.of(new ReserveItem(100L, 40));
        reservationService.reserve(1L, items);

        // onHandStock is incorrectly decreased during reserve
        assertThat(stock.getOnHandStock()).isEqualTo(160); // 200 - 40
        // reservedStock is correctly increased
        assertThat(stock.getReservedStock()).isEqualTo(40);
        // availableStock = onHandStock - reservedStock = 160 - 40 = 120
        assertThat(stock.getAvailableStock()).isEqualTo(120);
    }

    @Test
    @DisplayName("reserve distributes quantity across multiple warehouses")
    void testReserve_distributesAcrossWarehouses() {
        InventoryStock stock2 = new InventoryStock();
        stock2.setId(2L);
        stock2.setWarehouseId(2L);
        stock2.setSkuId(100L);
        stock2.setOnHandStock(50);
        stock2.setReservedStock(0);

        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock, stock2));
        when(stockRepo.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepo.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ReserveItem> items = List.of(new ReserveItem(100L, 250));
        // First warehouse: max 200, reserves 200. Remaining: 50.
        // Second warehouse: max 50, reserves 50.
        reservationService.reserve(1L, items);

        // Warehouse 1: onHand = 200-200 = 0, reserved = 200
        assertThat(stock.getOnHandStock()).isEqualTo(0);
        assertThat(stock.getReservedStock()).isEqualTo(200);
        // Warehouse 2: onHand = 50-50 = 0, reserved = 50
        assertThat(stock2.getOnHandStock()).isEqualTo(0);
        assertThat(stock2.getReservedStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("reserve throws BusinessException when stock is insufficient")
    void testReserve_throwsWhenInsufficientStock() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        List<ReserveItem> items = List.of(new ReserveItem(100L, 300));

        assertThatThrownBy(() -> reservationService.reserve(1L, items))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode().equals("INSUFFICIENT_STOCK"),
                        "should have code INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("reserve skips warehouses with zero available stock")
    void testReserve_skipsZeroAvailableStock() {
        InventoryStock emptyStock = new InventoryStock();
        emptyStock.setId(2L);
        emptyStock.setWarehouseId(2L);
        emptyStock.setSkuId(100L);
        emptyStock.setOnHandStock(10);
        emptyStock.setReservedStock(10); // available = 0

        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(emptyStock, stock));
        when(stockRepo.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepo.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ReserveItem> items = List.of(new ReserveItem(100L, 40));
        reservationService.reserve(1L, items);

        // emptyStock should be skipped (available=0)
        assertThat(emptyStock.getOnHandStock()).isEqualTo(10);
        assertThat(emptyStock.getReservedStock()).isEqualTo(10);
        // stock should handle the full quantity
        assertThat(stock.getOnHandStock()).isEqualTo(160);
        assertThat(stock.getReservedStock()).isEqualTo(40);
    }

    // ---- release tests ----

    @Test
    @DisplayName("release decreases reservedStock (onHandStock stays decreased due to L2-03)")
    void testRelease_increasesAvailableStock() {
        // Simulate post-reserve state (onHand decreased during reserve)
        stock.setOnHandStock(160);
        stock.setReservedStock(40);

        StockReservation reservation = new StockReservation();
        reservation.setId(1L);
        reservation.setOrderId(1L);
        reservation.setSkuId(100L);
        reservation.setWarehouseId(1L);
        reservation.setQuantity(40);
        reservation.setStatus(ReservationStatus.RESERVED);

        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(stockReservationRepo.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        reservationService.release(1L);

        // reservedStock decreased to 0
        assertThat(stock.getReservedStock()).isEqualTo(0);
        // onHandStock stays at 160 (not restored — L2-03 side effect)
        assertThat(stock.getOnHandStock()).isEqualTo(160);
        // availableStock = 160 - 0 = 160
        assertThat(stock.getAvailableStock()).isEqualTo(160);
        // reservation status updated to RELEASED
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("release throws ResourceNotFoundException when stock is missing")
    void testRelease_throwsWhenStockNotFound() {
        StockReservation reservation = new StockReservation();
        reservation.setId(1L);
        reservation.setOrderId(1L);
        reservation.setSkuId(100L);
        reservation.setWarehouseId(1L);
        reservation.setQuantity(40);
        reservation.setStatus(ReservationStatus.RESERVED);

        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.release(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("release does nothing when no RESERVED reservations exist")
    void testRelease_noReservedReservations_doesNothing() {
        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of());

        reservationService.release(1L);

        verify(stockRepo, never()).save(any());
        verify(stockReservationRepo, never()).save(any());
    }

    // ---- deductAfterPayment tests ----

    @Test
    @DisplayName("deductAfterPayment decreases both onHandStock and reservedStock")
    void testDeductAfterPayment_adjustsBothStocks() {
        // Simulate post-reserve state (onHand decreased during reserve)
        stock.setOnHandStock(160);
        stock.setReservedStock(40);

        StockReservation reservation = new StockReservation();
        reservation.setId(1L);
        reservation.setOrderId(1L);
        reservation.setSkuId(100L);
        reservation.setWarehouseId(1L);
        reservation.setQuantity(40);
        reservation.setStatus(ReservationStatus.RESERVED);

        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(stockReservationRepo.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        reservationService.deductAfterPayment(1L);

        // onHandStock: 160 - 40 = 120 (decreased AGAIN — cumulative L2-03 effect)
        assertThat(stock.getOnHandStock()).isEqualTo(120);
        // reservedStock: 40 - 40 = 0
        assertThat(stock.getReservedStock()).isEqualTo(0);
        // availableStock = 120 - 0 = 120
        assertThat(stock.getAvailableStock()).isEqualTo(120);
        // reservation status updated to DEDUCTED
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);
    }

    @Test
    @DisplayName("deductAfterPayment throws ResourceNotFoundException when stock is missing")
    void testDeductAfterPayment_throwsWhenStockNotFound() {
        StockReservation reservation = new StockReservation();
        reservation.setId(1L);
        reservation.setOrderId(1L);
        reservation.setSkuId(100L);
        reservation.setWarehouseId(1L);
        reservation.setQuantity(40);
        reservation.setStatus(ReservationStatus.RESERVED);

        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.deductAfterPayment(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- full cycle test ----

    @Test
    @DisplayName("full reserve-release-deduct cycle produces correct final state")
    void testFullReserveReleaseDeductCycle() {
        // ---- Setup: stock with 200 on-hand, 0 reserved ----
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(stockReservationRepo.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Reservations for orderId=1 (created during first reserve)
        StockReservation res1 = new StockReservation();
        res1.setId(1L);
        res1.setOrderId(1L);
        res1.setSkuId(100L);
        res1.setWarehouseId(1L);
        res1.setQuantity(50);
        res1.setStatus(ReservationStatus.RESERVED);

        // Reservations for orderId=2 (created during second reserve)
        StockReservation res2 = new StockReservation();
        res2.setId(2L);
        res2.setOrderId(2L);
        res2.setSkuId(100L);
        res2.setWarehouseId(1L);
        res2.setQuantity(50);
        res2.setStatus(ReservationStatus.RESERVED);

        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(res1));
        when(stockReservationRepo.findByOrderIdAndStatus(2L, ReservationStatus.RESERVED))
                .thenReturn(List.of(res2));

        // ---- Step 1: Reserve orderId=1, quantity=50 ----
        reservationService.reserve(1L, List.of(new ReserveItem(100L, 50)));

        // onHandStock decreased during reserve
        assertThat(stock.getOnHandStock()).isEqualTo(150); // 200 - 50
        assertThat(stock.getReservedStock()).isEqualTo(50);
        assertThat(stock.getAvailableStock()).isEqualTo(100); // 150 - 50

        // ---- Step 2: Release orderId=1 ----
        reservationService.release(1L);

        // reservedStock back to 0, but onHandStock stays at 150 (not restored — L2-03)
        assertThat(stock.getOnHandStock()).isEqualTo(150);
        assertThat(stock.getReservedStock()).isEqualTo(0);
        assertThat(stock.getAvailableStock()).isEqualTo(150); // 150 - 0
        assertThat(res1.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        // ---- Step 3: Reserve orderId=2, quantity=50 ----
        reservationService.reserve(2L, List.of(new ReserveItem(100L, 50)));

        // onHandStock decreased again: 150 - 50 = 100
        assertThat(stock.getOnHandStock()).isEqualTo(100);
        assertThat(stock.getReservedStock()).isEqualTo(50);
        assertThat(stock.getAvailableStock()).isEqualTo(50); // 100 - 50

        // ---- Step 4: Deduct after payment for orderId=2 ----
        reservationService.deductAfterPayment(2L);

        // Both decrease: onHand 50, reserved 0
        assertThat(stock.getOnHandStock()).isEqualTo(50); // 100 - 50
        assertThat(stock.getReservedStock()).isEqualTo(0); // 50 - 50
        assertThat(stock.getAvailableStock()).isEqualTo(50); // 50 - 0
        assertThat(res2.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);
    }
}
