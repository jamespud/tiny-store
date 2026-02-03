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
	$(MAVEN) clean install -Dmaven.test.skip=true
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

it: build ## Run integration tests (Testcontainers only, no compose)
	@echo "Running integration tests with Testcontainers..."
	@echo "WARNING: Ensure no other Docker containers conflict with Testcontainers infra"
	$(MAVEN) clean verify -Pit -DskipITs=false
	@echo "Integration tests completed successfully"

e2e: build ## Run E2E/API tests (compose stack only, no Testcontainers)
	@echo "Starting test environment for E2E/API tests..."
	@set -e; \
	cleanup() { \
		echo "Cleaning up test environment..."; \
		docker compose -f $(COMPOSE_TEST) down -v; \
	}; \
	trap cleanup EXIT; \
	docker compose -f $(COMPOSE_TEST) up -d; \
	echo "Waiting for services to be healthy..."; \
	sleep 30; \
	echo "Running API tests (only black-box tests against gateway)..."; \
	cd tests/api && ../../$(MAVEN) verify || exit 1; \
	echo "E2E/API tests passed successfully"

test: ## Run full test suite (IT → E2E, sequentially)
	@echo "Running full test suite (Phase 1: IT, Phase 2: E2E)..."
	@$(MAKE) it
	@echo ""
	@echo "Phase 1 (IT) completed. Starting Phase 2 (E2E)..."
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
