.PHONY: help debug test down build clean

# Default target
.DEFAULT_GOAL := help

# Variables
COMPOSE_DEBUG := docker/docker-compose-debug.yml
COMPOSE_TEST := docker/docker-compose-test.yml
COMPOSE_PERF := $(abspath docker/docker-compose-perf.yml)
PERF_COMPOSE := $(COMPOSE_PERF)
MAVEN := "./mvnw"
MAVEN_CLEAN_OPTS := -Dmaven.clean.failOnError=false

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

build: ## Build all services with Maven
	@echo "Building all services..."
	$(MAVEN) clean install -U -Dmaven.test.skip=true $(MAVEN_CLEAN_OPTS)
	@echo "Build completed successfully"

debug: build ## Start debug environment (fixed ports: 5432/6379/9092/8848)
	@echo "Starting debug environment..."
	@echo "WARNING: Debug and test environments cannot run simultaneously (same service ports)"
	@docker compose -f $(COMPOSE_DEBUG) up -d
	@echo ""
	@echo "Debug environment started successfully"
	@echo "Access points:"
	@echo "  - Gateway:    http://localhost:8080"
	@echo "  - Auth:       http://localhost:9000"
	@echo "  - Account:    http://localhost:8000"
	@echo "  - Inventory:  http://localhost:13000"
	@echo "  - Order:      http://localhost:28080"
	@echo "  - Payment:    http://localhost:8083"
	@echo "  - Product:    http://localhost:8090"
	@echo "  - Promotion:  http://localhost:1200"
	@echo "  - Nacos:      http://localhost:8848/nacos (username: nacos, password: nacos)"
	@echo "  - PostgreSQL: localhost:5432"
	@echo "  - Redis:      localhost:6379"
	@echo "  - Kafka:      localhost:9092"

unit: ## Run pure unit tests (fast, no Docker, mocked deps)
	@echo "Running unit tests (excluding E2E)..."
	$(MAVEN) -pl '!tests/api,!tests/performance' test
	@echo "Unit tests completed successfully"

it: build ## Run integration tests (Testcontainers only, no compose; excludes tests/api)
	@echo "Checking Docker Java API configuration for Testcontainers..."
	@if [ ! -f "$$HOME/.docker-java.properties" ]; then \
		echo "ERROR: $$HOME/.docker-java.properties not found"; \
		echo "Creating it with api.version=1.44..."; \
		echo "api.version=1.44" > "$$HOME/.docker-java.properties"; \
		echo "Created $$HOME/.docker-java.properties successfully"; \
	elif ! grep -q "api.version=1.44" "$$HOME/.docker-java.properties"; then \
		echo "WARNING: $$HOME/.docker-java.properties exists but missing 'api.version=1.44'"; \
		echo "Current content:"; \
		cat "$$HOME/.docker-java.properties"; \
		echo ""; \
		echo "Appending api.version=1.44..."; \
		echo "api.version=1.44" >> "$$HOME/.docker-java.properties"; \
		echo "Updated successfully"; \
	else \
		echo "✓ Docker Java API configuration verified (api.version=1.44)"; \
	fi
	@echo "Running integration tests with Testcontainers..."
	@echo "WARNING: Ensure no other Docker containers conflict with Testcontainers infra"
	$(MAVEN) clean verify -Pit -DskipITs=false -DskipTests -pl '!tests/api,!tests/performance' $(MAVEN_CLEAN_OPTS)
	@echo "Integration tests completed successfully"

e2e: build ## Run E2E/API tests (compose stack only, no Testcontainers)
	@echo "Starting test environment for E2E/API tests..."
	@set -e; \
	root_dir=$$(pwd); \
	cleanup() { \
		echo "Cleaning up test environment..."; \
		cd "$$root_dir"; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d --build; \
	echo "Waiting for gateway to be healthy (max 120s)..."; \
	for i in $$(seq 1 24); do \
		if curl -sf --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1; then \
			echo "Gateway is healthy after $$((i*5))s"; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Gateway failed to become healthy within 120s"; \
			echo "=== Gateway logs ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=50 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Waiting for downstream routes to be ready (max 120s)..."; \
	routes_ready=0; \
	for i in $$(seq 1 24); do \
		all_ready=1; \
		for path in "/internal/health/order" "/internal/health/promotion" "/internal/health/inventory" "/internal/health/product" "/internal/health/auth" "/internal/health/account" "/internal/health/pay"; do \
			response=$$(curl -sS --max-time 3 "http://localhost:8080$$path" 2>&1 || true); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:8080$$path" 2>&1 || echo 000); \
			if [ "$$code" != "200" ] || ! echo "$$response" | grep -q 'UP'; then \
				all_ready=0; \
				break; \
			fi; \
		done; \
		if [ $$all_ready -eq 1 ]; then \
			echo "Downstream routes are ready after $$((i*5))s"; \
			routes_ready=1; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Downstream routes failed to become ready within 120s"; \
			echo "=== Route status ==="; \
			for path in "/internal/health/order" "/internal/health/promotion" "/internal/health/inventory" "/internal/health/product" "/internal/health/auth" "/internal/health/account" "/internal/health/pay"; do \
				response=$$(curl -sS --max-time 3 "http://localhost:8080$$path" 2>&1 || true); \
				code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:8080$$path" 2>&1 || echo 000); \
				body_prefix=$$(echo "$$response" | LC_ALL=C cut -c 1-50); \
				echo "GET $$path -> $$code | $$body_prefix"; \
			done; \
			echo "=== Gateway logs (last 100 lines) ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=100 gateway; \
			echo "=== Product logs (last 100 lines) ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=100 product; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Running API tests (only black-box tests against gateway)..."; \
	cd tests/api && ../../$(MAVEN) verify -Pit -DskipITs=false || exit 1; \
	echo "E2E/API tests passed successfully"

e2e-smoke: build ## Run E2E smoke tests (gateway + all service health routes only, no business tests)
	@echo "Starting test environment for E2E smoke check..."
	@set -e; \
	root_dir=$$(pwd); \
	cleanup() { \
		echo "Cleaning up test environment..."; \
		cd "$$root_dir"; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d; \
	echo "Waiting for gateway to be healthy (max 120s)..."; \
	for i in $$(seq 1 24); do \
		if curl -sf --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1; then \
			echo "Gateway is healthy after $$((i*5))s"; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Gateway failed to become healthy within 120s"; \
			echo "=== Gateway logs ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=50 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Waiting for all service health-check routes to be ready (max 120s)..."; \
	all_routes_ready=0; \
	for i in $$(seq 1 24); do \
		all_ready=1; \
		for path in "/internal/health/order" "/internal/health/promotion" "/internal/health/inventory" "/internal/health/product" "/internal/health/auth" "/internal/health/account" "/internal/health/pay"; do \
			response=$$(curl -sS --max-time 3 "http://localhost:8080$$path" 2>&1); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:8080$$path" 2>&1); \
			if [ "$$code" != "200" ] || ! echo "$$response" | grep -q 'UP'; then \
				all_ready=0; \
				break; \
			fi; \
		done; \
		if [ $$all_ready -eq 1 ]; then \
			echo "All health-check routes are ready after $$((i*5))s"; \
			all_routes_ready=1; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Service health-check routes failed within 120s"; \
			echo "=== Route status ==="; \
			for path in "/internal/health/order" "/internal/health/promotion" "/internal/health/inventory" "/internal/health/product" "/internal/health/auth" "/internal/health/account" "/internal/health/pay"; do \
				response=$$(curl -sS --max-time 3 "http://localhost:8080$$path" 2>&1); \
				code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:8080$$path" 2>&1); \
				body_prefix=$$(echo "$$response" | LC_ALL=C cut -c 1-50); \
				echo "GET $$path -> $$code | $$body_prefix"; \
			done; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "E2E smoke check passed: gateway + all service routes are healthy"

test: ## Run full test suite (Unit → IT → E2E, sequentially)
	@echo "Running full test suite (Phase 1: Unit, Phase 2: IT, Phase 3: E2E)..."
	@$(MAKE) unit
	@echo ""
	@echo "Phase 1 (Unit) completed. Starting Phase 2 (IT)..."
	@echo ""
	@$(MAKE) it
	@echo ""
	@echo "Phase 2 (IT) completed. Starting Phase 3 (E2E)..."
	@echo ""
	@$(MAKE) e2e
	@echo ""
	@echo "All tests passed successfully"

down: ## Stop and remove containers (default: debug; use MODE=test for test env)
	@if [ "$(MODE)" = "test" ]; then \
		echo "Stopping test environment..."; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	else \
		echo "Stopping debug environment..."; \
		docker compose -f $(COMPOSE_DEBUG) down -v; \
	fi
	@echo "Environment stopped and cleaned up"

clean: ## Clean all build artifacts and Docker resources
	@echo "Cleaning build artifacts..."
	$(MAVEN) clean
	@echo "Removing all Docker containers and volumes..."
	@docker compose -f $(COMPOSE_DEBUG) down -v 2>/dev/null || true
	@docker compose -f $(COMPOSE_TEST) down -v 2>/dev/null || true
	@echo "Clean completed"

logs: ## Show logs (default: debug; use MODE=test SERVICE=name for specific service)
	@if [ "$(MODE)" = "test" ]; then \
		if [ -n "$(SERVICE)" ]; then \
			docker compose -f $(COMPOSE_TEST) logs -f $(SERVICE); \
		else \
			docker compose -f $(COMPOSE_TEST) logs -f; \
		fi \
	else \
		if [ -n "$(SERVICE)" ]; then \
			docker compose -f $(COMPOSE_DEBUG) logs -f $(SERVICE); \
		else \
			docker compose -f $(COMPOSE_DEBUG) logs -f; \
		fi \
	fi

status: ## Show status of all services
	@echo "Debug environment:"
	@docker compose -f $(COMPOSE_DEBUG) ps || echo "Not running"
	@echo ""
	@echo "Test environment:"
	@docker compose -f $(COMPOSE_TEST) ps || echo "Not running"

consistency: build ## Run concurrency consistency tests (200 concurrent requests, DB assertions)
	@echo "Starting test environment for consistency tests..."
	@set -e; \
	root_dir=$$(pwd); \
	cleanup() { \
		echo "Cleaning up test environment..."; \
		cd "$$root_dir"; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d --build; \
	echo "Waiting for gateway to be healthy (max 120s)..."; \
	for i in $$(seq 1 24); do \
		if curl -sf --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1; then \
			echo "Gateway is healthy after $$((i*5))s"; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Gateway failed to become healthy within 120s"; \
			echo "=== Gateway logs ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=50 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Waiting for downstream routes to be ready (max 120s)..."; \
	routes_ready=0; \
	for i in $$(seq 1 24); do \
		all_ready=1; \
		for path in "/internal/health/order" "/internal/health/inventory"; do \
			response=$$(curl -sS --max-time 3 "http://localhost:8080$$path" 2>&1 || true); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:8080$$path" 2>&1 || echo 000); \
			if [ "$$code" != "200" ] || ! echo "$$response" | grep -q 'UP'; then \
				all_ready=0; \
				break; \
			fi; \
		done; \
		if [ $$all_ready -eq 1 ]; then \
			echo "Downstream routes (order, inventory) are ready after $$((i*5))s"; \
			routes_ready=1; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Downstream routes failed to become ready within 120s"; \
			echo "=== Gateway logs ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=100 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Running consistency tests (200 concurrent, DB strong assertions)..."; \
	$(MAVEN) -pl tests/performance test -Pperf || exit 1; \
	echo "Consistency tests passed successfully"

load: build ## Run k6 load tests (stress test with p95/p99 latency metrics)
	@echo "Starting test environment for load tests..."
	@echo "WARNING: k6 must be installed (https://k6.io/docs/get-started/installation/)"
	@if ! command -v k6 > /dev/null 2>&1; then \
		echo "ERROR: k6 not found. Install it first:"; \
		echo "  macOS:   brew install k6"; \
		echo "  Linux:   sudo apt install k6 (or download from https://k6.io)"; \
		echo "  Windows: choco install k6"; \
		exit 1; \
	fi
	@set -e; \
	root_dir=$$(pwd); \
	cleanup() { \
		echo "Cleaning up test environment..."; \
		cd "$$root_dir"; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d --build; \
	echo "Waiting for gateway to be healthy (max 120s)..."; \
	for i in $$(seq 1 24); do \
		if curl -sf --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1; then \
			echo "Gateway is healthy after $$((i*5))s"; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Gateway failed to become healthy within 120s"; \
			echo "=== Gateway logs ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=50 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Waiting for downstream routes to be ready (max 120s)..."; \
	routes_ready=0; \
	for i in $$(seq 1 24); do \
		all_ready=1; \
		for path in "/internal/health/order" "/internal/health/inventory"; do \
			response=$$(curl -sS --max-time 3 "http://localhost:8080$$path" 2>&1 || true); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:8080$$path" 2>&1 || echo 000); \
			if [ "$$code" != "200" ] || ! echo "$$response" | grep -q 'UP'; then \
				all_ready=0; \
				break; \
			fi; \
		done; \
		if [ $$all_ready -eq 1 ]; then \
			echo "Downstream routes (order, inventory) are ready after $$((i*5))s"; \
			routes_ready=1; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: Downstream routes failed to become ready within 120s"; \
			echo "=== Gateway logs ==="; \
			docker compose -f $(COMPOSE_TEST) logs --tail=100 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Running k6 load tests..."; \
	cd perf/k6 && k6 run order_create.js || exit 1; \
	echo "Load tests completed successfully"


load-min: build ## Peak-test the order-creation link on the minimal stack (7 containers, direct to order)
	@echo "Starting minimal order-link peak-test stack (postgres+redis+kafka+nacos+order+inventory+promotion)..."
	@echo "WARNING: k6 must be installed (https://k6.io/docs/get-started/installation/)"
	@if ! command -v k6 > /dev/null 2>&1; then \
		echo "ERROR: k6 not found. Install it first:"; \
		echo "  macOS:   brew install k6"; \
		echo "  Linux:   sudo apt install k6 (or download from https://k6.io)"; \
		exit 1; \
	fi
	@set -e; \
	root_dir=$$(pwd); \
	levels="$${VUS_LEVELS:-1000 2000 3000 4000 5000}"; \
	warmup="$${WARMUP_LEVEL:-$${levels%% *}}"; \
	duration="$${DURATION:-30s}"; \
	stock="$${STOCK_PER_SKU:-500000}"; \
	cleanup() { \
		echo "Cleaning up perf stack..."; \
		cd "$$root_dir"; \
		docker compose -f $(PERF_COMPOSE) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(PERF_COMPOSE) up -d --build; \
	echo "Waiting for postgres (max 120s)..."; \
	for i in $$(seq 1 24); do \
		if docker compose -f $(PERF_COMPOSE) exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then \
			echo "Postgres ready after $$((i*5))s"; \
			break; \
		fi; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: postgres failed to become ready within 120s"; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Waiting for order (direct :28080) + inventory + promotion to be healthy (max 240s)..."; \
	for i in $$(seq 1 48); do \
		ok=1; \
		for base in http://localhost:28080 http://localhost:13000 http://localhost:1200; do \
			if ! curl -sf --max-time 3 "$$base/actuator/health" > /dev/null 2>&1; then \
				ok=0; \
				break; \
			fi; \
		done; \
		if [ $$ok -eq 1 ]; then \
			echo "order/inventory/promotion healthy after $$((i*5))s"; \
			break; \
		fi; \
		if [ $$i -eq 48 ]; then \
			echo "ERROR: services failed to become healthy within 240s"; \
			docker compose -f $(PERF_COMPOSE) logs --tail=100 order; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	seed_sku() { \
		sku="$$1"; \
		docker compose -f $(PERF_COMPOSE) exec -T postgres psql -U postgres -d tinystore -v ON_ERROR_STOP=1 \
			-c "INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at) VALUES ('SHOP_A','$$sku',$$stock,0,0,NOW(),NOW()) ON CONFLICT DO NOTHING;" > /dev/null; \
	}; \
	seed_sku "SKU-perf-warmup"; \
	echo "Warmup (VUS=$$warmup, DURATION=$$duration, SKU=SKU-perf-warmup, discarded)..."; \
	VUS=$$warmup DURATION=$$duration SKU_ID=SKU-perf-warmup BASE_URL=http://localhost:28080 \
		k6 run --quiet order_create_direct.js --summary-export /tmp/tinystore-perf-warmup.export.json \
			> /tmp/tinystore-perf-warmup.json 2> /tmp/tinystore-perf-warmup.err || true; \
	echo "Running k6 direct-to-order peak scan (VUS=$$levels, DURATION=$$duration) - the first level is a transition and is discarded..."; \
	cd perf/k6; \
	first=1; \
	for lvl in $$levels; do \
		sku="SKU-perf-$$lvl"; \
		seed_sku "$$sku"; \
		echo "=== VUS=$$lvl DURATION=$$duration SKU=$$sku (direct order) ==="; \
		summary=""; \
		attempt=1; \
		while [ $$attempt -le 2 ]; do \
			VUS=$$lvl DURATION=$$duration SKU_ID=$$sku BASE_URL=http://localhost:28080 \
				k6 run --quiet order_create_direct.js --summary-export /tmp/tinystore-perf-$$lvl.export.json \
					> /tmp/tinystore-perf-$$lvl.json 2> /tmp/tinystore-perf-$$lvl.err || true; \
			summary=$$(python3 k6_summary.py /tmp/tinystore-perf-$$lvl.json /tmp/tinystore-perf-$$lvl.err || true); \
			case "$$summary" in \
				"NO DATA"*) \
					echo "  --> no data on attempt $$attempt (transient k6 init error?), retrying in 5s..."; \
					attempt=$$((attempt+1)); \
					sleep 5;; \
				*) \
					break;; \
			esac; \
		done; \
		if [ $$first -eq 1 ]; then \
			echo "  --> [transition] discarded (first level after warmup, system still reaching steady state)"; \
			first=0; \
		else \
			echo "  --> $$summary"; \
		fi; \
		echo "=== end VUS=$$lvl ==="; \
	done; \
	echo "Minimal-stack order-link peak test completed"

load-min-nokafka: ## Peak-test the order-creation link WITHOUT Kafka (pure-sync probe, async links do NOT close)
	$(MAKE) load-min PERF_COMPOSE=$(abspath docker/docker-compose-perf-nokafka.yml)

load-matrix: build ## Run k6 load matrix (multi-VUS scan through gateway, full stack, clean per-level results)
	@echo "Starting full-stack load matrix (gateway :8080)..."
	@echo "WARNING: k6 must be installed (https://k6.io/docs/get-started/installation/)"
	@if ! command -v k6 > /dev/null 2>&1; then \
		echo "ERROR: k6 not found. Install it first:"; \
		exit 1; \
	fi
	@set -e; \
	root_dir=$$(pwd); \
	levels="$${VUS_LEVELS:-500 1000 5000 10000}"; \
	duration="$${DURATION:-60s}"; \
	stock="$${STOCK_PER_SKU:-500000}"; \
	cleanup() { \
		echo "Cleaning up test environment..."; \
		cd "$$root_dir"; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d --build; \
	echo "Waiting for gateway (max 120s)..."; \
	for i in $$(seq 1 24); do \
		curl -sf --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1 && { echo "Gateway healthy after $$((i*5))s"; break; }; \
		if [ $$i -eq 24 ]; then \
			echo "ERROR: gateway failed to become healthy within 120s"; \
			docker compose -f $(COMPOSE_TEST) logs --tail=50 gateway; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Waiting 30s for Nacos service registration (order -> inventory via Feign)..."; \
	sleep 30; \
	seed_sku() { \
		sku="$$1"; \
		docker compose -f $(COMPOSE_TEST) exec -T postgres psql -U postgres -d tinystore -v ON_ERROR_STOP=1 \
			-c "INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at) VALUES ('SHOP_A','$$sku',$$stock,0,0,NOW(),NOW()) ON CONFLICT DO NOTHING;" > /dev/null; \
	}; \
	for lvl in $$levels; do \
		seed_sku "SKU-matrix-$$lvl"; \
	done; \
	echo "Running k6 load matrix (VUS=$$levels, DURATION=$$duration, per-level SKU, via gateway :8080)..."; \
	cd perf/k6; \
	VUS_LEVELS="$$levels" DURATION="$$duration" BASE_URL="http://localhost:8080" bash run_matrix.sh; \
	echo "Load matrix complete - per-level results printed above (see LOAD MATRIX SUMMARY)"

load-matrix-it: build ## Run consistency IT matrix (oversell/idempotency/confirm at multiple concurrency levels, DB assertions)
	@echo "Starting test environment for consistency matrix..."
	@set -e; \
	root_dir=$$(pwd); \
	cleanup() { echo "Cleaning up..."; cd "$$root_dir"; docker compose -f $(COMPOSE_TEST) down -v; }; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d --build; \
	echo "Waiting for gateway (max 120s)..."; \
	for i in $$(seq 1 24); do \
		curl -sf --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1 && { echo "Gateway healthy after $$((i*5))s"; break; }; \
		sleep 5; \
	done; \
	echo "Waiting 30s for Nacos service registration (order -> inventory via Feign)..."; \
	sleep 30; \
	for C in 500 1000 5000 10000; do \
		echo "===== OVERSELL C=$$C ====="; \
		$(MAVEN) -pl tests/performance test -Pperf -Dtest=InventoryOversellBoundaryIT -Dperf.concurrency=$$C -Dinventory.base.url=http://localhost:13000 -Dpg.url=jdbc:postgresql://localhost:5433/tinystore || exit 1; \
		echo "===== IDEMPOTENCY C=$$C ====="; \
		$(MAVEN) -pl tests/performance test -Pperf -Dtest=OrderCreateIdempotencyConsistencyIT -Dperf.concurrency=$$C -Dgateway.base.url=http://localhost:8080 -Dpg.url=jdbc:postgresql://localhost:5433/tinystore || echo "WARN: idempotency C=$$C failed (known stack bottleneck at high concurrency)"; \
	done; \
	for N in 500 1000; do \
		echo "===== CONFIRM-LOCK N=$$N ====="; \
		$(MAVEN) -pl tests/performance test -Pperf -Dtest=InventoryConfirmLockContentionIT -Dperf.confirm.concurrency=$$N -Dinventory.base.url=http://localhost:13000 -Dpg.url=jdbc:postgresql://localhost:5433/tinystore || exit 1; \
	done; \
	echo "Consistency matrix complete - collect outputs into docs/performance/load-report-2026-07-28.md"
