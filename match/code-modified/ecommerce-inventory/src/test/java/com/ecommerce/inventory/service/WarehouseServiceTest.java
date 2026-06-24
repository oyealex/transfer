package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.WarehouseCreateRequest;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WarehouseService")
@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    @DisplayName("create saves warehouse with correct fields and ACTIVE status")
    void testCreate_createsWarehouse() {
        WarehouseCreateRequest request = new WarehouseCreateRequest();
        request.setName("Main Warehouse");
        request.setProvince("Guangdong");
        request.setCity("Shenzhen");
        request.setDistrict("Nanshan");
        request.setDetail("No. 123 Tech Road");
        request.setServiceRegions("South China");
        request.setPriority(1);

        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            w.setId(1L);
            return w;
        });

        Warehouse result = warehouseService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Main Warehouse");
        assertThat(result.getProvince()).isEqualTo("Guangdong");
        assertThat(result.getCity()).isEqualTo("Shenzhen");
        assertThat(result.getDistrict()).isEqualTo("Nanshan");
        assertThat(result.getDetail()).isEqualTo("No. 123 Tech Road");
        assertThat(result.getServiceRegions()).isEqualTo("South China");
        assertThat(result.getPriority()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("list returns all warehouses from repository")
    void testList_returnsAllWarehouses() {
        Warehouse w1 = new Warehouse();
        w1.setId(1L);
        w1.setName("Warehouse A");
        w1.setStatus("ACTIVE");

        Warehouse w2 = new Warehouse();
        w2.setId(2L);
        w2.setName("Warehouse B");
        w2.setStatus("ACTIVE");

        when(warehouseRepository.findAll()).thenReturn(List.of(w1, w2));

        List<Warehouse> result = warehouseService.list();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Warehouse::getName)
                .containsExactly("Warehouse A", "Warehouse B");
        verify(warehouseRepository).findAll();
    }

    @Test
    @DisplayName("list returns empty list when no warehouses exist")
    void testList_returnsEmptyWhenNoWarehouses() {
        when(warehouseRepository.findAll()).thenReturn(List.of());

        List<Warehouse> result = warehouseService.list();

        assertThat(result).isEmpty();
    }
}
