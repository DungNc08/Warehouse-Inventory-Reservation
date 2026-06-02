package com.warehouse.api.dto;

import com.warehouse.domain.enums.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String orderId,
        ReservationStatus status,
        Instant createdAt,
        List<ReservationItemResponse> items
) {
}
