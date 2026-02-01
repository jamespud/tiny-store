package com.github.spud.tinystore.payment.test.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Payment 模块集成测试抽象基类
 * 
 * 职责：
 * 1. 统一管理 PostgreSQL / Redis / Kafka 容器（测试不依赖本机服务）
 * 2. 通过 @DynamicPropertySource 注入测试环境配置
 * 
 * 继承规则：
 * - 所有 @SpringBootTest 的集成测试应继承此类
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPaymentIT {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
        .withDatabaseName("tinystore_payment_test")
        .withUsername("postgres")
        .withPassword("postgres");

    @Container
    protected static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
        .withExposedPorts(6379);

    @Container
    protected static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:8.1.1")
    ).withEnv("KAFKA_PROCESS_ROLES", "broker,controller");


    /**
     * 动态注入测试环境配置
     * 
     * 核心配置：
     * - PostgreSQL 指向 PostgreSQL 容器
     * - Redis 指向 Redis 容器
     * - Kafka 指向 Kafka 容器
     */
    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // JPA/Hibernate schema
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "tinystore_payment");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "true");
        
        // Flyway
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.default-schema", () -> "tinystore_payment");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/payment");
        
        // Redis（同时注入 spring.data.redis 与 spring.redis 以确保 Redisson 兼容）
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379).toString());
        
        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
