package com.warehouse.api.controller;

import com.warehouse.api.dto.ApiResponse;
import com.warehouse.api.dto.ReservationRequest;
import com.warehouse.api.dto.ReservationResponse;
import com.warehouse.api.mapper.RequestMapper;
import com.warehouse.api.mapper.ResponseMapper;
import com.warehouse.domain.model.Reservation;
import com.warehouse.service.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final RequestMapper requestMapper;
    private final ResponseMapper responseMapper;

    public ReservationController(
            ReservationService reservationService,
            RequestMapper requestMapper,
            ResponseMapper responseMapper
    ) {
        this.reservationService = reservationService;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResponse> createReservation(@Valid @RequestBody ReservationRequest request) {
        Reservation reservation = reservationService.createReservation(
                requestMapper.toCreateReservationCommand(request)
        );
        return ApiResponse.success(responseMapper.toReservationResponse(reservation));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> getReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.getReservation(id);
        return ApiResponse.success(responseMapper.toReservationResponse(reservation));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ReservationResponse> confirmReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.confirmReservation(id);
        return ApiResponse.success(responseMapper.toReservationResponse(reservation));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ReservationResponse> cancelReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.cancelReservation(id);
        return ApiResponse.success(responseMapper.toReservationResponse(reservation));
    }
}
