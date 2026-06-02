package com.warehouse.api.mapper;

import com.warehouse.api.dto.InventoryResponse;
import com.warehouse.api.dto.ReservationItemResponse;
import com.warehouse.api.dto.ReservationResponse;
import com.warehouse.domain.model.Inventory;
import com.warehouse.domain.model.Reservation;
import com.warehouse.domain.model.ReservationItem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapperImpl implements ResponseMapper {

    @Override
    public ReservationResponse toReservationResponse(Reservation reservation) {
        List<ReservationItemResponse> items = reservation.getItems().stream()
                .map(this::toReservationItemResponse)
                .toList();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                items
        );
    }

    @Override
    public ReservationItemResponse toReservationItemResponse(ReservationItem item) {
        return new ReservationItemResponse(item.getSku(), item.getQuantity());
    }

    @Override
    public InventoryResponse toInventoryResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getSku(),
                inventory.getTotalStock(),
                inventory.getAvailableStock(),
                inventory.getReservedStock()
        );
    }
}
