package com.github.spud.tinystore.account.application;

import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import com.github.spud.tinystore.account.infrastructure.persistence.repository.UserCoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/sql/user_core_only.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("用户注册并发集成测试")
class UserAccountRegisterConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
            .withExposedPorts(6379);

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
        .withEnv("KAFKA_PROCESS_ROLES", "broker,controller");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        
        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        
        // Kafka（Spring Cloud Stream Kafka Binder）
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
        
        // 禁用 Nacos（避免连接本机 Nacos）
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
    }

    @Autowired
    private UserAccountApplicationService service;

    @Autowired
    private UserCoreRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("并发注册相同手机号 - 只能成功一次")
    void concurrentRegisterSamePhone_OnlyOneSucceeds() throws InterruptedException {
        // Given
        String phone = "13800138000";
        String password = "password123";
        String nickname = "测试用户";
        int threadCount = 10;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - 多个线程同时尝试注册相同手机号
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    service.registerUser(phone, password, nickname);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 触发所有线程同时执行
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then - 验证只有一次成功
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        // 验证数据库中只有一条记录
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_core WHERE account = ?",
                Long.class,
                phone
        );
        assertThat(count).isEqualTo(1L);

        // 验证失败原因都是用户已存在或数据完整性约束
        assertThat(exceptions).allMatch(e ->
                e instanceof IllegalArgumentException ||
                        e.getCause() != null && (
                                e.getCause().getMessage().contains("duplicate key") ||
                                        e.getCause().getMessage().contains("constraint") ||
                                        e.getMessage().contains("用户已存在")
                        )
        );
    }

    @Test
    @DisplayName("并发注册不同手机号 - 全部成功")
    void concurrentRegisterDifferentPhones_AllSucceed() throws InterruptedException {
        // Given
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - 多个线程注册不同手机号
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String phone = "1380013800" + index;
                    service.registerUser(phone, "password123", "用户" + index);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 不应该有异常
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then - 所有注册都应该成功
        assertThat(successCount.get()).isEqualTo(threadCount);

        // 验证数据库中有对应数量的记录
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_core WHERE account LIKE '1380013800%'",
                Long.class
        );
        assertThat(count).isEqualTo((long) threadCount);
    }
}
