package com.github.spud.tinystore.auth.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
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
import org.testcontainers.utility.DockerImageName;

import com.github.spud.tinystore.auth.domain.audit.AuditEvent;

/**
 * C5 回归：审计记录的 ip 必须能写入 PostgreSQL 的 inet 列。
 *
 * <p>修复前 `ps.setString(7, event.ip())` 会抛
 * {@code column "ip" is of type inet but expression is of type character varying}，
 * 让 `/otp/send` 直接 500，并把真实的 OTP 错误掩盖成 SQL 异常。
 *
 * <p>本测试用真实 PostgreSQL + Flyway 迁移，断言 append 不抛异常且该行可读回。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("resource")
@DisplayName("auth 审计日志 — inet 绑定（C5）")
class JdbcAuditLogAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/auth");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private JdbcAuditLogAdapter auditLogAdapter;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("带 IP 的审计事件可以写入 inet 列")
    void append_withIp_writesInetColumn() {
        AuditEvent event = AuditEvent.failure(
                null,
                "13800000000",
                null,
                "otp_send",
                Set.of(),
                "10.1.2.3",
                "junit-agent",
                "otp_not_found");

        // 修复前：这里直接抛 BadSqlGrammarException（inet vs varchar）
        auditLogAdapter.append(event);

        String storedIp = jdbcTemplate.queryForObject(
                "select ip::text from auth_audit where action = ? order by created_at desc limit 1",
                String.class, "otp_send");
        // PostgreSQL 的 inet 文本形式带掩码（10.1.2.3/32），只断言地址本身。
        assertThat(storedIp).startsWith("10.1.2.3");
    }
}
