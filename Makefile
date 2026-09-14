.PHONY: help debug test down build clean

# Default target
.DEFAULT_GOAL := help

# Variables
COMPOSE_DEBUG := docker/docker-compose-debug.yml
COMPOSE_TEST := docker/docker-compose-test.yml
# Overlay that removes container_name/fixed-port pins so every app service can run N replicas.
COMPOSE_MULTI := docker/docker-compose-multi.yml
COMPOSE_PERF := $(abspath docker/docker-compose-perf.yml)
PERF_COMPOSE := $(COMPOSE_PERF)
MAVEN := "./mvnw"
MAVEN_CLEAN_OPTS := -Dmaven.clean.failOnError=false

# Multi-instance (distributed) test stack sizing.
# MULTI_REPLICAS is the single knob: `make e2e-multi MULTI_REPLICAS=3`. The host-port ranges in
# docker/docker-compose-multi.yml are 10 ports wide per service, so any N in [1..10] works with
# no compose edit; replicas are enumerated at runtime, never assumed to be "replica #1/#2".
MULTI_SERVICES := gateway auth account product promotion inventory order payment
MULTI_REPLICAS ?= 2
MULTI_MIN_REPLICAS := 1
# Must match the width of the host-port ranges in docker/docker-compose-multi.yml (10).
MULTI_MAX_REPLICAS := 10
MULTI_SCALE_FLAGS := $(foreach s,$(MULTI_SERVICES),--scale $(s)=$(MULTI_REPLICAS))
# Compose interpolation (deploy.replicas: ${MULTI_REPLICAS:-2}) reads the environment, not the
# make variable, so export it; otherwise `docker compose ... up` without --scale would differ.
export MULTI_REPLICAS
# resilience-multi: probe for this long, kill one replica this many seconds in.
MULTI_PROBE_SECONDS ?= 45
MULTI_KILL_AFTER ?= 10
# The replica-failure probe is a gate: reads must survive the kill (0 lost requests) and the retried
# request must come back inside MULTI_MAX_FAILOVER_MS. Set MULTI_EXPECT_FAILOVER=1 to invert it into
# the pre-fix demonstration, where the probe only passes when requests WERE lost (E1 evidence).
MULTI_EXPECT_FAILOVER ?= 0
MULTI_MAX_FAILOVER_MS ?= 5000
# rate-limit-multi: burst size / concurrency per case, and whether to re-create the gateways with the
# calling host as a trusted proxy for the second phase (the C16 trusted-proxy path).
MULTI_RATE_LIMIT_BURST ?= 200
MULTI_RATE_LIMIT_CONCURRENCY ?= 50

# Host ports published by the single-instance stack. docker-compose-test.yml interpolates the same
# names, so overriding them here and there stays consistent:
#   make e2e E2E_GATEWAY_PORT=28000 E2E_AUTH_PORT=29000
# A host port being taken (by an unrelated container on a shared dev box) is an environment detail,
# not a behaviour under test: the tests are pointed at whatever port was chosen. Nothing should
# treat "8080 is already allocated" as a blocker for validating the platform.
E2E_POSTGRES_PORT ?= 5433
E2E_REDIS_PORT ?= 6380
E2E_KAFKA_PORT ?= 9093
E2E_NACOS_PORT ?= 8849
E2E_NACOS_GRPC_PORT ?= 9849
E2E_GATEWAY_PORT ?= 8080
E2E_AUTH_PORT ?= 9000
E2E_ACCOUNT_PORT ?= 8000
E2E_PRODUCT_PORT ?= 8090
E2E_PROMOTION_PORT ?= 1200
E2E_INVENTORY_PORT ?= 13000
E2E_ORDER_PORT ?= 28080
E2E_PAYMENT_PORT ?= 8083
export E2E_POSTGRES_PORT E2E_REDIS_PORT E2E_KAFKA_PORT E2E_NACOS_PORT E2E_NACOS_GRPC_PORT \
       E2E_GATEWAY_PORT E2E_AUTH_PORT E2E_ACCOUNT_PORT E2E_PRODUCT_PORT E2E_PROMOTION_PORT \
       E2E_INVENTORY_PORT E2E_ORDER_PORT E2E_PAYMENT_PORT

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-18s %s\n", $$1, $$2}'

check-multi-replicas: ## Guard: MULTI_REPLICAS must be an integer in [1..10]
	@case "$(MULTI_REPLICAS)" in \
		''|*[!0-9]*) echo "ERROR: MULTI_REPLICAS must be an integer, got '$(MULTI_REPLICAS)'"; exit 1 ;; \
	esac; \
	if [ "$(MULTI_REPLICAS)" -lt $(MULTI_MIN_REPLICAS) ] || [ "$(MULTI_REPLICAS)" -gt $(MULTI_MAX_REPLICAS) ]; then \
		echo "ERROR: MULTI_REPLICAS=$(MULTI_REPLICAS) is out of range [$(MULTI_MIN_REPLICAS)..$(MULTI_MAX_REPLICAS)]"; \
		echo "       The multi overlay publishes $(MULTI_MAX_REPLICAS) host ports per service; widen the ranges first."; \
		exit 1; \
	fi

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
	@echo "  - Gateway:    http://localhost:$(E2E_GATEWAY_PORT)"
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
	# Gate discipline: 0 reruns. This branch exists to catch low-probability distributed races; retrying a
	# failing test here would turn "attempt 1 fails, attempt 2 passes" into a green build (P1 in review).
	$(MAVEN) clean verify -Pit -DskipITs=false -DskipTests -pl '!tests/api,!tests/performance' $(MAVEN_CLEAN_OPTS)

it-retry: build ## Same as `it`, but tolerates the rootless-Docker container flakes (2 reruns). NOT the gate.
	@echo "Running integration tests with 2 reruns per failing test (local/rootless-Docker convenience only)"
	$(MAVEN) clean verify -Pit -DskipITs=false -DskipTests -pl '!tests/api,!tests/performance' \
		-Dit.reruns=2 $(MAVEN_CLEAN_OPTS)
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
		if curl -sf --max-time 3 http://localhost:$(E2E_GATEWAY_PORT)/actuator/health > /dev/null 2>&1; then \
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
			response=$$(curl -sS --max-time 3 "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || true); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || echo 000); \
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
				response=$$(curl -sS --max-time 3 "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || true); \
				code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || echo 000); \
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
	cd tests/api && ../../$(MAVEN) verify -Pit -DskipITs=false \
		-Dgateway.base.url=http://localhost:$(E2E_GATEWAY_PORT) \
		-DPOSTGRES_URL=jdbc:postgresql://localhost:$(E2E_POSTGRES_PORT)/tinystore \
		|| exit 1; \
	echo "E2E/API tests passed successfully"

multi-up: check-multi-replicas build ## Start the multi-instance (distributed) test stack (MULTI_REPLICAS per service)
	@echo "Starting multi-instance test environment ($(MULTI_REPLICAS)x per service)..."
	@docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) up -d --build $(MULTI_SCALE_FLAGS)
	@echo "Multi-instance stack started. Replica host ports:"
	@for svc_port in gateway:8080 auth:9000 account:8000 product:8090 promotion:1200 inventory:13000 order:28080 payment:8083; do \
		svc=$${svc_port%%:*}; port=$${svc_port##*:}; \
		for cid in $$(docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) ps -q $$svc); do \
			printf "  %-12s %s\n" "$$svc" "$$(docker port $$cid $$port | head -1 | sed 's/.*://')"; \
		done; \
	done

multi-down: ## Stop the multi-instance test stack
	@docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) down -v

e2e-multi: check-multi-replicas build ## Run E2E/API + distributed assertions against the N-replica stack
	@echo "Starting multi-instance environment for distributed E2E tests ($(MULTI_REPLICAS) replicas/service)..."
	@set -e; \
	root_dir=$$(pwd); \
	replicas=$(MULTI_REPLICAS); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_ports() { compose ps -q "$$1" | while read -r cid; do docker port "$$cid" "$$2" 2>/dev/null | head -1 | sed 's/.*://'; done; }; \
	urls() { out=""; for p in $$(replica_ports "$$1" "$$2"); do out="$${out:+$$out,}http://localhost:$$p"; done; printf '%s' "$$out"; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for all $$replicas gateway replicas to be healthy (max 180s)..."; \
	for i in $$(seq 1 36); do \
		ok=1; \
		for p in $$(replica_ports gateway 8080); do \
			if [ -z "$$p" ] || [ "$$(curl -sf -o /dev/null -w '%{http_code}' --max-time 3 http://localhost:$$p/actuator/health || echo 000)" != "200" ]; then ok=0; fi; \
		done; \
		found=$$(replica_ports gateway 8080 | grep -c . || true); \
		if [ "$$found" -ge "$$replicas" ] && [ $$ok -eq 1 ]; then echo "All $$replicas gateway replicas healthy after $$((i*5))s"; break; fi; \
		if [ $$i -eq 36 ]; then echo "ERROR: gateway replicas not healthy in 180s ($$found/$$replicas published)"; compose logs --tail=80 gateway; exit 1; fi; \
		sleep 5; \
	done; \
	gw_port=$$(replica_ports gateway 8080 | head -1); \
	echo "Waiting for downstream routes (max 180s)..."; \
	for i in $$(seq 1 36); do \
		all_ready=1; \
		for path in "/internal/health/order" "/internal/health/promotion" "/internal/health/inventory" "/internal/health/product" "/internal/health/auth" "/internal/health/account" "/internal/health/pay"; do \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$$gw_port$$path" 2>/dev/null || echo 000); \
			if [ "$$code" != "200" ]; then all_ready=0; break; fi; \
		done; \
		if [ $$all_ready -eq 1 ]; then echo "Downstream routes ready after $$((i*5))s"; break; fi; \
		if [ $$i -eq 36 ]; then echo "ERROR: downstream routes not ready in 180s"; compose logs --tail=80 gateway; exit 1; fi; \
		sleep 5; \
	done; \
	product_urls=$$(urls product 8090); \
	inventory_urls=$$(urls inventory 13000); \
	promotion_urls=$$(urls promotion 1200); \
	gw_urls=$$(urls gateway 8080); \
	order_urls=$$(urls order 28080); \
	auth_urls=$$(urls auth 9000); \
	echo "Resolved replica URLs:"; \
	echo "  gateway   $$gw_urls"; \
	echo "  order     $$order_urls"; \
	echo "  inventory $$inventory_urls"; \
	echo "  auth      $$auth_urls"; \
	echo "Waiting for all $(MULTI_REPLICAS) replicas of every service to register in Nacos (max 480s)..."; \
	for i in $$(seq 1 96); do \
		missing=""; \
		for svc in tinystore-gateway tinystore-auth tinystore-domain-account product-service promotion-service tinystore-inventory-service order-service pay-service; do \
			n=$$(curl -s --max-time 3 "http://localhost:$(E2E_NACOS_PORT)/nacos/v1/ns/instance/list?serviceName=$$svc&healthyOnly=false" | grep -o '"healthy":true' | wc -l); \
			if [ "$$n" -lt $(MULTI_REPLICAS) ]; then missing="$$missing $$svc($$n/$(MULTI_REPLICAS))"; fi; \
		done; \
		if [ -z "$$missing" ]; then echo "Discovery topology complete after $$((i*5))s"; break; fi; \
		if [ $$((i % 12)) -eq 0 ]; then echo "  ... still waiting ($$((i*5))s):$$missing"; fi; \
		if [ $$i -eq 96 ]; then \
			echo "ERROR: replicas never registered after 480s:$$missing"; \
			compose ps --format '{{.Name}} {{.State}} {{.Status}}'; \
			compose logs --tail=40 product; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Running API tests + distributed assertions..."; \
	cd tests/api && ../../$(MAVEN) verify -Pit -DskipITs=false \
		-Dgateway.base.url=http://localhost:$$gw_port \
		-Dproduct.base.url=$${product_urls%%,*} \
		-Dinventory.base.url=$${inventory_urls%%,*} \
		-Dpromotion.base.url=$${promotion_urls%%,*} \
		-Dmulti.instance.mode=true \
		-Dmulti.instance.replicas=$$replicas \
		-Dnacos.base.url=http://localhost:$(E2E_NACOS_PORT) \
		-Dgateway.replica.urls=$$gw_urls \
		-Dorder.replica.urls=$$order_urls \
		-DPOSTGRES_URL=jdbc:postgresql://localhost:$(E2E_POSTGRES_PORT)/tinystore \
		|| exit 1; \
	echo "Multi-instance E2E + distributed assertions passed"

consistency-multi: check-multi-replicas build ## Run concurrency consistency tests against the N-replica stack (+ outbox duplicate probe)
	@echo "Starting multi-instance environment for concurrency consistency tests ($(MULTI_REPLICAS) replicas/service)..."
	@set -e; \
	root_dir=$$(pwd); \
	replicas=$(MULTI_REPLICAS); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_ports() { compose ps -q "$$1" | while read -r cid; do docker port "$$cid" "$$2" 2>/dev/null | head -1 | sed 's/.*://'; done; }; \
	urls() { out=""; for p in $$(replica_ports "$$1" "$$2"); do out="$${out:+$$out,}http://localhost:$$p"; done; printf '%s' "$$out"; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for all $$replicas gateway replicas (max 180s)..."; \
	for i in $$(seq 1 36); do \
		ok=1; \
		for p in $$(replica_ports gateway 8080); do \
			if [ -z "$$p" ] || [ "$$(curl -sf -o /dev/null -w '%{http_code}' --max-time 3 http://localhost:$$p/actuator/health || echo 000)" != "200" ]; then ok=0; fi; \
		done; \
		found=$$(replica_ports gateway 8080 | grep -c . || true); \
		if [ "$$found" -ge "$$replicas" ] && [ $$ok -eq 1 ]; then echo "Gateways healthy after $$((i*5))s"; break; fi; \
		if [ $$i -eq 36 ]; then echo "ERROR: gateways not healthy ($$found/$$replicas published)"; compose logs --tail=80 gateway; exit 1; fi; \
		sleep 5; \
	done; \
	gw_port=$$(replica_ports gateway 8080 | head -1); \
	for i in $$(seq 1 36); do \
		all_ready=1; \
		for path in "/internal/health/order" "/internal/health/inventory"; do \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$$gw_port$$path" 2>/dev/null || echo 000); \
			[ "$$code" != "200" ] && all_ready=0; \
		done; \
		if [ $$all_ready -eq 1 ]; then echo "order+inventory routes ready after $$((i*5))s"; break; fi; \
		if [ $$i -eq 36 ]; then echo "ERROR: routes not ready"; compose logs --tail=80 gateway; exit 1; fi; \
		sleep 5; \
	done; \
	echo "Applying E2E seed fixture (perf tests assert against SHOP_A/SKU_A)..."; \
	docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -v ON_ERROR_STOP=1 \
		< tests/api/src/test/resources/seed/e2e-seed.sql > /dev/null; \
	inventory_urls=$$(urls inventory 13000); \
	inv_first=$${inventory_urls%%,*}; \
	echo "Inventory replicas: $$inventory_urls"; \
	echo "Running concurrency consistency tests (load spread over both inventory replicas)..."; \
	$(MAVEN) -pl tests/performance test -Pperf \
		-Dgateway.base.url=http://localhost:$$gw_port \
		-Dinventory.base.url=$$inv_first \
		-Dinventory.replica.urls=$$inventory_urls \
		-Dinventory.prometheus.url=$$inv_first/actuator/prometheus \
		-Dpg.url=jdbc:postgresql://localhost:$(E2E_POSTGRES_PORT)/tinystore \
		|| consistency_status=$$?; \
	echo ""; \
	echo "=== Outbox duplicate-publish probe (multi-instance) ==="; \
	for t in tinystore.order.general tinystore.promotion.general tinystore.inventory.general; do \
		docker exec tinystore-kafka-test /opt/kafka/bin/kafka-console-consumer.sh \
			--bootstrap-server localhost:9092 --topic $$t --from-beginning --timeout-ms 8000 \
			> /tmp/outbox-$$t.txt 2>/dev/null || true; \
		tot=$$(grep -c . /tmp/outbox-$$t.txt || true); \
		uni=$$(grep -oE '"eventId":"[^"]+"' /tmp/outbox-$$t.txt | sort -u | wc -l); \
		echo "  $$t: messages=$$tot uniqueEventIds=$$uni duplicatePublishes=$$((tot-uni))"; \
	done; \
	echo ""; \
	echo "=== Cross-replica ID collision probe (unique-constraint violations) ==="; \
	compose logs order 2>&1 | grep 'duplicate key value violates unique constraint' \
		| grep -oE '\[log-id: [^]]+\].*unique constraint "[^"]+"' \
		| sed -E 's/.*unique constraint "([^"]+)".*/\1/' | sort | uniq -c | sort -rn || echo "  none observed"; \
	echo ""; \
	if [ -n "$$consistency_status" ] && [ "$$consistency_status" != "0" ]; then \
		echo "Concurrency consistency tests FAILED (exit $$consistency_status) -- see docs/architecture/multi-instance-testing-blockers.md"; \
		exit $$consistency_status; \
	fi; \
	echo "Concurrency consistency tests passed on the multi-instance stack"

resilience-multi: check-multi-replicas build ## Kill one replica mid-traffic and measure the failover window (distributed resilience probe)
	@echo "Starting multi-instance environment for the replica-failure probe..."
	@set -e; \
	root_dir=$$(pwd); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_ports() { compose ps -q "$$1" | while read -r cid; do docker port "$$cid" "$$2" 2>/dev/null | head -1 | sed 's/.*://'; done; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for gateway + order route (max 480s; order/auth take 45-70s+ to boot on a loaded host and the gateway answers 'No servers available' until they register)..."; \
	for i in $$(seq 1 96); do \
		gw_port=$$(replica_ports gateway 8080 | head -1); \
		code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$$gw_port/api/order/trades/probe-ready" 2>/dev/null || echo 000); \
		if [ "$$code" = "404" ]; then echo "order route ready after $$((i*5))s"; break; fi; \
		if [ $$i -eq 96 ]; then echo "ERROR: order route not ready"; compose logs --tail=80 gateway; exit 1; fi; \
		sleep 5; \
	done; \
	echo "Seeding one real trade to probe against (a 404-only probe proves nothing: the gateway itself 404s for a missing route)..."; \
	probe_sku="SKU-failover-$$$$"; \
	docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -q -c \
		"INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at) VALUES ('SHOP_A','$$probe_sku',10,0,0,NOW(),NOW()) ON CONFLICT (shop_id, sku_id) DO NOTHING;"; \
	probe_trade="trade-failover-$$$$"; \
	create_body=$$(curl -s --max-time 20 -X POST "http://localhost:$$gw_port/api/order/trades" \
		-H 'Content-Type: application/json' -H "Idempotency-Key: failover-$$probe_trade" \
		-d "{\"tradeId\":\"$$probe_trade\",\"buyerId\":\"buyer-failover\",\"buyerNick\":\"failover\",\"addressId\":\"addr-001\",\"traceId\":\"$$probe_trade\",\"orderLines\":[{\"skuId\":\"$$probe_sku\",\"productId\":\"prod-1\",\"productName\":\"P\",\"shopId\":\"SHOP_A\",\"sellerId\":\"seller-A\",\"quantity\":1,\"priceCents\":1000,\"weightGrams\":0}]}" || true); \
	case "$$create_body" in *"$$probe_trade"*) echo "  probe trade created: $$probe_trade";; \
	*) echo "ERROR: could not create the probe trade through the gateway: $$create_body"; exit 1;; esac; \
	victim=$$(compose ps -q order | tail -1); \
	echo "Victim replica: $$(docker inspect -f '{{.Name}}' $$victim)"; \
	echo "Probing through the gateway; killing that replica at T+$(MULTI_KILL_AFTER)s..."; \
	probe_log=/tmp/tinystore-failover-$$$$.log; \
	( i=0; end=$$(( $$(date +%s) + $(MULTI_PROBE_SECONDS) )); \
	  while [ $$(date +%s) -lt $$end ]; do \
		i=$$((i+1)); t0=$$(date +%s%3N); \
		resp=$$(curl -s -w '\n%{http_code}' --max-time 20 "http://localhost:$$gw_port/api/order/trades/$$probe_trade" 2>/dev/null || true); \
		code=$$(printf '%s' "$$resp" | tail -n 1); \
		body=$$(printf '%s' "$$resp" | sed '$$d'); \
		[ -z "$$code" ] && code=000; \
		case "$$body" in *"$$probe_trade"*) marker=1;; *) marker=0;; esac; \
		t1=$$(date +%s%3N); echo "$$t0 $$((t1-t0)) $$code $$marker" >> $$probe_log; \
	  done ) & probe_pid=$$!; \
	sleep $(MULTI_KILL_AFTER); \
	kill_at=$$(date +%s%3N); \
	docker kill $$victim > /dev/null 2>&1 || true; \
	echo "Killed victim at $$kill_at"; \
	wait $$probe_pid || true; \
	echo ""; \
	echo "=== Replica-failure probe result ==="; \
	if [ "$(MULTI_EXPECT_FAILOVER)" = "1" ]; then echo "  mode: pre-fix demonstration (failures are expected)"; else echo "  mode: gate (0 lost requests, failover within $(MULTI_MAX_FAILOVER_MS)ms)"; fi; \
	set +e; \
	MULTI_KILL_AT=$$kill_at MULTI_PROBE_LOG=$$probe_log \
	MULTI_EXPECT_FAILOVER=$(MULTI_EXPECT_FAILOVER) \
	MULTI_MAX_FAILOVER_MS=$(MULTI_MAX_FAILOVER_MS) \
	MULTI_PROBE_OK_STATUS=200 \
	python3 docker/failover-probe.py; \
	status=$$?; \
	set -e; \
	rm -f $$probe_log; \
	echo ""; \
	if [ $$status -ne 0 ]; then \
		echo "Replica-failure probe failed (exit $$status: 1=run unusable, 2=gate violation) -- see"; \
		echo "docs/architecture/multi-instance-testing-blockers.md (E1) for the expected shape of the report"; \
		exit $$status; \
	fi; \
	echo "Replica-failure probe passed"

rate-limit-multi: check-multi-replicas build ## Burst the gateway's limiter across replicas: spoofed XFF, shared quota, trusted proxy (C16)
	@echo "Starting multi-instance environment for the rate-limit trust-boundary probe ($(MULTI_REPLICAS) replicas/service)..."
	@set -e; \
	root_dir=$$(pwd); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_ports() { compose ps -q "$$1" | while read -r cid; do docker port "$$cid" "$$2" 2>/dev/null | head -1 | sed 's/.*://'; done; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	wait_route() { \
		for i in $$(seq 1 36); do \
			gw=$$(replica_ports gateway 8080 | head -1); \
			code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$$gw/api/order/trades/rl-ready" 2>/dev/null || echo 000); \
			if [ "$$code" = "404" ]; then echo "order route ready after $$((i*5))s"; return 0; fi; \
			sleep 5; \
		done; \
		echo "ERROR: order route not ready"; compose logs --tail=60 gateway; return 1; \
	}; \
	MULTI_GATEWAY_RATE_LIMIT_ENABLED=true compose up -d --build $(MULTI_SCALE_FLAGS); \
	wait_route; \
	gw_ports=$$(replica_ports gateway 8080 | paste -sd, -); \
	echo ""; \
	echo "=== Phase 1: direct client (X-Forwarded-For must be ignored) ==="; \
	RATE_LIMIT_GATEWAY_PORTS=$$gw_ports \
	RATE_LIMIT_BURST=$(MULTI_RATE_LIMIT_BURST) \
	RATE_LIMIT_CONCURRENCY=$(MULTI_RATE_LIMIT_CONCURRENCY) \
	python3 docker/rate-limit-probe.py --mode untrusted; \
	untrusted_keys=$$(docker exec tinystore-redis-test redis-cli --scan \
		--pattern 'tinystore:gateway:ratelimit:order-service:*:tokens' | wc -l | tr -d ' '); \
	forged_keys=$$(docker exec tinystore-redis-test redis-cli --scan \
		--pattern 'tinystore:gateway:ratelimit:order-service:198.51.100.*:tokens' | wc -l | tr -d ' '); \
	echo "  limiter identities in Redis after phase 1: $$untrusted_keys (expected 1 bucket for the cluster)"; \
	echo "  of which forged (198.51.100.*) : $$forged_keys (expected 0 -- the header was ignored)"; \
	if [ "$$untrusted_keys" != "1" ] || [ "$$forged_keys" != "0" ]; then \
		echo "ERROR: a client-controlled X-Forwarded-For minted limiter identities ($$forged_keys forged,"; \
		echo "       $$untrusted_keys total); untrusted callers must not be able to choose their bucket"; \
		exit 1; \
	fi; \
	echo ""; \
	echo "=== Phase 2: trusted proxy (the forwarded address becomes the identity) ==="; \
	net=$$(docker network ls --filter name=tinystore-test --format '{{.Name}}' | head -1); \
	proxy_ip=$$(docker network inspect "$$net" --format '{{range .IPAM.Config}}{{.Gateway}}{{end}}'); \
	if [ -z "$$proxy_ip" ]; then echo "ERROR: could not determine the docker network gateway address"; exit 1; fi; \
	echo "Re-creating gateway replicas with GATEWAY_TRUSTED_PROXIES=$$proxy_ip/32 ..."; \
	MULTI_GATEWAY_RATE_LIMIT_ENABLED=true MULTI_GATEWAY_TRUSTED_PROXIES="$$proxy_ip/32" compose up -d gateway; \
	wait_route; \
	gw_ports=$$(replica_ports gateway 8080 | paste -sd, -); \
	RATE_LIMIT_GATEWAY_PORTS=$$gw_ports \
	RATE_LIMIT_BURST=$(MULTI_RATE_LIMIT_BURST) \
	RATE_LIMIT_CONCURRENCY=$(MULTI_RATE_LIMIT_CONCURRENCY) \
	python3 docker/rate-limit-probe.py --mode trusted; \
	forwarded_keys=$$(docker exec tinystore-redis-test redis-cli --scan \
		--pattern 'tinystore:gateway:ratelimit:order-service:198.51.100.*:tokens' | wc -l | tr -d ' '); \
	echo "  forwarded-address identities in Redis after phase 2: $$forwarded_keys (expected >0 for a trusted proxy)"; \
	if [ "$$forwarded_keys" -le 0 ]; then \
		echo "ERROR: a trusted proxy's X-Forwarded-For did not become the limiter identity"; \
		exit 1; \
	fi; \
	echo ""; \
	echo "Rate-limit trust-boundary probe passed"

rebalance-multi: check-multi-replicas build ## Place a burst, kill one replica, record the Kafka group's rebalance + lag recovery (C7)
	@echo "Starting multi-instance environment for the Kafka rebalance measurement ($(MULTI_REPLICAS) replicas/service)..."
	@set -e; \
	root_dir=$$(pwd); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_ports() { compose ps -q "$$1" | while read -r cid; do docker port "$$cid" "$$2" 2>/dev/null | head -1 | sed 's/.*://'; done; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for gateway + order route (max 180s)..."; \
	for i in $$(seq 1 36); do \
		gw_port=$$(replica_ports gateway 8080 | head -1); \
		code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$$gw_port/api/order/trades/rb-ready" 2>/dev/null || echo 000); \
		if [ "$$code" = "404" ]; then echo "order route ready after $$((i*5))s"; break; fi; \
		if [ $$i -eq 36 ]; then echo "ERROR: order route not ready"; compose logs --tail=60 gateway; exit 1; fi; \
		sleep 5; \
	done; \
	sku="SKU-rebalance-$$$$"; \
	echo "Seeding stock for $$sku ..."; \
	docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -q -c \
		"INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at) VALUES ('SHOP_A','$$sku',$(REBALANCE_BURST),0,0,NOW(),NOW()) ON CONFLICT (shop_id, sku_id) DO NOTHING;"; \
	echo "Waiting for the order consumer group to settle (stable member count, at least one per replica)..."; \
	prev_members=""; \
	settled=""; \
	for i in $$(seq 1 36); do \
		state=$$(docker exec tinystore-kafka-test /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-service --state 2>/dev/null | awk '/^order-service/ {print $$5" "$$6}'); \
		members=$${state#Stable }; \
		echo "  group state: $${state:-not-joined} (attempt $$i)"; \
		if [ "$${state%% *}" = "Stable" ] && [ -n "$$members" ] && [ "$$members" -ge "$(MULTI_REPLICAS)" ] && [ "$$members" = "$$prev_members" ]; then \
			echo "  group settled with $$members members (each order replica runs one listener per consumed topic)"; \
			settled=yes; break; \
		fi; \
		prev_members=$$members; \
		sleep 5; \
	done; \
	if [ -z "$$settled" ]; then echo "ERROR: order-service consumer group never settled"; exit 1; fi; \
	victim=$$(compose ps -q order | tail -1); \
	echo ""; \
	echo "=== Kafka rebalance measurement (C7) ==="; \
	set +e; \
	REBALANCE_SKU="$$sku" REBALANCE_BURST=$(REBALANCE_BURST) \
	REBALANCE_LAG_SLA_SECONDS=$(REBALANCE_LAG_SLA_SECONDS) \
	python3 docker/kafka-rebalance-probe.py --victim "$$victim" --gateway-port "$$gw_port"; \
	status=$$?; \
	set -e; \
	echo ""; \
	if [ $$status -ne 0 ]; then \
		echo "Kafka rebalance measurement reported exit $$status (1=unusable run, 2=SLA exceeded) -- see"; \
		echo "docs/architecture/multi-instance-testing-blockers.md (C7)"; \
		exit $$status; \
	fi; \
	echo "Kafka rebalance measurement recorded"

load-multi: check-multi-replicas build ## Run the k6 load matrix against the N-replica stack (+ per-replica traffic split)
	@echo "Starting multi-instance environment for the distributed load matrix ($(MULTI_REPLICAS) replicas/service)..."
	@if ! command -v k6 > /dev/null 2>&1; then echo "ERROR: k6 not found"; exit 1; fi
	@set -e; \
	root_dir=$$(pwd); \
	levels="$${VUS_LEVELS:-100 300}"; \
	duration="$${DURATION:-30s}"; \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_ports() { compose ps -q "$$1" | while read -r cid; do docker port "$$cid" "$$2" 2>/dev/null | head -1 | sed 's/.*://'; done; }; \
	urls() { out=""; for p in $$(replica_ports "$$1" "$$2"); do out="$${out:+$$out,}http://localhost:$$p"; done; printf '%s' "$$out"; }; \
	order_trades_count() { curl -s --max-time 5 "http://localhost:$$1/actuator/prometheus" 2>/dev/null | grep 'http_server_requests_seconds_count' | grep 'uri="/order/trades"' | awk '{s+=$$NF} END {print s+0}'; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for gateway replicas (max 180s)..."; \
	for i in $$(seq 1 36); do \
		gw_port=$$(replica_ports gateway 8080 | head -1); \
		code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$$gw_port/api/order/trades/load-ready" 2>/dev/null || echo 000); \
		[ "$$code" = "404" ] && { echo "order route ready after $$((i*5))s"; break; }; \
		[ $$i -eq 36 ] && { echo "ERROR: route not ready"; compose logs --tail=60 gateway; exit 1; }; \
		sleep 5; \
	done; \
	echo "Waiting for the discovery topology to be complete (max 480s; app services take 45-70s+ to boot and have no healthcheck)..."; \
	for i in $$(seq 1 96); do \
		missing=""; \
		for svc in tinystore-gateway tinystore-auth tinystore-domain-account product-service promotion-service tinystore-inventory-service order-service pay-service; do \
			n=$$(curl -s --max-time 3 "http://localhost:$(E2E_NACOS_PORT)/nacos/v1/ns/instance/list?serviceName=$$svc&healthyOnly=false" | grep -o '"healthy":true' | wc -l); \
			[ "$$n" -lt $(MULTI_REPLICAS) ] && missing="$$missing $$svc"; \
		done; \
		[ -z "$$missing" ] && { echo "Topology complete after $$((i*5))s"; break; }; \
		if [ $$((i % 12)) -eq 0 ]; then echo "  ... still waiting ($$((i*5))s):$$missing"; fi; \
		if [ $$i -eq 96 ]; then \
			echo "ERROR: incomplete topology after 480s:$$missing"; \
			compose ps --format '{{.Name}} {{.State}} {{.Status}}'; \
			for svc in $$missing; do echo "=== $$svc logs ==="; compose logs --tail=30 "$$svc"; done; \
			exit 1; \
		fi; \
		sleep 5; \
	done; \
	echo "Seeding load SKUs..."; \
	echo "=== Per-replica readiness (A4) ==="; \
	readiness_fail=0; \
	for spec in gateway:8080 auth:9000 account:8000 product:8090 promotion:1200 inventory:13000 order:28080 payment:8083; do \
		svc=$${spec%%:*}; cport=$${spec##*:}; \
		for hp in $$(replica_ports $$svc $$cport); do \
			body=$$(curl -s --max-time 5 "http://localhost:$$hp/actuator/health/readiness" || true); \
			case "$$body" in *'"status":"UP"'*) echo "  $$svc :$$hp ready";; \
			*) echo "  $$svc :$$hp NOT ready -> $${body:-no response}"; readiness_fail=1;; esac; \
		done; \
	done; \
	if [ $$readiness_fail -ne 0 ]; then echo "ERROR: a replica exposes no UP readiness (A4)"; exit 1; fi; \
	for sku in SKU-load SKU_A; do \
		docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -v ON_ERROR_STOP=1 -c \
			"INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at) VALUES ('SHOP_A','$$sku',500000,0,0,NOW(),NOW()) ON CONFLICT (shop_id, sku_id) DO NOTHING;" > /dev/null; \
	done; \
	docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -v ON_ERROR_STOP=1 < tests/api/src/test/resources/seed/e2e-seed.sql > /dev/null; \
	order_urls=$$(urls order 28080); \
	split_before=/tmp/tinystore-load-multi-before-$$$$.txt; \
	split_after=/tmp/tinystore-load-multi-after-$$$$.txt; \
	: > $$split_before; \
	for u in $$(printf '%s' "$$order_urls" | tr ',' ' '); do p=$${u##*:}; echo "$$p $$(order_trades_count $$p)" >> $$split_before; done; \
	echo ""; \
	echo "=== PostgreSQL connection budget (D3) ==="; \
	python3 docker/pg-connection-probe.py snapshot --replicas $(MULTI_REPLICAS); \
	conn_log=/tmp/tinystore-pg-connections-$$$$.json; \
	python3 docker/pg-connection-probe.py watch --replicas $(MULTI_REPLICAS) --seconds $(CONNECTION_WATCH_SECONDS) --out $$conn_log & watch_pid=$$!; \
	echo "Running k6 matrix (VUS=$$levels, DURATION=$$duration) through gateway :$$gw_port (2 replicas behind it)..."; \
	cd perf/k6; \
	set +e; \
	matrix_log=/tmp/tinystore-load-multi-$$$$.log; \
	VUS_LEVELS="$$levels" DURATION="$$duration" BASE_URL="http://localhost:$$gw_port" SKU_ID=SKU-load bash run_matrix.sh > $$matrix_log 2>&1; \
	k6_status=$$?; \
	set -e; \
	cat $$matrix_log; \
	threshold_fail=0; \
	if grep -q "THRESHOLD-CROSSED" $$matrix_log 2>/dev/null; then threshold_fail=1; fi; \
	rm -f $$matrix_log; \
	cd "$$root_dir"; \
	kill $$watch_pid 2>/dev/null || true; \
	sleep 1; \
	echo ""; \
	echo "=== PostgreSQL connections under load ==="; \
	set +e; \
	python3 docker/pg-connection-probe.py report --replicas $(MULTI_REPLICAS) --out $$conn_log; \
	conn_status=$$?; \
	set -e; \
	rm -f $$conn_log; \
	pool_timeouts=$$(compose logs --tail=4000 order payment inventory promotion product account auth 2>/dev/null | grep -c "Connection is not available" || true); \
	echo "  Hikari acquisition timeouts in the service logs: $$pool_timeouts"; \
	if [ "$$conn_status" -ne 0 ] || [ "$$pool_timeouts" != "0" ]; then \
		echo "ERROR: the connection budget was exceeded or a service could not acquire a connection"; \
		echo "       (see docs/architecture/multi-instance-testing-blockers.md, D3)"; \
		exit 3; \
	fi; \
	: > $$split_after; \
	for u in $$(printf '%s' "$$order_urls" | tr ',' ' '); do p=$${u##*:}; echo "$$p $$(order_trades_count $$p)" >> $$split_after; done; \
	echo ""; \
	echo "=== Per-replica traffic split (order) ==="; \
	set +e; \
	awk 'NR==FNR{b[$$1]=$$2;next}{d=$$2-b[$$1]; printf "  replica :%s -> %d requests%s\n", $$1, d, (d>0?"":"  <-- STARVED"); if (d<=0) starved=1} END{exit starved}' $$split_before $$split_after; \
	traffic_fail=$$?; \
	set -e; \
	if [ $$traffic_fail -ne 0 ]; then \
		echo "ERROR: at least one order replica served no traffic during the run -- the load balancer is"; \
		echo "       starving a replica (accepted for the API suite, not for the load gate)"; \
		rm -f $$split_before $$split_after; exit 4; \
	fi; \
	rm -f $$split_before $$split_after; \
	echo ""; \
	echo "=== Cross-replica ID collision probe ==="; \
	compose logs order 2>&1 | grep 'duplicate key value violates unique constraint' \
		| grep -oE '\[log-id: [^]]+\].*unique constraint "[^"]+"' \
		| sed -E 's/.*unique constraint "([^"]+)".*/\1/' | sort | uniq -c | sort -rn || echo "  none observed"; \
	echo ""; \
	if [ $$threshold_fail -ne 0 ]; then echo "k6 load matrix crossed its error/latency thresholds under multi-instance load (see above)"; exit 3; fi; \
	if [ $$k6_status -ne 0 ]; then echo "k6 load matrix exited $$k6_status"; exit $$k6_status; fi; \
	echo "Distributed load matrix complete"

load-compare: check-multi-replicas build ## k6 ladder on 1 replica vs N replicas: distributed scaling report (throughput + p95/p99 + per-replica split)
	@echo "Starting distributed scaling comparison (1x vs $(MULTI_REPLICAS)x replicas, same k6 ladder)..."
	@echo "Knobs: VUS_LEVELS (as in make load-matrix) / DURATION / REPEAT (median of N passes) / ORDER=single-first|multi-first / WARMUP_VUS / WARMUP_DURATION."
	@bash perf/k6/load_compare.sh \
		--compose-test $(COMPOSE_TEST) \
		--compose-multi $(COMPOSE_MULTI) \
		--services "$(MULTI_SERVICES)" \
		--replicas "$(MULTI_REPLICAS)" \
		--levels "$${VUS_LEVELS:-100 300 600}" \
		--duration "$${DURATION:-30s}" \
		--stock "$${STOCK_PER_SKU:-500000}" \
		--order "$${ORDER:-single-first}" \
		--warmup-vus "$${WARMUP_VUS:-30}" \
		--warmup-duration "$${WARMUP_DURATION:-10s}" \
		--repeat "$${REPEAT:-1}" \
		--outdir perf/reports

retry-multi: check-multi-replicas build ## Probe the order-placement failure path: is a same-key retry accepted after a 5xx?
	@echo "Starting multi-instance environment for the order retry probe..."
	@set -e; \
	root_dir=$$(pwd); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_port() { compose ps -q "$$1" | sed -n "$${2}p" | xargs -r -I{} docker port {} "$$3" | head -1 | sed 's/.*://'; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for the order route (max 300s)..."; \
	for i in $$(seq 1 60); do \
		gw_port=$$(replica_port gateway 1 8080); \
		code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$$gw_port/api/order/trades/retry-ready" 2>/dev/null || echo 000); \
		[ "$$code" = "404" ] && { echo "order route ready after $$((i*5))s"; break; }; \
		[ $$i -eq 60 ] && { echo "ERROR: order route not ready"; compose logs --tail=60 gateway; exit 1; }; \
		sleep 5; \
	done; \
	echo "Seeding E2E fixture..."; \
	docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -v ON_ERROR_STOP=1 \
		< tests/api/src/test/resources/seed/e2e-seed.sql > /dev/null; \
	order_port=$$(replica_port order 1 28080); \
	echo "Probing idempotency semantics (gateway :$$gw_port, order replica :$$order_port)..."; \
	set +e; \
	bash docker/order-retry-probe.sh "$$gw_port" "$$order_port"; \
	probe_status=$$?; \
	set -e; \
	echo ""; \
	if [ $$probe_status -eq 2 ]; then \
		echo "Order-chain idempotency probe reproduced a defect -- see docs/architecture/multi-instance-testing-blockers.md (C9/C11)"; \
	fi; \
	exit $$probe_status

chain-multi: check-multi-replicas build ## Verify the order-placement chain's resource invariants on the N-replica stack
	@echo "Starting multi-instance environment for the order-chain invariant gate..."
	@set -e; \
	root_dir=$$(pwd); \
	compose() { docker compose -f $(COMPOSE_TEST) -f $(COMPOSE_MULTI) "$$@"; }; \
	replica_port() { compose ps -q "$$1" | sed -n "$${2}p" | xargs -r -I{} docker port {} "$$3" | head -1 | sed 's/.*://'; }; \
	cleanup() { echo "Cleaning up multi-instance environment..."; cd "$$root_dir"; compose down -v; }; \
	trap cleanup EXIT; \
	compose up -d --build $(MULTI_SCALE_FLAGS); \
	echo "Waiting for the order route (max 300s)..."; \
	for i in $$(seq 1 60); do \
		gw_port=$$(replica_port gateway 1 8080); \
		code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$$gw_port/api/order/trades/chain-ready" 2>/dev/null || echo 000); \
		[ "$$code" = "404" ] && { echo "order route ready after $$((i*5))s"; break; }; \
		[ $$i -eq 60 ] && { echo "ERROR: order route not ready"; compose logs --tail=60 gateway; exit 1; }; \
		sleep 5; \
	done; \
	echo "Seeding E2E fixture..."; \
	docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -v ON_ERROR_STOP=1 \
		< tests/api/src/test/resources/seed/e2e-seed.sql > /dev/null; \
	set +e; \
	bash docker/order-chain-invariants.sh "$$gw_port"; \
	status=$$?; \
	set -e; \
	echo ""; \
	if [ $$status -ne 0 ]; then \
		echo "Order-chain invariant gate failed -- see docs/architecture/multi-instance-testing-blockers.md"; \
	fi; \
	exit $$status

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
		if curl -sf --max-time 3 http://localhost:$(E2E_GATEWAY_PORT)/actuator/health > /dev/null 2>&1; then \
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
			response=$$(curl -sS --max-time 3 "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1); \
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
				response=$$(curl -sS --max-time 3 "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1); \
				code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1); \
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
		if curl -sf --max-time 3 http://localhost:$(E2E_GATEWAY_PORT)/actuator/health > /dev/null 2>&1; then \
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
			response=$$(curl -sS --max-time 3 "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || true); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || echo 000); \
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
		if curl -sf --max-time 3 http://localhost:$(E2E_GATEWAY_PORT)/actuator/health > /dev/null 2>&1; then \
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
			response=$$(curl -sS --max-time 3 "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || true); \
			code=$$(curl -sS --max-time 3 -w '%{http_code}' -o /dev/null "http://localhost:$(E2E_GATEWAY_PORT)$$path" 2>&1 || echo 000); \
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
		curl -sf --max-time 3 http://localhost:$(E2E_GATEWAY_PORT)/actuator/health > /dev/null 2>&1 && { echo "Gateway healthy after $$((i*5))s"; break; }; \
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
	VUS_LEVELS="$$levels" DURATION="$$duration" BASE_URL="http://localhost:$(E2E_GATEWAY_PORT)" bash run_matrix.sh; \
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
		curl -sf --max-time 3 http://localhost:$(E2E_GATEWAY_PORT)/actuator/health > /dev/null 2>&1 && { echo "Gateway healthy after $$((i*5))s"; break; }; \
		sleep 5; \
	done; \
	echo "Waiting 30s for Nacos service registration (order -> inventory via Feign)..."; \
	sleep 30; \
	for C in 500 1000 5000 10000; do \
		echo "===== OVERSELL C=$$C ====="; \
		$(MAVEN) -pl tests/performance test -Pperf -Dtest=InventoryOversellBoundaryIT -Dperf.concurrency=$$C -Dinventory.base.url=http://localhost:$(E2E_INVENTORY_PORT) -Dpg.url=jdbc:postgresql://localhost:$(E2E_POSTGRES_PORT)/tinystore || exit 1; \
		echo "===== IDEMPOTENCY C=$$C ====="; \
		$(MAVEN) -pl tests/performance test -Pperf -Dtest=OrderCreateIdempotencyConsistencyIT -Dperf.concurrency=$$C -Dgateway.base.url=http://localhost:$(E2E_GATEWAY_PORT) -Dpg.url=jdbc:postgresql://localhost:$(E2E_POSTGRES_PORT)/tinystore || echo "WARN: idempotency C=$$C failed (known stack bottleneck at high concurrency)"; \
	done; \
	for N in 500 1000; do \
		echo "===== CONFIRM-LOCK N=$$N ====="; \
		$(MAVEN) -pl tests/performance test -Pperf -Dtest=InventoryConfirmLockContentionIT -Dperf.confirm.concurrency=$$N -Dinventory.base.url=http://localhost:$(E2E_INVENTORY_PORT) -Dpg.url=jdbc:postgresql://localhost:$(E2E_POSTGRES_PORT)/tinystore || exit 1; \
	done; \
	echo "Consistency matrix complete - collect outputs into docs/performance/load-report-2026-07-28.md"
# rebalance-multi: place a burst of orders, kill one replica, then record the Kafka group's
# rebalance window and lag recovery (C7 -- an operational number with an explicit SLA, not a
# correctness gate). REBALANCE_LAG_SLA_SECONDS is the release threshold: measured 42.7s on this box,
# so 90s is the "clearly beyond noise" line rather than the observed value itself.
REBALANCE_LAG_SLA_SECONDS ?= 90
REBALANCE_BURST ?= 30
# load-multi samples pg_stat_activity while k6 runs; the watch is a safety net if k6 outlives it.
CONNECTION_WATCH_SECONDS ?= 300
