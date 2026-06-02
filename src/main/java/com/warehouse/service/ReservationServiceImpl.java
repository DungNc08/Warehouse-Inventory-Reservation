package com.warehouse.service;

import com.warehouse.domain.command.CreateReservationCommand;
import com.warehouse.domain.command.CreateReservationLineItem;
import com.warehouse.domain.model.Inventory;
import com.warehouse.domain.model.Reservation;
import com.warehouse.domain.model.ReservationItem;
import com.warehouse.exception.DuplicateOrderException;
import com.warehouse.exception.InsufficientStockException;
import com.warehouse.exception.ResourceNotFoundException;
import com.warehouse.factory.ReservationFactory;
import com.warehouse.repository.InventoryRepository;
import com.warehouse.repository.ReservationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ReservationFactory reservationFactory;

    public ReservationServiceImpl(
            ReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            ReservationFactory reservationFactory
    ) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationFactory = reservationFactory;
    }

    @Override
    @Transactional
    public Reservation createReservation(CreateReservationCommand command) {
        if (reservationRepository.existsByOrderId(command.orderId())) {
            throw new DuplicateOrderException(command.orderId());
        }

        List<String> skus = command.items().stream()
                .map(CreateReservationLineItem::sku)
                .sorted()
                .distinct()
                .toList();

        Map<String, Inventory> inventoryBySku = inventoryRepository.findAllBySkuInForUpdate(skus).stream()
                .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        validateInventoryExists(command, inventoryBySku);
        validateStockAvailability(command, inventoryBySku);

        Reservation reservation = reservationFactory.create(command);
        for (ReservationItem item : reservation.getItems()) {
            Inventory inventory = inventoryBySku.get(item.getSku());
            inventory.reserve(item.getQuantity());
        }

        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public Reservation getReservation(UUID id) {
        return reservationRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation " + id + " was not found"
                ));
    }

    @Override
    @Transactional
    public Reservation confirmReservation(UUID id) {
        Reservation reservation = getReservationForUpdate(id);
        reservation.confirm();
        return reservation;
    }

    @Override
    @Transactional
    public Reservation cancelReservation(UUID id) {
        Reservation reservation = getReservationForUpdate(id);
        reservation.cancel();

        List<String> skus = reservation.getItems().stream()
                .map(ReservationItem::getSku)
                .sorted()
                .distinct()
                .toList();

        Map<String, Inventory> inventoryBySku = inventoryRepository.findAllBySkuInForUpdate(skus).stream()
                .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        for (ReservationItem item : reservation.getItems()) {
            Inventory inventory = inventoryBySku.get(item.getSku());
            if (inventory == null) {
                throw new ResourceNotFoundException(
                        "INVENTORY_NOT_FOUND",
                        "Inventory for SKU " + item.getSku() + " was not found"
                );
            }
            inventory.release(item.getQuantity());
        }

        return reservation;
    }

    private Reservation getReservationForUpdate(UUID id) {
        return reservationRepository.findWithItemsByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation " + id + " was not found"
                ));
    }

    private void validateInventoryExists(CreateReservationCommand command, Map<String, Inventory> inventoryBySku) {
        for (CreateReservationLineItem item : command.items()) {
            if (!inventoryBySku.containsKey(item.sku())) {
                throw new ResourceNotFoundException(
                        "INVENTORY_NOT_FOUND",
                        "Inventory for SKU " + item.sku() + " was not found"
                );
            }
        }
    }

    private void validateStockAvailability(CreateReservationCommand command, Map<String, Inventory> inventoryBySku) {
        Map<String, Integer> requestedBySku = command.items().stream()
                .collect(Collectors.groupingBy(
                        CreateReservationLineItem::sku,
                        Collectors.summingInt(CreateReservationLineItem::quantity)
                ));

        requestedBySku.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Inventory inventory = inventoryBySku.get(entry.getKey());
                    int requested = entry.getValue();
                    if (!inventory.hasAvailable(requested)) {
                        throw new InsufficientStockException(
                                entry.getKey(),
                                inventory.getAvailableStock(),
                                requested
                        );
                    }
                });
    }
}
