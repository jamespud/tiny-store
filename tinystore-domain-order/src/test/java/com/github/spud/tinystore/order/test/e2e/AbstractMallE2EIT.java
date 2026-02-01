package com.github.spud.tinystore.order.test.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.order.OrderApplication;
import com.github.spud.tinystore.order.test.it.AbstractOrderIT;
import com.github.spud.tinystore.payment.PaymentApplication;
import com.github.spud.tinystore.promotion.PromotionApplication;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMallE2EIT extends AbstractOrderIT {

    protected ConfigurableApplicationContext orderContext;
    protected ConfigurableApplicationContext inventoryContext;
    protected ConfigurableApplicationContext promotionContext;
    protected ConfigurableApplicationContext paymentContext;

    protected int orderPort;
    protected int inventoryPort;
    protected int promotionPort;
    protected int paymentPort;

    protected String orderBaseUrl;
    protected String inventoryBaseUrl;
    protected String promotionBaseUrl;
    protected String paymentBaseUrl;

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final RestTemplate restTemplate = new RestTemplate();

    protected KafkaTestProducer kafkaTestProducer;

    @BeforeAll
    void startContexts() {
        if (orderContext != null) {
            return;
        }

        inventoryContext = startInventoryContext();
        promotionContext = startPromotionContext();
        orderContext = startOrderContext();
        paymentContext = startPaymentContext();

        inventoryPort = getPort(inventoryContext);
        promotionPort = getPort(promotionContext);
        orderPort = getPort(orderContext);
        paymentPort = getPort(paymentContext);

        inventoryBaseUrl = "http://localhost:" + inventoryPort;
        promotionBaseUrl = "http://localhost:" + promotionPort;
        orderBaseUrl = "http://localhost:" + orderPort;
        paymentBaseUrl = "http://localhost:" + paymentPort;

        kafkaTestProducer = new KafkaTestProducer(getKafka().getBootstrapServers(), "tinystore.order.general");
    }

    @AfterAll
    void stopContexts() {
        if (kafkaTestProducer != null) {
            kafkaTestProducer.close();
        }
        if (paymentContext != null) {
            paymentContext.close();
        }
        if (orderContext != null) {
            orderContext.close();
        }
        if (promotionContext != null) {
            promotionContext.close();
        }
        if (inventoryContext != null) {
            inventoryContext.close();
        }
    }

    protected <T> T getOrderBean(Class<T> type) {
        return orderContext.getBean(type);
    }

    protected <T> T getInventoryBean(Class<T> type) {
        return inventoryContext.getBean(type);
    }

    protected <T> T getPromotionBean(Class<T> type) {
        return promotionContext.getBean(type);
    }

    protected <T> T getPaymentBean(Class<T> type) {
        return paymentContext.getBean(type);
    }

    private ConfigurableApplicationContext startOrderContext() {
        Map<String, Object> props = baseProps("tinystore_order");
        props.put("spring.flyway.locations", "classpath:db/migration/order");
        props.put("order.feign.inventory-url", "http://localhost:" + getPort(inventoryContext));
        props.put("order.feign.promotion-url", "http://localhost:" + getPort(promotionContext));
        props.put("spring.task.scheduling.enabled", "false");
        props.put("order.outbox.poll-interval", "999999999");
        props.put("order.scheduler.payment-timeout-interval", "999999999");
        props.put("order.scheduler.auto-receive-interval", "999999999");
        return startContext(OrderApplication.class, props);
    }

    private ConfigurableApplicationContext startInventoryContext() {
        Map<String, Object> props = baseProps("tinystore_inventory");
        props.put("spring.flyway.locations", "classpath:db/migration/inventory");
        return startContext(InventoryApplication.class, props);
    }

    private ConfigurableApplicationContext startPromotionContext() {
        Map<String, Object> props = baseProps("tinystore_promotion");
        props.put("spring.flyway.locations", "classpath:db/migration/promotion");
        return startContext(PromotionApplication.class, props);
    }

    private ConfigurableApplicationContext startPaymentContext() {
        Map<String, Object> props = baseProps("tinystore_payment");
        props.put("spring.flyway.locations", "classpath:db/migration/payment");
        props.put("feign.client.url.order", "http://localhost:" + getPort(orderContext));
        props.put("payment.kafka.topic.order-events", "tinystore.order.general");
        props.put("spring.task.scheduling.enabled", "false");
        return startContext(PaymentApplication.class, props);
    }

    private ConfigurableApplicationContext startContext(Class<?> appClass, Map<String, Object> props) {
        return new SpringApplicationBuilder(appClass)
            .properties(props)
            .run();
    }

    private Map<String, Object> baseProps(String schema) {
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", "0");
        props.put("spring.main.web-application-type", "servlet");
        props.put("spring.datasource.url", jdbcUrlWithSchema(schema));
        props.put("spring.datasource.username", getPostgres().getUsername());
        props.put("spring.datasource.password", getPostgres().getPassword());
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        props.put("spring.jpa.hibernate.ddl-auto", "validate");
        props.put("spring.jpa.properties.hibernate.default_schema", schema);
        props.put("spring.flyway.enabled", "true");
        props.put("spring.flyway.schemas", schema);
        props.put("spring.flyway.default-schema", schema);
        props.put("spring.flyway.create-schemas", "true");
        props.put("spring.kafka.bootstrap-servers", getKafka().getBootstrapServers());
        props.put("spring.data.redis.host", getRedis().getHost());
        props.put("spring.data.redis.port", getRedis().getMappedPort(6379).toString());
        props.put("tinystore.security.resource-server.enabled", "false");
        return props;
    }

    private String jdbcUrlWithSchema(String schema) {
        String base = getPostgres().getJdbcUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "currentSchema=" + schema;
    }

    private int getPort(ConfigurableApplicationContext context) {
        if (context instanceof WebServerApplicationContext webContext) {
            return webContext.getWebServer().getPort();
        }
        throw new IllegalStateException("Context does not have a web server: " + context.getId());
    }
}
