package com.warehouse.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end API workflow tests (GET / confirm / cancel / inventory).
 */
class ReservationWorkflowIT extends WarehouseIntegrationTestBase {

    @Test
    void getInventory_returnsSeedStock() throws Exception {
        JsonNode inventory = getInventory("A100");

        assertThat(inventory.get("sku").asText()).isEqualTo("A100");
        assertThat(inventory.get("totalStock").asInt()).isEqualTo(100);
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(100);
        assertThat(inventory.get("reservedStock").asInt()).isZero();
    }

    @Test
    void createReservation_thenGet_returnsPendingReservation() throws Exception {
        JsonNode created = createReservation("ORD-WF-GET", "A100", 25);
        UUID id = reservationId(created);

        ResponseEntity<String> getResponse = getReservation(id);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(getResponse.getBody()).get("data");
        assertThat(data.get("orderId").asText()).isEqualTo("ORD-WF-GET");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
        assertThat(data.get("items")).hasSize(1);
        assertThat(data.get("items").get(0).get("sku").asText()).isEqualTo("A100");
        assertThat(data.get("items").get(0).get("quantity").asInt()).isEqualTo(25);
    }

    @Test
    void createReservation_thenConfirm_keepsStockReserved() throws Exception {
        JsonNode created = createReservation("ORD-WF-CONFIRM", "B200", 10);
        UUID id = reservationId(created);

        ResponseEntity<String> confirmResponse = confirmReservation(id);

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(confirmResponse.getBody()).get("data");
        assertThat(data.get("status").asText()).isEqualTo("CONFIRMED");

        JsonNode inventory = getInventory("B200");
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(40);
        assertThat(inventory.get("reservedStock").asInt()).isEqualTo(10);
    }

    @Test
    void createReservation_thenCancel_releasesStock() throws Exception {
        JsonNode created = createReservation("ORD-WF-CANCEL", "B200", 15);
        UUID id = reservationId(created);

        ResponseEntity<String> cancelResponse = cancelReservation(id);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(cancelResponse.getBody()).get("data");
        assertThat(data.get("status").asText()).isEqualTo("CANCELLED");

        JsonNode inventory = getInventory("B200");
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(50);
        assertThat(inventory.get("reservedStock").asInt()).isZero();
    }

    @Test
    void confirm_thenCancel_returnsInvalidStateTransition() throws Exception {
        JsonNode created = createReservation("ORD-WF-BAD-CANCEL", "A100", 5);
        UUID id = reservationId(created);

        assertThat(confirmReservation(id).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> cancelResponse = cancelReservation(id);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(cancelResponse)).isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void getReservation_unknownId_returnsNotFound() {
        UUID unknownId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        ResponseEntity<String> response = getReservation(unknownId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
