package com.warehouse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.command.CreateReservationLineItem;
import com.warehouse.domain.enums.ReservationStatus;
import com.warehouse.domain.model.Inventory;
import com.warehouse.domain.model.Reservation;
import com.warehouse.domain.model.ReservationItem;
import com.warehouse.exception.DuplicateOrderException;
import com.warehouse.exception.InsufficientStockException;
import com.warehouse.exception.InvalidStateTransitionException;
import com.warehouse.factory.ReservationFactory;
import com.warehouse.repository.InventoryRepository;
import com.warehouse.repository.ReservationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReservationFactory reservationFactory;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Test
    void createReservation_rejectsWhenStockIsInsufficient() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSku()).thenReturn("A100");
        when(inventory.getAvailableStock()).thenReturn(30);
        when(inventory.hasAvailable(50)).thenReturn(false);

        CreateReservationCommand command = new CreateReservationCommand(
                "ORD-1001",
                List.of(new CreateReservationLineItem("A100", 50))
        );

        when(reservationRepository.existsByOrderId("ORD-1001")).thenReturn(false);
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventory));

        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("SKU A100 has only 30 units available, 50 were requested");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_rejectsDuplicateOrderId() {
        CreateReservationCommand command = new CreateReservationCommand(
                "ORD-DUP",
                List.of(new CreateReservationLineItem("A100", 5))
        );

        when(reservationRepository.existsByOrderId("ORD-DUP")).thenReturn(true);

        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("A reservation already exists for order ORD-DUP");

        verify(inventoryRepository, never()).findAllBySkuInForUpdate(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_rejectsEntireRequestWhenOneSkuHasInsufficientStock() {
        Inventory inventoryA100 = mock(Inventory.class);
        when(inventoryA100.getSku()).thenReturn("A100");
        when(inventoryA100.hasAvailable(5)).thenReturn(true);

        Inventory inventoryB200 = mock(Inventory.class);
        when(inventoryB200.getSku()).thenReturn("B200");
        when(inventoryB200.getAvailableStock()).thenReturn(2);
        when(inventoryB200.hasAvailable(3)).thenReturn(false);

        CreateReservationCommand command = new CreateReservationCommand(
                "ORD-MULTI",
                List.of(
                        new CreateReservationLineItem("A100", 5),
                        new CreateReservationLineItem("B200", 3)
                )
        );

        when(reservationRepository.existsByOrderId("ORD-MULTI")).thenReturn(false);
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100", "B200")))
                .thenReturn(List.of(inventoryA100, inventoryB200));

        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("SKU B200 has only 2 units available, 3 were requested");

        verify(reservationRepository, never()).save(any());
        verify(inventoryA100, never()).reserve(anyInt());
    }

    @Test
    void createReservation_succeedsWhenStockIsAvailable() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSku()).thenReturn("A100");
        when(inventory.hasAvailable(5)).thenReturn(true);

        CreateReservationCommand command = new CreateReservationCommand(
                "ORD-1002",
                List.of(new CreateReservationLineItem("A100", 5))
        );
        Reservation reservation = new Reservation(UUID.randomUUID(), "ORD-1002");
        reservation.addItem(new ReservationItem("A100", 5));

        when(reservationRepository.existsByOrderId("ORD-1002")).thenReturn(false);
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventory));
        when(reservationFactory.create(command)).thenReturn(reservation);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        Reservation result = reservationService.createReservation(command);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(inventory).reserve(5);
    }

    @Test
    void confirmReservation_movesPendingToConfirmed() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = pendingReservation(reservationId, "ORD-2001");

        when(reservationRepository.findWithItemsByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        Reservation result = reservationService.confirmReservation(reservationId);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void confirmReservation_rejectsWhenAlreadyConfirmed() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = pendingReservation(reservationId, "ORD-2002");
        reservation.confirm();

        when(reservationRepository.findWithItemsByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirmReservation(reservationId))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("already CONFIRMED");
    }

    @Test
    void cancelReservation_movesPendingToCancelledAndReleasesStock() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = pendingReservation(reservationId, "ORD-3001");
        reservation.addItem(new ReservationItem("A100", 10));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSku()).thenReturn("A100");

        when(reservationRepository.findWithItemsByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findAllBySkuInForUpdate(List.of("A100"))).thenReturn(List.of(inventory));

        Reservation result = reservationService.cancelReservation(reservationId);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(inventory).release(10);
    }

    @Test
    void cancelReservation_rejectsWhenAlreadyConfirmed() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = pendingReservation(reservationId, "ORD-3002");
        reservation.confirm();

        when(reservationRepository.findWithItemsByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CONFIRMED reservation cannot be cancelled");
    }

    private Reservation pendingReservation(UUID id, String orderId) {
        return new Reservation(id, orderId);
    }
}
