package com.warehouse.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ReservationRequest(
        @NotBlank String orderId,
        @NotEmpty List<@Valid ReservationItemRequest> items
) {
}
