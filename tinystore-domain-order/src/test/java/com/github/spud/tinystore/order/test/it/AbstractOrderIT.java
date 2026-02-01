package com.github.spud.tinystore.order.test.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * order 模块集成测试抽象基类
 * 
 * 职责：
 * 1. 统一管理 PostgreSQL / Redis / Kafka 容器
 * 2. 通过 @DynamicPropertySource 注入测试环境配置
 * 3. 提供"默认关闭调度"的开关机制（子类可覆盖）
 * 
 * 继承规则：
 * - @DataJpaTest 的 IT（如 TradeJpaRepositoryIT）直接继承此类
 * - @SpringBootTest 的 IT 继承 AbstractSpringBootOrderIT
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractOrderIT {

    /**
     * 调度线程控制开关（默认关闭，避免测试时序噪音）
     * 子类可在静态初始化块设置为 true（如 OrderTimeoutIT）
     */
    protected static final AtomicBoolean SCHEDULING_ENABLED = new AtomicBoolean(false);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
        .withDatabaseName("tinystore_order_test")
        .withUsername("postgres")
        .withPassword("postgres");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
        .withExposedPorts(6379)
        .waitingFor(Wait.forListeningPort());

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
        .withEnv("KAFKA_PROCESS_ROLES", "broker,controller");

    /**
     * 动态注入测试环境配置
     * 
     * 核心配置：
     * - 数据源指向 PostgreSQL 容器
     * - Redis 指向 Redis 容器
     * - Kafka 指向 Kafka 容器
     * - 关闭 Flyway（测试用 Hibernate DDL）
     * - 默认关闭调度线程（避免时序噪音）
     * - outbox/scheduler 间隔设置极大值（双重保险）
     */
    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // JPA/Hibernate
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "order_it");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "true");
        
        // Flyway（集成测试禁用，避免跨模块迁移冲突）
        registry.add("spring.flyway.enabled", () -> "false");
        
        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        
        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        
        // 调度线程控制（默认关闭）
        registry.add("spring.task.scheduling.enabled", () -> String.valueOf(SCHEDULING_ENABLED.get()));
        
        // Outbox/Scheduler 间隔设置极大值（双重保险，避免误触发）
        registry.add("order.outbox.poll-interval", () -> "999999999");
        registry.add("order.scheduler.payment-timeout-interval", () -> "999999999");
        registry.add("order.scheduler.auto-receive-interval", () -> "999999999");
    }

    /**
     * 提供容器访问方法（供需要跨上下文共享容器的测试使用）
     */
    protected static PostgreSQLContainer<?> getPostgres() {
        return postgres;
    }

    protected static GenericContainer<?> getRedis() {
        return redis;
    }

    protected static ConfluentKafkaContainer getKafka() {
        return kafka;
    }
}
