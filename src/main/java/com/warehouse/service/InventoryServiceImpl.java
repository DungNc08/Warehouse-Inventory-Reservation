package com.warehouse.service;

import com.warehouse.domain.model.Inventory;
import com.warehouse.exception.ResourceNotFoundException;
import com.warehouse.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Inventory getInventory(String sku) {
        return inventoryRepository.findById(sku)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INVENTORY_NOT_FOUND",
                        "Inventory for SKU " + sku + " was not found"
                ));
    }
}
