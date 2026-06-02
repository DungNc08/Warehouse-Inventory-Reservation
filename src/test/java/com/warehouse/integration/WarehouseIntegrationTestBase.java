package com.warehouse.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.api.dto.ReservationItemRequest;
import com.warehouse.api.dto.ReservationRequest;
import com.warehouse.support.DatabaseMigrationRunner;
import com.warehouse.support.TestcontainersEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WarehouseIntegrationTestBase {

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

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void migrateSchema() {
        DatabaseMigrationRunner.migrate(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    @BeforeEach
    void resetWarehouseState() {
        jdbcTemplate.execute("DELETE FROM reservation_items");
        jdbcTemplate.execute("DELETE FROM reservations");
        jdbcTemplate.update("""
                UPDATE inventory
                SET available_stock = total_stock,
                    reserved_stock = 0,
                    version = 0
                WHERE sku IN ('A100', 'B200')
                """);
    }

    protected String reservationsUrl() {
        return "http://localhost:" + port + "/api/v1/reservations";
    }

    protected String reservationUrl(UUID id) {
        return reservationsUrl() + "/" + id;
    }

    protected String inventoryUrl(String sku) {
        return "http://localhost:" + port + "/api/v1/inventory/" + sku;
    }

    protected JsonNode createReservation(String orderId, String sku, int quantity) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                reservationsUrl(),
                HttpMethod.POST,
                new HttpEntity<>(new ReservationRequest(
                        orderId,
                        List.of(new ReservationItemRequest(sku, quantity))
                )),
                String.class
        );
        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new AssertionError("Expected 201 but got " + response.getStatusCode() + ": " + response.getBody());
        }
        return objectMapper.readTree(response.getBody()).get("data");
    }

    protected UUID reservationId(JsonNode reservation) {
        return UUID.fromString(reservation.get("id").asText());
    }

    protected ResponseEntity<String> getReservation(UUID id) {
        return restTemplate.getForEntity(reservationUrl(id), String.class);
    }

    protected ResponseEntity<String> confirmReservation(UUID id) {
        return restTemplate.postForEntity(reservationUrl(id) + "/confirm", null, String.class);
    }

    protected ResponseEntity<String> cancelReservation(UUID id) {
        return restTemplate.postForEntity(reservationUrl(id) + "/cancel", null, String.class);
    }

    protected JsonNode getInventory(String sku) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(inventoryUrl(sku), String.class);
        return objectMapper.readTree(response.getBody()).get("data");
    }

    protected String errorCode(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).get("error").get("code").asText();
    }

    protected List<ResponseEntity<String>> runConcurrentRequests(
            int threadCount,
            Callable<ResponseEntity<String>> task
    ) throws Exception {
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(task);
        }
        return runConcurrentRequests(tasks);
    }

    protected List<ResponseEntity<String>> runConcurrentRequests(
            List<Callable<ResponseEntity<String>>> tasks
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        for (Callable<ResponseEntity<String>> task : tasks) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return task.call();
            }));
        }

        startLatch.countDown();
        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            responses.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return responses;
    }
}
