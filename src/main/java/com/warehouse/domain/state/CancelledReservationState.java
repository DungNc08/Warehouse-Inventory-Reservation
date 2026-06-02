package com.warehouse.domain.state;

import com.warehouse.domain.model.Reservation;
import com.warehouse.exception.InvalidStateTransitionException;

public class CancelledReservationState implements ReservationState {

    @Override
    public void confirm(Reservation reservation) {
        throw new InvalidStateTransitionException(
                "INVALID_STATE_TRANSITION",
                "A CANCELLED reservation cannot be confirmed"
        );
    }

    @Override
    public void cancel(Reservation reservation) {
        throw new InvalidStateTransitionException(
                "INVALID_STATE_TRANSITION",
                "Reservation " + reservation.getId() + " is already CANCELLED"
        );
    }
}
