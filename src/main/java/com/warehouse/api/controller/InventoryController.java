package com.warehouse.api.controller;

import com.warehouse.api.dto.ApiResponse;
import com.warehouse.api.dto.InventoryResponse;
import com.warehouse.api.mapper.ResponseMapper;
import com.warehouse.domain.model.Inventory;
import com.warehouse.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ResponseMapper responseMapper;

    public InventoryController(InventoryService inventoryService, ResponseMapper responseMapper) {
        this.inventoryService = inventoryService;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/{sku}")
    public ApiResponse<InventoryResponse> getInventory(@PathVariable String sku) {
        Inventory inventory = inventoryService.getInventory(sku);
        return ApiResponse.success(responseMapper.toInventoryResponse(inventory));
    }
}
