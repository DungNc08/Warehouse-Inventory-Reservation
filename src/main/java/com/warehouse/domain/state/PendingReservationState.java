package com.warehouse.domain.state;

import com.warehouse.domain.enums.ReservationStatus;
import com.warehouse.domain.model.Reservation;

public class PendingReservationState implements ReservationState {

    @Override
    public void confirm(Reservation reservation) {
        reservation.applyStatus(ReservationStatus.CONFIRMED);
    }

    @Override
    public void cancel(Reservation reservation) {
        reservation.applyStatus(ReservationStatus.CANCELLED);
    }
}
