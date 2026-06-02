package com.warehouse.api.dto;

public record InventoryResponse(
        String sku,
        int totalStock,
        int availableStock,
        int reservedStock
) {
}
