package com.warehouse.domain.command;

import java.util.List;

public record CreateReservationCommand(
        String orderId,
        List<CreateReservationLineItem> items
) {
}
