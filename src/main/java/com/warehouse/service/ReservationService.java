package com.warehouse.service;

import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.model.Reservation;
import java.util.UUID;

public interface ReservationService {

    Reservation createReservation(CreateReservationCommand command);

    Reservation getReservation(UUID id);

    Reservation confirmReservation(UUID id);

    Reservation cancelReservation(UUID id);
}
