package com.warehouse.service;

import com.warehouse.domain.model.Inventory;

public interface InventoryService {

    Inventory getInventory(String sku);
}
