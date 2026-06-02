package com.warehouse.domain.state;

import com.warehouse.domain.model.Reservation;

public interface ReservationState {

    void confirm(Reservation reservation);

    void cancel(Reservation reservation);
}
