package com.warehouse.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReservationItemRequest(
        @NotBlank String sku,
        @Positive int quantity
) {
}
