package com.warehouse.api.mapper;

import com.warehouse.api.dto.ReservationRequest;
import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.command.CreateReservationLineItem;
import org.springframework.stereotype.Component;

@Component
public class RequestMapperImpl implements RequestMapper {

    @Override
    public CreateReservationCommand toCreateReservationCommand(ReservationRequest request) {
        return new CreateReservationCommand(
                request.orderId(),
                request.items().stream()
                        .map(item -> new CreateReservationLineItem(item.sku(), item.quantity()))
                        .toList()
        );
    }
}
