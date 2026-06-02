package com.warehouse.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.warehouse.api.dto.ReservationItemRequest;
import com.warehouse.api.dto.ReservationRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Concurrency integration tests against real PostgreSQL (Testcontainers).
 */
class ConcurrentReservationIT extends WarehouseIntegrationTestBase {

    @Test
    void concurrentReservationsForSameSku_allowOnlyOneWhenStockIsInsufficient() throws Exception {
        ReservationRequest requestOne = new ReservationRequest(
                "ORD-CONCURRENT-1",
                List.of(new ReservationItemRequest("A100", 60))
        );
        ReservationRequest requestTwo = new ReservationRequest(
                "ORD-CONCURRENT-2",
                List.of(new ReservationItemRequest("A100", 60))
        );

        List<ResponseEntity<String>> responses = runConcurrentRequests(List.of(
                () -> restTemplate.exchange(
                        reservationsUrl(),
                        HttpMethod.POST,
                        new HttpEntity<>(requestOne),
                        String.class
                ),
                () -> restTemplate.exchange(
                        reservationsUrl(),
                        HttpMethod.POST,
                        new HttpEntity<>(requestTwo),
                        String.class
                )
        ));

        long created = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long rejected = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();

        assertThat(created).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);

        ResponseEntity<String> rejectedResponse = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .findFirst()
                .orElseThrow();
        JsonNode rejectedBody = objectMapper.readTree(rejectedResponse.getBody());
        assertThat(rejectedBody.get("error").get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");

        JsonNode inventory = getInventory("A100");
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(40);
        assertThat(inventory.get("reservedStock").asInt()).isEqualTo(60);
        assertThat(inventory.get("totalStock").asInt()).isEqualTo(100);
    }

    @Test
    void concurrentReservations_manyThreads_neverOversellStock() throws Exception {
        int threadCount = 10;
        int quantityPerRequest = 15;

        List<ResponseEntity<String>> responses = runConcurrentRequests(threadCount, () ->
                restTemplate.exchange(
                        reservationsUrl(),
                        HttpMethod.POST,
                        new HttpEntity<>(new ReservationRequest(
                                "ORD-STRESS-" + UUID.randomUUID(),
                                List.of(new ReservationItemRequest("A100", quantityPerRequest))
                        )),
                        String.class
                )
        );

        long created = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long rejected = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();

        assertThat(created + rejected).isEqualTo(threadCount);
        assertThat(created).isBetween(1L, 6L);
        assertThat(rejected).isEqualTo(threadCount - created);

        JsonNode inventory = getInventory("A100");
        int reserved = inventory.get("reservedStock").asInt();
        int available = inventory.get("availableStock").asInt();
        assertThat(reserved + available).isEqualTo(100);
        assertThat(reserved).isEqualTo(created * quantityPerRequest);
        assertThat(reserved).isLessThanOrEqualTo(100);
    }

    @Test
    void concurrentReservations_sameOrderId_oneSucceedsOneDuplicateOrder() throws Exception {
        ReservationRequest request = new ReservationRequest(
                "ORD-RACE",
                List.of(new ReservationItemRequest("A100", 10))
        );

        List<ResponseEntity<String>> responses = runConcurrentRequests(2, () ->
                restTemplate.exchange(
                        reservationsUrl(),
                        HttpMethod.POST,
                        new HttpEntity<>(request),
                        String.class
                )
        );

        long created = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long rejected = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();

        assertThat(created).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);

        ResponseEntity<String> duplicateResponse = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .findFirst()
                .orElseThrow();
        assertThat(errorCode(duplicateResponse)).isEqualTo("DUPLICATE_ORDER");

        JsonNode inventory = getInventory("A100");
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(90);
        assertThat(inventory.get("reservedStock").asInt()).isEqualTo(10);
    }

    @Test
    void concurrentCancel_sameReservation_releasesStockOnlyOnce() throws Exception {
        JsonNode reservation = createReservation("ORD-DBL-CANCEL", "B200", 10);
        UUID reservationId = reservationId(reservation);

        List<ResponseEntity<String>> responses = runConcurrentRequests(
                2,
                () -> cancelReservation(reservationId)
        );

        long ok = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long conflict = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(1);
        assertThat(errorCode(responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .findFirst()
                .orElseThrow())).isEqualTo("INVALID_STATE_TRANSITION");

        JsonNode inventory = getInventory("B200");
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(50);
        assertThat(inventory.get("reservedStock").asInt()).isZero();
    }

    @Test
    void concurrentConfirm_sameReservation_allowsOnlyOneTransition() throws Exception {
        JsonNode reservation = createReservation("ORD-DBL-CONFIRM", "B200", 5);
        UUID reservationId = reservationId(reservation);

        List<ResponseEntity<String>> responses = runConcurrentRequests(
                2,
                () -> confirmReservation(reservationId)
        );

        long ok = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long conflict = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(1);

        ResponseEntity<String> getResponse = getReservation(reservationId);
        JsonNode data = objectMapper.readTree(getResponse.getBody()).get("data");
        assertThat(data.get("status").asText()).isEqualTo("CONFIRMED");

        JsonNode inventory = getInventory("B200");
        assertThat(inventory.get("availableStock").asInt()).isEqualTo(45);
        assertThat(inventory.get("reservedStock").asInt()).isEqualTo(5);
    }

}
