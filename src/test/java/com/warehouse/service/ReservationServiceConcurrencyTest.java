package com.warehouse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.command.CreateReservationLineItem;
import com.warehouse.domain.model.Inventory;
import com.warehouse.domain.model.Reservation;
import com.warehouse.domain.model.ReservationItem;
import com.warehouse.factory.ReservationFactory;
import com.warehouse.repository.InventoryRepository;
import com.warehouse.repository.ReservationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Documents concurrency strategy in the service layer (assignment requirement):
 * pessimistic row locks are acquired in sorted SKU order before stock mutation.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceConcurrencyTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReservationFactory reservationFactory;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void createReservation_acquiresPessimisticLocksInSortedSkuOrder() {
        Inventory inventoryB = mock(Inventory.class);
        when(inventoryB.getSku()).thenReturn("B200");
        when(inventoryB.hasAvailable(3)).thenReturn(true);

        Inventory inventoryA = mock(Inventory.class);
        when(inventoryA.getSku()).thenReturn("A100");
        when(inventoryA.hasAvailable(5)).thenReturn(true);

        CreateReservationCommand command = new CreateReservationCommand(
                "ORD-LOCK-ORDER",
                List.of(
                        new CreateReservationLineItem("B200", 3),
                        new CreateReservationLineItem("A100", 5)
                )
        );
        Reservation reservation = new Reservation(UUID.randomUUID(), "ORD-LOCK-ORDER");
        reservation.addItem(new ReservationItem("B200", 3));
        reservation.addItem(new ReservationItem("A100", 5));

        when(reservationRepository.existsByOrderId("ORD-LOCK-ORDER")).thenReturn(false);
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100", "B200")))
                .thenReturn(List.of(inventoryA, inventoryB));
        when(reservationFactory.create(command)).thenReturn(reservation);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.createReservation(command);

        verify(inventoryRepository).findAllBySkuInForUpdate(List.of("A100", "B200"));
        verify(inventoryA).reserve(5);
        verify(inventoryB).reserve(3);
    }

    @Test
    void createReservation_doesNotMutateStockBeforePessimisticLock() {
        CreateReservationCommand command = new CreateReservationCommand(
                "ORD-LOCK-FIRST",
                List.of(new CreateReservationLineItem("A100", 10))
        );

        when(reservationRepository.existsByOrderId("ORD-LOCK-FIRST")).thenReturn(false);
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100"))).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(com.warehouse.exception.ResourceNotFoundException.class);

        verify(reservationFactory, never()).create(command);
    }
}
