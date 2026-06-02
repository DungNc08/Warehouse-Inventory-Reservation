package com.warehouse.api.dto;

public record ReservationItemResponse(
        String sku,
        int quantity
) {
}
