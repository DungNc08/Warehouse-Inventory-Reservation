package com.warehouse.api.mapper;

import com.warehouse.api.dto.InventoryResponse;
import com.warehouse.api.dto.ReservationItemResponse;
import com.warehouse.api.dto.ReservationResponse;
import com.warehouse.domain.model.Inventory;
import com.warehouse.domain.model.Reservation;
import com.warehouse.domain.model.ReservationItem;

public interface ResponseMapper {

    ReservationResponse toReservationResponse(Reservation reservation);

    ReservationItemResponse toReservationItemResponse(ReservationItem item);

    InventoryResponse toInventoryResponse(Inventory inventory);
}
