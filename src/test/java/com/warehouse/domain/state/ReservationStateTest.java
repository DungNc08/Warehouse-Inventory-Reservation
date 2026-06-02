package com.warehouse.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.warehouse.domain.enums.ReservationStatus;
import com.warehouse.domain.model.Reservation;
import com.warehouse.exception.InvalidStateTransitionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationStateTest {

    @Test
    void pendingReservationCanBeConfirmedOrCancelled() {
        Reservation reservation = new Reservation(UUID.randomUUID(), "ORD-STATE-1");

        reservation.confirm();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        reservation = new Reservation(UUID.randomUUID(), "ORD-STATE-2");
        reservation.cancel();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void confirmedReservationCannotBeCancelled() {
        Reservation reservation = new Reservation(UUID.randomUUID(), "ORD-STATE-3");
        reservation.confirm();

        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CONFIRMED reservation cannot be cancelled");
    }

    @Test
    void cancelledReservationCannotBeConfirmed() {
        Reservation reservation = new Reservation(UUID.randomUUID(), "ORD-STATE-4");
        reservation.cancel();

        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CANCELLED reservation cannot be confirmed");
    }
}
