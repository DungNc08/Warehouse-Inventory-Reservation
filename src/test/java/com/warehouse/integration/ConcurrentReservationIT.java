package com.warehouse.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.api.dto.ReservationItemRequest;
import com.warehouse.api.dto.ReservationRequest;
import com.warehouse.support.DatabaseMigrationRunner;
import com.warehouse.support.TestcontainersEnvironment;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Assignment integration test: Testcontainers + PostgreSQL.
 * Two concurrent POST /reservations for SKU A100 (60+60 vs 100 stock)
 * — exactly one succeeds (201) and one is rejected (409 INSUFFICIENT_STOCK).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentReservationIT {

    static {
        TestcontainersEnvironment.init();
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("warehouse")
            .withUsername("warehouse")
            .withPassword("warehouse");

    @DynamicPropertySource
    static void disableLiquibaseInApp(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeAll
    void migrateSchema() {
        DatabaseMigrationRunner.migrate(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentReservationsForSameSku_allowOnlyOneWhenStockIsInsufficient() throws Exception {
        String reserveUrl = "http://localhost:" + port + "/api/v1/reservations";
        ReservationRequest requestOne = new ReservationRequest(
                "ORD-CONCURRENT-1",
                List.of(new ReservationItemRequest("A100", 60))
        );
        ReservationRequest requestTwo = new ReservationRequest(
                "ORD-CONCURRENT-2",
                List.of(new ReservationItemRequest("A100", 60))
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<ResponseEntity<String>> first = executor.submit(() -> {
            startLatch.await();
            return restTemplate.exchange(
                    reserveUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestOne),
                    String.class
            );
        });

        Future<ResponseEntity<String>> second = executor.submit(() -> {
            startLatch.await();
            return restTemplate.exchange(
                    reserveUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestTwo),
                    String.class
            );
        });

        startLatch.countDown();

        ResponseEntity<String> firstResponse = first.get(30, TimeUnit.SECONDS);
        ResponseEntity<String> secondResponse = second.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        ResponseEntity<String> successResponse;
        ResponseEntity<String> rejectedResponse;
        if (firstResponse.getStatusCode() == HttpStatus.CREATED) {
            successResponse = firstResponse;
            rejectedResponse = secondResponse;
        } else {
            successResponse = secondResponse;
            rejectedResponse = firstResponse;
        }

        assertThat(successResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rejectedResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode successBody = objectMapper.readTree(successResponse.getBody());
        assertThat(successBody.get("error").isNull()).isTrue();
        assertThat(successBody.get("data").get("status").asText()).isEqualTo("PENDING");

        JsonNode rejectedBody = objectMapper.readTree(rejectedResponse.getBody());
        assertThat(rejectedBody.get("data").isNull()).isTrue();
        assertThat(rejectedBody.get("error").get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");

        ResponseEntity<String> inventoryResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/inventory/A100",
                String.class
        );
        JsonNode inventory = objectMapper.readTree(inventoryResponse.getBody());
        assertThat(inventory.get("data").get("availableStock").asInt()).isEqualTo(40);
        assertThat(inventory.get("data").get("reservedStock").asInt()).isEqualTo(60);
        assertThat(inventory.get("data").get("totalStock").asInt()).isEqualTo(100);
    }
}
