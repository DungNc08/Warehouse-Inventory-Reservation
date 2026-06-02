package com.warehouse.factory;

import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.model.Reservation;

public interface ReservationFactory {

    Reservation create(CreateReservationCommand command);
}
