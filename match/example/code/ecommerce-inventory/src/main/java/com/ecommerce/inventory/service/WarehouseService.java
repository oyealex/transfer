package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.WarehouseCreateRequest;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public Warehouse create(WarehouseCreateRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setName(request.getName());
        warehouse.setProvince(request.getProvince());
        warehouse.setCity(request.getCity());
        warehouse.setDistrict(request.getDistrict());
        warehouse.setDetail(request.getDetail());
        warehouse.setServiceRegions(request.getServiceRegions());
        warehouse.setPriority(request.getPriority());
        warehouse.setStatus("ACTIVE");
        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Warehouse created: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Warehouse> list() {
        return warehouseRepository.findAll();
    }
}
