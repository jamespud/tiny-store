package com.github.spud.tinystore.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(classes = InventoryApplication.class)
class StockAppServiceIT {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1")
			.withDatabaseName("tinystore")
			.withUsername("postgres")
			.withPassword("postgres");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
			.withExposedPorts(6379);

	@Container
	static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
		.withEnv("KAFKA_PROCESS_ROLES", "broker,controller");

	@DynamicPropertySource
	static void registerProps(DynamicPropertyRegistry registry) {
		// PostgreSQL
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl());
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		
		// Redis
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
		
		// Kafka
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
	}

	@Autowired
	private StockAppService stockAppService;

	@Autowired
	private JpaInventoryStockRepository stockRepository;

	@Autowired
	private JpaInventoryReservationRepository reservationRepository;

	@Autowired
	private JpaInventoryAdjustmentRepository adjustmentRepository;

	@BeforeEach
	void cleanup() {
		stockRepository.deleteAll();
		reservationRepository.deleteAll();
		adjustmentRepository.deleteAll();
	}

	@Test
	void preOccupy_isIdempotent() {
		stockRepository.save(new InventoryStockEntity()
				.setShopId("T1")
				.setSkuId("SKU1")
				.setTotalQuantity(10)
				.setReservedQuantity(0));

		StockPreOccupyRequest req = new StockPreOccupyRequest();
		req.setShopId("T1");
		req.setTradeId("O1");
		req.setExpiresAtEpochMs(Instant.now().plusSeconds(600).toEpochMilli());
		StockPreOccupyRequest.Line l = new StockPreOccupyRequest.Line();
		l.setSkuId("SKU1");
		l.setQuantity(3);
		req.setLines(List.of(l));

		String idem = UUID.randomUUID().toString();
		var resp1 = stockAppService.preOccupy(idem, req);
		var resp2 = stockAppService.preOccupy(idem, req);

		assertThat(resp1.isSuccess()).isTrue();
		assertThat(resp2.isSuccess()).isTrue();
		assertThat(resp1.getPreOccupyIds()).isEqualTo(resp2.getPreOccupyIds());

		InventoryStockEntity stock = stockRepository.findByShopIdAndSkuId("T1", "SKU1").orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(3);
	}

	@Test
	void expireReservations_releasesReservedQuantity() {
		stockRepository.save(new InventoryStockEntity()
				.setShopId("T1")
				.setSkuId("SKU2")
				.setTotalQuantity(10)
				.setReservedQuantity(4));

		reservationRepository.save(new InventoryReservationEntity()
				.setReservationId(UUID.randomUUID().toString().replace("-", ""))
				.setShopId("T1")
				.setSkuId("SKU2")
				.setQuantity(4)
				.setStatus(StockAppService.STATUS_RESERVED)
				.setExpireAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
				.setTradeId("O2")
				.setOperationId(UUID.randomUUID().toString()));

		int expired = stockAppService.expireReservations();
		assertThat(expired).isEqualTo(1);

		InventoryStockEntity stock = stockRepository.findByShopIdAndSkuId("T1", "SKU2").orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(0);
	}

	@Test
	void restock_isIdempotentPerRefundId() {
		stockRepository.save(new InventoryStockEntity()
				.setShopId("T1")
				.setSkuId("SKU1")
				.setTotalQuantity(10)
				.setReservedQuantity(0));
		stockRepository.save(new InventoryStockEntity()
				.setShopId("T1")
				.setSkuId("SKU2")
				.setTotalQuantity(20)
				.setReservedQuantity(0));

		StockRestockRequest req = new StockRestockRequest();
		req.setShopId("T1");
		req.setTradeId("O3");
		req.setRefundId("R1");
		StockRestockRequest.Line i1 = new StockRestockRequest.Line();
		i1.setSkuId("SKU1");
		i1.setQuantity(2);
		StockRestockRequest.Line i2 = new StockRestockRequest.Line();
		i2.setSkuId("SKU2");
		i2.setQuantity(3);
		req.setItems(List.of(i1, i2));

		var r1 = stockAppService.restock(UUID.randomUUID().toString(), req);
		var r2 = stockAppService.restock(UUID.randomUUID().toString(), req);
		assertThat(r1.isSuccess()).isTrue();
		assertThat(r2.isSuccess()).isTrue();

		InventoryStockEntity s1 = stockRepository.findByShopIdAndSkuId("T1", "SKU1").orElseThrow();
		InventoryStockEntity s2 = stockRepository.findByShopIdAndSkuId("T1", "SKU2").orElseThrow();
		assertThat(s1.getTotalQuantity()).isEqualTo(12);
		assertThat(s2.getTotalQuantity()).isEqualTo(23);
		assertThat(adjustmentRepository.countByReasonAndReferenceId(StockAppService.ADJUST_REASON_RESTOCK_REFUND, "R1"))
				.isEqualTo(1);
	}
}
