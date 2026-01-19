package com.github.spud.tinystore.order.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.OrderApplication;
import com.github.spud.tinystore.order.infrastructure.acl.ProductClient;
import com.github.spud.tinystore.order.infrastructure.event.publisher.OutboxEventPublisher;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderMainEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderMainJpaRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(classes = OrderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class OrderMainFlowE2EIT {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
		.withDatabaseName("tinystore")
		.withUsername("postgres")
		.withPassword("postgres");

	@Container
	static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

	@DynamicPropertySource
	static void registerProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?currentSchema=tinystore_order");
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@MockBean
	private ProductClient productClient;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OutboxEventPublisher outboxEventPublisher;

	@Autowired
	private OrderMainJpaRepository orderMainJpaRepository;

	private Consumer<String, String> consumer;

	@BeforeEach
	void setUp() {
		ProductClient.InternalSkuDTO sku1 = new ProductClient.InternalSkuDTO();
		sku1.setSkuId("SKU1");
		sku1.setMerchantId("M1");
		sku1.setAvailable(true);
		sku1.setUnitPrice(100);
		sku1.setPromotePrice(100);
		sku1.setSkuName("S1");
		sku1.setSpecJson("{}");

		ProductClient.InternalSkuDTO sku2 = new ProductClient.InternalSkuDTO();
		sku2.setSkuId("SKU2");
		sku2.setMerchantId("M1");
		sku2.setAvailable(true);
		sku2.setUnitPrice(200);
		sku2.setPromotePrice(200);
		sku2.setSkuName("S2");
		sku2.setSpecJson("{}");

		ProductClient.InternalSkuBatchQueryResponse resp = new ProductClient.InternalSkuBatchQueryResponse();
		Map<String, ProductClient.InternalSkuDTO> skuMap = new HashMap<>();
		skuMap.put("SKU1", sku1);
		skuMap.put("SKU2", sku2);
		resp.setSkuMap(skuMap);
		when(productClient.batchGetSkuInfo(any(), any(), any(), any())).thenReturn(resp);

		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		this.consumer = new KafkaConsumer<>(props);
		this.consumer.subscribe(Pattern.compile("tinystore\\.order\\..+"));
	}

	@AfterEach
	void tearDown() {
		if (consumer != null) {
			consumer.close();
		}
	}

	@Test
	void shouldPersistOrderAndPublishKafkaEvents() throws Exception {
		String tenantId = "T1";
		String userId = "temp";

		String submitBody = """
			{
			  \"addressId\": \"ADDR-1\",
			  \"platformCouponId\": null,
			  \"merchantSkuGroups\": [
			    {
			      \"merchantId\": \"M1\",
			      \"merchantCouponId\": null,
			      \"skuItems\": [
			        { \"skuId\": \"SKU1\", \"quantity\": 2 },
			        { \"skuId\": \"SKU2\", \"quantity\": 1 }
			      ]
			    }
			  ]
			}
			""";

		MvcResult submitResult = mockMvc.perform(
				MockMvcRequestBuilders.post("/order/v1/order/user/submit/apply")
					.contentType("application/json")
					.header("X-Tenant-Id", tenantId)
					.header("X-User-Id", userId)
					.header("X-Trace-Id", "trace-1")
					.content(submitBody)
			)
			.andExpect(MockMvcResultMatchers.status().isOk())
			.andReturn();

		JsonNode submitJson = objectMapper.readTree(submitResult.getResponse().getContentAsString());
		String mainOrderNo = submitJson.path("data").path("mainOrderNo").asText();
		assertThat(mainOrderNo).isNotBlank();

		outboxEventPublisher.publishPendingEvents();
		assertThat(awaitKafkaEvent("tinystore.order.created", "order.created", Duration.ofSeconds(10))).isTrue();

		String paymentBody = """
			{
			  \"orderId\": \"%s\",
			  \"payType\": \"FULL\",
			  \"payAmount\": 4.00,
			  \"paidAt\": 1710000000000,
			  \"eventId\": \"EVT-PAY-1\",
			  \"paymentId\": \"PAY-1\"
			}
			""".formatted(mainOrderNo);

		mockMvc.perform(
				MockMvcRequestBuilders.post("/order/v1/order/internal/payment/success")
					.contentType("application/json")
					.header("X-Tenant-Id", tenantId)
					.header("X-User-Id", userId)
					.header("X-Trace-Id", "trace-2")
					.content(paymentBody)
			)
			.andExpect(MockMvcResultMatchers.status().isOk());

		outboxEventPublisher.publishPendingEvents();
		assertThat(awaitKafkaEvent("tinystore.order.paid", "order.payment.succeeded", Duration.ofSeconds(10))).isTrue();

		String acceptBody = """
			{
			  \"orderId\": \"%s\",
			  \"operatorId\": \"OP-M1\",
			  \"idempotencyKey\": \"EVT-ACC-1\"
			}
			""".formatted(mainOrderNo);

		mockMvc.perform(
				MockMvcRequestBuilders.post("/order/v1/order/merchant/order/receive")
					.contentType("application/json")
					.header("X-Tenant-Id", tenantId)
					.header("X-User-Id", userId)
					.header("X-Trace-Id", "trace-3")
					.content(acceptBody)
			)
			.andExpect(MockMvcResultMatchers.status().isOk());

		outboxEventPublisher.publishPendingEvents();
		assertThat(awaitKafkaEvent("tinystore.order.accepted", "order.accepted", Duration.ofSeconds(10))).isTrue();

		String shipBody = """
			{
			  \"orderId\": \"%s\",
			  \"operatorId\": \"OP-M1\",
			  \"idempotencyKey\": \"EVT-SHIP-1\",
			  \"logistics\": {
			    \"companyCode\": \"SF\",
			    \"companyName\": \"SF\",
			    \"trackingNo\": \"TN-1\"
			  }
			}
			""".formatted(mainOrderNo);

		mockMvc.perform(
				MockMvcRequestBuilders.post("/order/v1/order/merchant/ship")
					.contentType("application/json")
					.header("X-Tenant-Id", tenantId)
					.header("X-User-Id", userId)
					.header("X-Trace-Id", "trace-4")
					.content(shipBody)
			)
			.andExpect(MockMvcResultMatchers.status().isOk());

		outboxEventPublisher.publishPendingEvents();
		assertThat(awaitKafkaEvent("tinystore.order.shipped", "order.shipped", Duration.ofSeconds(10))).isTrue();

		String deliveredBody = """
			{
			  \"orderId\": \"%s\",
			  \"trackingNo\": \"TN-1\",
			  \"deliveredAt\": 1735689600000,
			  \"source\": \"LOGISTICS\",
			  \"eventId\": \"EVT-DEL-1\"
			}
			""".formatted(mainOrderNo);

		mockMvc.perform(
				MockMvcRequestBuilders.post("/order/v1/order/internal/logistics/delivered")
					.contentType("application/json")
					.header("X-Tenant-Id", tenantId)
					.header("X-User-Id", userId)
					.header("X-Trace-Id", "trace-5")
					.content(deliveredBody)
			)
			.andExpect(MockMvcResultMatchers.status().isOk());

		outboxEventPublisher.publishPendingEvents();
		assertThat(awaitKafkaEvent("tinystore.order.delivered", "order.delivered", Duration.ofSeconds(10))).isTrue();

		String receiptBody = """
			{
			  \"orderId\": \"%s\",
			  \"idempotencyKey\": \"EVT-REC-1\"
			}
			""".formatted(mainOrderNo);

		mockMvc.perform(
				MockMvcRequestBuilders.post("/order/v1/order/user/confirm-receipt")
					.contentType("application/json")
					.header("X-Tenant-Id", tenantId)
					.header("X-User-Id", userId)
					.header("X-Trace-Id", "trace-6")
					.content(receiptBody)
			)
			.andExpect(MockMvcResultMatchers.status().isOk());

		outboxEventPublisher.publishPendingEvents();
		assertThat(awaitKafkaEvent("tinystore.order.received", "order.received", Duration.ofSeconds(10))).isTrue();

		OrderMainEntity main = orderMainJpaRepository.findByOrderNo(mainOrderNo).orElseThrow();
		assertThat(main.getCoreFlowStatus()).isEqualTo("COMPLETED");
		assertThat(main.getPaymentStatus()).isEqualTo("PAID");
		assertThat(main.getFulfillmentStatus()).isEqualTo("RECEIVED");
	}

	private boolean awaitKafkaEvent(String expectedTopic, String expectedEventType, Duration timeout)
		throws Exception {
		long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
			for (ConsumerRecord<String, String> r : records) {
				if (!expectedTopic.equals(r.topic())) {
					continue;
				}
				JsonNode n = objectMapper.readTree(r.value());
				String et = n.path("eventType").asText();
				if (expectedEventType.equals(et)) {
					return true;
				}
			}
		}
		return false;
	}
}
