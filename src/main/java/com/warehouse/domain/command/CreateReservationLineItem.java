package com.warehouse.domain.command;

public record CreateReservationLineItem(
        String sku,
        int quantity
) {
}
