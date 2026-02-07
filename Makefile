.PHONY: help debug test down build clean

# Default target
.DEFAULT_GOAL := help

# Variables
COMPOSE_DEBUG := docker/docker-compose-debug.yml
COMPOSE_TEST := docker/docker-compose-test.yml
MAVEN := ./mvnw

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

build: ## Build all services with Maven
	@echo "Building all services..."
	$(MAVEN) clean install -U -Dmaven.test.skip=true
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
	$(MAVEN) -pl '!tests/api' test
	@echo "Unit tests completed successfully"

it: build ## Run integration tests (Testcontainers only, no compose)
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
	$(MAVEN) clean verify -Pit -DskipITs=false -DskipTests -pl '!tests/api'
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
