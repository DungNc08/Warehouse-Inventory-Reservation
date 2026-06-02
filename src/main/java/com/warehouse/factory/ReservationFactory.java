package com.warehouse.factory;

import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.command.CreateReservationLineItem;
import com.warehouse.domain.model.Reservation;
import com.warehouse.domain.model.ReservationItem;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationFactory {

    public Reservation create(CreateReservationCommand command) {
        Reservation reservation = new Reservation(UUID.randomUUID(), command.orderId());
        for (CreateReservationLineItem lineItem : command.items()) {
            reservation.addItem(new ReservationItem(lineItem.sku(), lineItem.quantity()));
        }
        return reservation;
    }
}
