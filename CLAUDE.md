# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Uses Maven wrapper (`./mvnw`) and a Makefile. Java 17, Spring Boot 3.5, Spring Cloud 2025.0.0.

```bash
make build           # Full Maven build (clean install, skip tests)
make unit            # Unit tests only (excludes tests/api and tests/performance)
make it              # Integration tests with Testcontainers (excludes tests/api)
make e2e             # E2E/API tests against compose stack (make build + docker compose up + API tests)
make e2e-smoke       # Health-check-only E2E (gateway + all service routes)
make test            # Full suite: Unit → IT → E2E sequentially
make consistency     # 200-concurrent-request consistency tests (compose stack + JUnit assertions)
make load            # k6 load tests (requires k6 installed)
make debug           # Start debug Docker environment (PostgreSQL, Redis, Kafka, Nacos + all 8 services)
make down            # Stop debug env (use MODE=test for test env)
make clean           # Clean build artifacts + Docker containers/volumes
```

**Running a single test:**
```bash
./mvnw test -pl tinystore-domain-inventory -Dtest=InventoryReservationDomainServiceTest
./mvnw test -pl tinystore-domain-inventory -Dtest=InventoryReservationDomainServiceTest#reserve_shouldCreatePreDeductedReservations
./mvnw test -pl tinystore-domain-order -Dtest=TradeApplicationServiceCanonicalTest
```

**Running a single IT:**
```bash
./mvnw verify -pl tinystore-domain-order -Pit -DskipITs=false -Dtest=TradeEndpointIT
```

**Running a single E2E test (requires compose stack running):**
```bash
cd tests/api && ../../mvnw verify -Pit -DskipITs=false -Dtest=MallE2EIT#strictClosureE2E_createTradeToConfirmReceipt_shouldCompleteFullLifecycle
```

## Architecture Overview

This is a **DDD-style microservices e-commerce platform** using hexagonal/ports-and-adapters architecture. Each domain module follows the same internal layering:

```
domain-module/
├── domain/          # model (aggregates, value objects), service (domain services), repository (ports), event, enums, command
├── application/     # application services (orchestration), commands, DTOs
├── infrastructure/  # persistence (JPA entities, repository impls), ACL (Feign clients), scheduler, event (Kafka/outbox), config
└── interfaces/      # REST controllers, DTOs, mappers, internal REST endpoints
```

### Module Map

| Module | Port | Schema | Purpose |
|--------|------|--------|---------|
| `tinystore-domain-gateway` | 8080 | — | Spring Cloud Gateway: routing, JWT auth, IP rate limiting, request idempotency |
| `tinystore-domain-auth` | 9000 | `tinystore_auth` | OTP/password auth, token revocation, consent, client registration |
| `tinystore-domain-account` | 8000 | `tinystore_account` | User accounts, addresses, merchant shops |
| `tinystore-domain-inventory` | 13000 | `tinystore_inventory` | Inventory reservation lifecycle, stock management, Redis deduct gateway |
| `tinystore-domain-order` | 28080 | `tinystore_order` | Trade/order creation, payment saga, fulfillment, outbox events |
| `tinystore-domain-payment` | 8083 | `tinystore_payment` | Payment orders, refunds, notification retry |
| `tinystore-domain-product` | 8090 | `tinystore_product` | Product/SKU catalog, specifications, attributes |
| `tinystore-domain-promotion` | 1200 | `tinystore_promotion` | Coupons, checkout quotes (quote/commit/release), budget monitoring |
| `tinystore-library-infrastructure` | — | — | Shared: Redis, Redisson, Kafka constants, Feign clients, JSON utils, security auto-config |

### Infrastructure & Dependencies

- **PostgreSQL 18** — one database `tinystore`, each domain in its own schema (`tinystore_order`, `tinystore_inventory`, etc.)
- **Redis 7.4** — idempotency store, rate limiting, high-concurrency inventory gateway (non-authoritative)
- **Kafka 4.1** — domain event bus (order events consumed by payment, promotion)
- **Nacos** — service discovery and (optionally) config management
- **Flyway** — per-module migrations under `src/main/resources/db/migration/<schema>/`
- **Testcontainers** — PostgreSQL, Kafka containers for ITs
- **Redisson** — distributed locks and Redis abstractions
- **Spring Security OAuth2 Resource Server** — JWT validation (can be disabled via `tinystore.security.resource-server.enabled`)

### Key Cross-Cutting Patterns

**Outbox Pattern (order domain):** `OutboxEventService` persists events to `outbox_event` table transactionally with business writes. `OutboxEventPublisherScheduler` polls and publishes to Kafka. `OutboxCleanupScheduler` removes old delivered events. DLT support for failed deliveries.

**Idempotency:** `IdempotencyService` in order domain uses Redis with configurable TTL. Gateway has separate `IdempotencyService` with local Caffeine fallback. All critical mutating endpoints require `Idempotency-Key` header.

**ACL (Anti-Corruption Layer):** Inter-service calls use Feign clients under `infrastructure/acl/`. Order domain's `InventoryClient` and `PromotionClient` are the primary examples. DTOs are duplicated per domain to avoid cross-domain coupling.

**Feature Flags:** Gradual rollout is controlled via Spring `@Value` / `@ConditionalOnProperty`:
- `order.inventory.use-canonical-reservation-api` — route new orders to canonical reservation API
- `inventory.reservation.canonical-enabled` — enable canonical reservation app service
- `inventory.legacy-stock-api-enabled` — control legacy `/api/inventory/stock/*` endpoints

### Inventory Reservation Architecture (RFC-001)

This is the most architecturally significant subsystem. Key rules:

1. **`inventory_reservation` is the single authority** for inventory lifecycle. `inventory_stock.total_quantity` is the authoritative remaining-stock field. Redis is non-authoritative (concurrency gate only). `inventory_deduct_record` is an execution log only — never carries lifecycle states.

2. **Canonical state machine:** `PRE_DEDUCTED → CONFIRMED` (payment success, terminal) | `PRE_DEDUCTED → RELEASED` (cancel, terminal) | `PRE_DEDUCTED → EXPIRED` (scheduler only, terminal). `CONFIRMED → RELEASED` is **forbidden** (must go through after-sale/refund).

3. **Rollout C strategy:** Schema migrations deployed first (V7, V12, V13), feature flags off. Then `use-canonical-reservation-api` enabled gradually. Legacy drained when all version-1 orders clear.

4. **Dual-write compatibility:** `ShopOrderRepositoryImpl` reads from `inventory_reservation_refs_json` (v2) with fallback to `inventory_pre_occupy_ids_json` (v1). Writes go to both columns for v2 orders. `InventoryStatus.getByCode()` accepts legacy codes (`LOCKED`→`PRE_DEDUCTED`, `DEDUCTED`→`CONFIRMED`) as parser aliases.

### Test Structure

- **Unit tests:** `*Test.java` under `src/test/` in each domain module. Mock MVC for controllers, mock beans for services.
- **Integration tests:** `*IT.java` using Testcontainers (PostgreSQL, Kafka). Found in domain modules under `src/test/`.
- **API/E2E tests:** `tests/api/` — black-box tests against the gateway using `TestRestTemplate`. Require full compose stack.
- **Performance tests:** `tests/performance/` — consistency tests (200 concurrent requests with DB assertions) and k6 load scripts.

### Common Development Tasks

**Adding a new Flyway migration:** Create `V<N>__description.sql` in the domain's `db/migration/<schema>/` directory. Follow existing naming conventions (next sequential number after last existing migration).

**Adding a new domain endpoint:** Controller in `interfaces/rest/`, request/response DTOs in `interfaces/dto/`, application service in `application/service/`, domain logic in `domain/service/`. Follow the existing pattern of `@Validated` controllers with `Idempotency-Key` header support.

**Working with feature flags:** Add `@Value` or `@ConditionalOnProperty` in the relevant component. Flag names follow `order.inventory.*` or `inventory.*` conventions. Default to `false` for new canonical features, `true` for legacy compatibility.
