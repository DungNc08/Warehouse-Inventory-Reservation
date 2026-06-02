# Warehouse Inventory Reservation System

A Spring Boot service that reserves warehouse inventory under concurrent load without overselling stock.

## 1. Challenge Choice

Warehouse Inventory Reservation System**

This challenge maps directly to a common production problem: preventing overselling when multiple clients reserve the same SKU at the same time. It exercises transactional design, database locking, state management, and concurrency testing — areas that are harder to demonstrate convincingly in a pricing engine exercise.

## 2. Architecture Overview

The project follows a layered structure with a strict dependency direction: **API → Service → Domain**, never the reverse.

```
Warehouse-Inventory-Reservation/
├── database/                    # Liquibase SQL migrations + migration Docker image
├── src/main/java/com/warehouse/
│   ├── api/
│   │   ├── controller/          # REST endpoints
│   │   ├── dto/                 # HTTP request/response types + { data, error } envelope
│   │   ├── mapper/              # RequestMapper (inbound), ResponseMapper (outbound)
│   │   └── exception/           # GlobalExceptionHandler
│   ├── service/                 # Service interfaces + *Impl (unit-tested)
│   ├── domain/
│   │   ├── command/             # CreateReservationCommand (no API dependency)
│   │   ├── model/               # JPA entities + stock/reservation logic
│   │   └── state/               # Reservation lifecycle (State pattern)
│   ├── factory/                 # ReservationFactory interface + impl (Factory pattern)
│   ├── repository/              # Spring Data JPA + lock-aware queries
│   └── exception/               # Domain/business exceptions
└── docker-compose.yml
```

**Request flow:**

```
Controller → RequestMapper → Service → Repository / Factory / Domain
                ↓                              ↓
         ReservationRequest            CreateReservationCommand
         (API DTO)                     (domain command)
```

- **API layer** — HTTP, Jakarta validation, maps DTOs to domain commands, wraps responses in `{ data, error }`.
- **Service layer** — transactions, stock validation, pessimistic locking, reservation workflows.
- **Domain layer** — entities, command objects, state transitions (State pattern).
- **Repository layer** — persistence with `PESSIMISTIC_WRITE` queries for contended rows.

API DTOs never leak into the service or factory. The service accepts `CreateReservationCommand`; only `RequestMapper` in the API layer knows about `ReservationRequest`.

### REST API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/reservations` | Reserve inventory (all-or-nothing per request) |
| `GET` | `/api/v1/reservations/{id}` | Get reservation by ID |
| `POST` | `/api/v1/reservations/{id}/confirm` | Move `PENDING` → `CONFIRMED` |
| `POST` | `/api/v1/reservations/{id}/cancel` | Move `PENDING` → `CANCELLED`, release stock |
| `GET` | `/api/v1/inventory/{sku}` | Get current stock for a SKU |

## 3. Design Patterns

The assignment requires **State** and **Factory** — both are named here and implemented in code.

### State Pattern
Reservation lifecycle transitions are handled by state objects instead of scattered `if/else` checks.

| Class | Location |
|---|---|
| `ReservationState` | `domain/state/ReservationState.java` |
| `PendingReservationState` | `domain/state/PendingReservationState.java` |
| `ConfirmedReservationState` | `domain/state/ConfirmedReservationState.java` |
| `CancelledReservationState` | `domain/state/CancelledReservationState.java` |
| Context | `domain/model/Reservation.java` (`confirm()`, `cancel()`) |

Valid transitions: `PENDING → CONFIRMED`, `PENDING → CANCELLED`. `CONFIRMED` is terminal.

### Factory Pattern
Reservation entity creation is centralized; the factory builds domain objects from a domain command.

| Class | Location |
|---|---|
| `ReservationFactory` | `factory/ReservationFactory.java` (interface) |
| `ReservationFactoryImpl` | `factory/ReservationFactoryImpl.java` |
| Input | `domain/command/CreateReservationCommand.java` |

## 4. SOLID Principles

| Principle | Where |
|---|---|
| **S** — Single Responsibility | `ReservationService` orchestrates workflows; `Inventory` owns stock mutations; state classes own transitions; `RequestMapper`/`ResponseMapper` own DTO mapping |
| **O** — Open/Closed | New reservation states can be added by implementing `ReservationState` without changing existing state classes |
| **L** — Liskov Substitution | All `ReservationState` implementations are interchangeable through the interface |
| **I** — Interface Segregation | Small focused interfaces: `ReservationState`, repositories, `ReservationService` / `InventoryService`, `ReservationFactory`, `RequestMapper` / `ResponseMapper` |
| **D** — Dependency Inversion | Controllers and services depend on interfaces (`*Service`, `ReservationFactory`, repositories); Spring wires `*Impl` classes. API DTOs are converted to domain commands at the boundary (`RequestMapper`) so inner layers never depend on HTTP types |

## 5. Database Design

Schema is managed **outside the application** in the `database/` folder. Migrations run as a one-shot Docker job before the app starts — the Spring Boot service does not auto-run Liquibase.

```
database/
├── Dockerfile
├── liquibase.properties
└── changelog/
    ├── db.changelog-master.yaml
    └── changes/
        ├── 001-create-schema.sql    # SQL changesets only (assignment requirement)
        └── 002-seed-data.sql
```

| Table | Purpose |
|---|---|
| `products` | SKU catalog (name, description) |
| `inventory` | Stock per SKU: `total_stock`, `available_stock`, `reserved_stock`, optimistic `version` |
| `reservations` | Reservation header: UUID, unique `order_id`, status, timestamps |
| `reservation_items` | Line items per reservation (SKU + quantity) |

**Decisions:**
- Migrations decoupled from the app JAR — can run as an independent deploy job (Kubernetes Job, CI step, or Compose one-shot service).
- `order_id` has a `UNIQUE` constraint to prevent duplicate reservations for the same order (backed by application check + DB constraint).
- Stock invariant enforced at DB level: `available_stock + reserved_stock <= total_stock`.
- **Concurrency — inventory rows:** `SELECT … FOR UPDATE` (`PESSIMISTIC_WRITE`) on all SKUs in a reservation before any stock change; SKUs locked in **sorted order** to avoid deadlocks (`InventoryRepository.findAllBySkuInForUpdate`).
- **Concurrency — reservation rows:** `PESSIMISTIC_WRITE` on the reservation entity before confirm/cancel to prevent double-cancel releasing stock twice (`ReservationRepository.findWithItemsByIdForUpdate`).
- `inventory.version` (`@Version`) increments on JPA flush as a secondary optimistic safeguard; primary correctness comes from pessimistic locking.

## 6. How to Run the System

**Prerequisites:** Docker and Docker Compose

```bash
docker compose up
```

On first run, Compose builds the migration and app images automatically. If you changed code locally, use:

```bash
docker compose up --build
```

Compose starts services in order:

1. **postgres** — PostgreSQL 16 database
2. **migration** — one-shot Liquibase job (`database/Dockerfile`) applies SQL changesets
3. **app** — Spring Boot API (starts only after migration succeeds)

The API is available at `http://localhost:8080` with no manual migration steps.

**Example requests:**

```bash
# Reserve inventory
curl -s -X POST http://localhost:8080/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-1","items":[{"sku":"A100","quantity":30}]}'

# Get reservation
curl -s http://localhost:8080/api/v1/reservations/{id}

# Get inventory
curl -s http://localhost:8080/api/v1/inventory/A100

# Confirm reservation
curl -s -X POST http://localhost:8080/api/v1/reservations/{id}/confirm

# Cancel reservation
curl -s -X POST http://localhost:8080/api/v1/reservations/{id}/cancel
```

**Sample responses:**

`GET /api/v1/inventory/A100` (initial seed stock):

```json
{
  "data": {
    "sku": "A100",
    "totalStock": 100,
    "availableStock": 100,
    "reservedStock": 0
  },
  "error": null
}
```

`POST /api/v1/reservations` (reserve 30 units — success):

```json
{
  "data": {
    "id": "280bb980-adf0-4d15-9f6d-367d125b1ad3",
    "orderId": "ORD-1",
    "status": "PENDING",
    "createdAt": "2026-06-02T07:01:47.710085364Z",
    "items": [
      {
        "sku": "A100",
        "quantity": 30
      }
    ]
  },
  "error": null
}
```

`POST /api/v1/reservations` (insufficient stock — rejected):

```json
{
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "SKU A100 has only 30 units available, 50 were requested"
  }
}
```

Seed data includes SKU `A100` (100 units) and `B200` (50 units).

## 7. How to Run the Tests

**Unit tests** (no Docker required):

```bash
./mvnw test
```

| Suite | Class | What it covers |
|-------|--------|----------------|
| Service | `ReservationServiceTest` (8) | Insufficient stock, duplicate order, multi-SKU rejection, confirm/cancel |
| State | `ReservationStateTest` (3) | Valid and invalid state transitions |
| Concurrency | `ReservationServiceConcurrencyTest` (3) | SKU lock order + no stock mutation before lock; cancel uses `findWithItemsByIdForUpdate` before release |
| Error handling | `GlobalExceptionHandlerTest` (2) | `DataIntegrityViolationException` → `409 DUPLICATE_ORDER` |

**Integration test** (requires Docker):

| Suite | Class | What it covers |
|-------|--------|----------------|
| Integration | `ConcurrentReservationIT` (1) | Testcontainers + real PostgreSQL; two concurrent `POST /reservations` for SKU `A100` (60+60 vs 100 stock) → one `201`, one `409 INSUFFICIENT_STOCK`; inventory ends at available=40, reserved=60 |

`ConcurrentReservationIT` is **skipped** if Docker is unavailable (`@Testcontainers(disabledWithoutDocker = true)`). Unit tests always run.

**Run the integration test explicitly** (Docker Desktop on Linux; avoids `@` in socket path):

```bash
chmod +x scripts/run-integration-tests.sh
./scripts/run-integration-tests.sh
```

The script links `~/.docker/desktop/docker-cli.sock` → `/tmp/tc-docker.sock` (required for Testcontainers when your home path contains `@`).

## 8. Trade-offs

| Decision | Trade-off |
|---|---|
| Pessimistic locking | Strong correctness under contention, but lower throughput on hot SKUs |
| State objects recreated per transition | Simple and stateless, but not a persistent State pattern with injected dependencies |
| All-or-nothing multi-SKU reserve | Safer for orders, but one unavailable SKU rejects the entire request |
| Duplicate-order check + DB unique constraint | Application-level `existsByOrderId` catches most cases early; DB constraint is the safety net under race (mapped to `409 DUPLICATE_ORDER` via `GlobalExceptionHandler`) |
| In-process concurrency test | Validates DB locking end-to-end, but not full multi-instance deployment behavior |

**With more time:**
- Add idempotency keys for reserve requests
- Add reservation expiry / automatic release job
- Add observability (metrics for lock wait time, rejected reservations)
- Add integration tests for confirm/cancel/get endpoints

## 9. Scale Considerations

| Bottleneck | Fix |
|---|---|
| Row lock contention on hot SKUs | Partition inventory by warehouse, queue reservation requests, or use Redis-based reservation tokens |
| Single PostgreSQL writer | Read replicas for inventory reads; writer only for reserve/cancel |
| Synchronous reserve API | Event-driven reservation pipeline with outbox pattern |
| Multi-region latency | Regional inventory shards with limited cross-region reservations |

---

## Tech Stack

- Java 17
- Spring Boot 3.2
- PostgreSQL 16
- Spring Data JPA
- Liquibase (SQL changesets in `database/`, run via migration job)
- JUnit 5 + Mockito
- Testcontainers

## API Response Format

Every API response — success or error — uses this envelope (assignment requirement):

**Success:**

```json
{ "data": { ... }, "error": null }
```

**Error:**

```json
{
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "SKU A100 has only 30 units available, 50 were requested"
  }
}
```

| Error code | HTTP | When |
|---|---|---|
| `INSUFFICIENT_STOCK` | 409 | Not enough available stock for a SKU |
| `DUPLICATE_ORDER` | 409 | Order ID already has a reservation |
| `INVALID_STATE_TRANSITION` | 409 | e.g. confirm a `CONFIRMED` reservation, cancel a `CONFIRMED` reservation |
| `RESERVATION_NOT_FOUND` | 404 | Unknown reservation ID |
| `INVENTORY_NOT_FOUND` | 404 | Unknown SKU |
| `VALIDATION_ERROR` | 400 | Invalid request body |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
