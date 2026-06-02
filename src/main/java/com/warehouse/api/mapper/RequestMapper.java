package com.warehouse.api.mapper;

import com.warehouse.api.dto.ReservationRequest;
import com.warehouse.domain.command.CreateReservationCommand;

public interface RequestMapper {

    CreateReservationCommand toCreateReservationCommand(ReservationRequest request);
}
