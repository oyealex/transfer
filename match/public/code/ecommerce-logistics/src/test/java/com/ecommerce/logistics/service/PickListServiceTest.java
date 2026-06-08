package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.logistics.entity.PickList;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.entity.ShipmentStatus;
import com.ecommerce.logistics.repository.PickListRepository;
import com.ecommerce.logistics.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PickListService}.
 */
@ExtendWith(MockitoExtension.class)
class PickListServiceTest {

    @Mock
    private PickListRepository pickListRepository;
    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private PickListService pickListService;

    // ==================== createPickList ====================

    @Test
    void testCreatePickList_success() {
        Shipment shipment = new Shipment();
        shipment.setId(1L);
        shipment.setStatus(ShipmentStatus.OUTBOUND);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(pickListRepository.save(any(PickList.class))).thenAnswer(inv -> {
            PickList pl = inv.getArgument(0);
            pl.setId(100L);
            return pl;
        });

        PickList result = pickListService.createPickList(1L, 10L,
                "[{\"skuId\":1,\"skuName\":\"Item A\",\"quantity\":2}]", 50L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getShipmentId());
        assertEquals(10L, result.getWarehouseId());
        assertEquals(50L, result.getPickerId());
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getPickListNo());
        assertEquals("[{\"skuId\":1,\"skuName\":\"Item A\",\"quantity\":2}]", result.getItems());

        // Verify shipment was updated with pickListId
        verify(shipmentRepository).save(shipment);
        assertEquals(100L, shipment.getPickListId());
    }

    @Test
    void testCreatePickList_shipmentNotFound_throwsException() {
        when(shipmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pickListService.createPickList(999L, 10L,
                        "[]", 50L));
    }

    @Test
    void testCreatePickList_pickListAlreadyExists_returnsExisting() {
        Shipment shipment = new Shipment();
        shipment.setId(1L);
        shipment.setPickListId(5L);
        PickList existing = new PickList();
        existing.setId(5L);
        existing.setPickListNo("PL12345");
        existing.setStatus("PENDING");

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(pickListRepository.findById(5L)).thenReturn(Optional.of(existing));

        PickList result = pickListService.createPickList(1L, 10L,
                "[{\"skuId\":2}]", 50L);

        assertEquals(5L, result.getId());
        assertEquals("PL12345", result.getPickListNo());
    }

    // ==================== completePicking ====================

    @Test
    void testCompletePicking_fromPending_success() {
        PickList pickList = new PickList();
        pickList.setId(100L);
        pickList.setStatus("PENDING");
        when(pickListRepository.findById(100L)).thenReturn(Optional.of(pickList));
        when(pickListRepository.save(any(PickList.class))).thenAnswer(inv -> inv.getArgument(0));

        PickList result = pickListService.completePicking(100L, 50L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(50L, result.getPickerId());
    }

    @Test
    void testCompletePicking_fromPicking_success() {
        PickList pickList = new PickList();
        pickList.setId(101L);
        pickList.setStatus("PICKING");
        when(pickListRepository.findById(101L)).thenReturn(Optional.of(pickList));
        when(pickListRepository.save(any(PickList.class))).thenAnswer(inv -> inv.getArgument(0));

        PickList result = pickListService.completePicking(101L, 60L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(60L, result.getPickerId());
    }

    @Test
    void testCompletePicking_alreadyCompleted_throwsException() {
        PickList pickList = new PickList();
        pickList.setId(102L);
        pickList.setStatus("COMPLETED");
        when(pickListRepository.findById(102L)).thenReturn(Optional.of(pickList));

        assertThrows(IllegalStateException.class,
                () -> pickListService.completePicking(102L, 50L));
    }

    @Test
    void testCompletePicking_notFound_throwsException() {
        when(pickListRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pickListService.completePicking(999L, 50L));
    }

    // ==================== getPickList ====================

    @Test
    void testGetPickList_success() {
        PickList pickList = new PickList();
        pickList.setId(100L);
        pickList.setPickListNo("PL12345");
        when(pickListRepository.findById(100L)).thenReturn(Optional.of(pickList));

        PickList result = pickListService.getPickList(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("PL12345", result.getPickListNo());
    }

    @Test
    void testGetPickList_notFound_throwsException() {
        when(pickListRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pickListService.getPickList(999L));
    }

    // ==================== getPickListByShipmentId ====================

    @Test
    void testGetPickListByShipmentId_success() {
        PickList pickList = new PickList();
        pickList.setId(200L);
        pickList.setShipmentId(10L);
        when(pickListRepository.findByShipmentId(10L)).thenReturn(Optional.of(pickList));

        PickList result = pickListService.getPickListByShipmentId(10L);

        assertNotNull(result);
        assertEquals(200L, result.getId());
        assertEquals(10L, result.getShipmentId());
    }

    @Test
    void testGetPickListByShipmentId_notFound_throwsException() {
        when(pickListRepository.findByShipmentId(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pickListService.getPickListByShipmentId(999L));
    }
}
