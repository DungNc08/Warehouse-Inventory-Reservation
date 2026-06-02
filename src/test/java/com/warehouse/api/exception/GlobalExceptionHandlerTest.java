package com.warehouse.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.api.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsDuplicateOrderIdConstraintTo409() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint \"reservations_order_id_key\" "
                                + "Detail: Key (order_id)=(ORD-RACE) already exists."
                )
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("DUPLICATE_ORDER");
        assertThat(response.getBody().error().message())
                .isEqualTo("A reservation already exists for order ORD-RACE");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void mapsOtherDataIntegrityViolationsToGenericConflict() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"products_pkey\"")
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
    }
}
