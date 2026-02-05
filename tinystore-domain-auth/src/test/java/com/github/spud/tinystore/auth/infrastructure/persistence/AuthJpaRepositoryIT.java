package com.github.spud.tinystore.auth.infrastructure.persistence;

import com.github.spud.tinystore.auth.infrastructure.persistence.entity.Authorization;
import com.github.spud.tinystore.auth.infrastructure.persistence.entity.Client;
import com.github.spud.tinystore.auth.infrastructure.persistence.repository.AuthorizationRepository;
import com.github.spud.tinystore.auth.infrastructure.persistence.repository.ClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthJpaRepositoryIT - Auth 模块 JPA 持久化集成测试
 * 
 * 测试范围：
 * 1. Flyway 迁移成功执行（表结构正确创建）
 * 2. Client（RegisteredClient）实体映射与读写
 * 3. 唯一约束生效（uk_oauth2_registered_client_client_id）
 * 4. Authorization 实体映射与外键约束、查询方法可用
 * 
 * 使用 Testcontainers 启动真实 PostgreSQL 实例
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("resource")
@DisplayName("Auth JPA Repository 集成测试")
public class AuthJpaRepositoryIT {

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
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Flyway（使用 auth 模块的迁移脚本）
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/auth");
        
        // JPA（验证模式：确保实体与迁移一致）
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        
        // Redis（Testcontainers）
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AuthorizationRepository authorizationRepository;

    @Test
    @DisplayName("Flyway 迁移成功 & 关键表已创建")
    void flywayMigrationsExecutedAndTablesExist() {
        // 验证 Flyway 历史表存在
        Integer flywayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(flywayCount).as("Flyway 迁移记录").isGreaterThan(0);

        // 验证核心业务表存在（oauth2_registered_client, oauth2_authorization, mall_user, login_otp）
        Integer clientTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'oauth2_registered_client'",
                Integer.class);
        assertThat(clientTableExists).as("oauth2_registered_client 表存在").isEqualTo(1);

        Integer authTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'oauth2_authorization'",
                Integer.class);
        assertThat(authTableExists).as("oauth2_authorization 表存在").isEqualTo(1);

        Integer userTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'mall_user'",
                Integer.class);
        assertThat(userTableExists).as("mall_user 表存在").isEqualTo(1);

        Integer otpTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'login_otp'",
                Integer.class);
        assertThat(otpTableExists).as("login_otp 表存在").isEqualTo(1);
    }

    @Test
    @DisplayName("Client 实体写入与读取 - JPA 映射正确")
    void clientEntityCanBeSavedAndRetrieved() {
        // Given
        Client client = new Client();
        client.setId("test-client-id-" + System.currentTimeMillis());
        client.setClientId("test-client-" + System.currentTimeMillis());
        client.setClientIdIssuedAt(Instant.now());
        client.setClientName("Test Client");
        client.setClientAuthenticationMethods("client_secret_basic");
        client.setAuthorizationGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris("http://localhost:8080/callback");
        client.setScopes("openid,profile");
        client.setClientSettings("{}");
        client.setTokenSettings("{}");

        // When
        Client saved = clientRepository.save(client);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(client.getId());

        Optional<Client> retrieved = clientRepository.findByClientId(client.getClientId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getClientName()).isEqualTo("Test Client");
    }

    @Test
    @DisplayName("Client 唯一约束生效 - 重复 client_id 抛异常")
    void duplicateClientIdViolatesUniqueConstraint() {
        // Given - 第一个客户端
        Client client1 = new Client();
        client1.setId("unique-test-1-" + System.currentTimeMillis());
        client1.setClientId("duplicate-client-id");
        client1.setClientIdIssuedAt(Instant.now());
        client1.setClientName("Client 1");
        client1.setClientAuthenticationMethods("client_secret_basic");
        client1.setAuthorizationGrantTypes("authorization_code");
        client1.setScopes("openid");
        client1.setClientSettings("{}");
        client1.setTokenSettings("{}");
        clientRepository.save(client1);

        // Given - 第二个客户端（相同 client_id）
        Client client2 = new Client();
        client2.setId("unique-test-2-" + System.currentTimeMillis());
        client2.setClientId("duplicate-client-id"); // 重复！
        client2.setClientIdIssuedAt(Instant.now());
        client2.setClientName("Client 2");
        client2.setClientAuthenticationMethods("client_secret_post");
        client2.setAuthorizationGrantTypes("client_credentials");
        client2.setScopes("read");
        client2.setClientSettings("{}");
        client2.setTokenSettings("{}");

        // When & Then - 保存应抛出唯一约束异常
        assertThatThrownBy(() -> {
            clientRepository.saveAndFlush(client2);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Authorization 实体读写 & 外键约束 & 查询方法可用")
    void authorizationEntityWithForeignKeyAndQueries() {
        // Given - 先创建 Client（满足外键约束）
        Client client = new Client();
        client.setId("auth-test-client-" + System.currentTimeMillis());
        client.setClientId("auth-client-" + System.currentTimeMillis());
        client.setClientIdIssuedAt(Instant.now());
        client.setClientName("Auth Test Client");
        client.setClientAuthenticationMethods("client_secret_basic");
        client.setAuthorizationGrantTypes("authorization_code");
        client.setScopes("openid");
        client.setClientSettings("{}");
        client.setTokenSettings("{}");
        clientRepository.save(client);

        // Given - 创建 Authorization
        Authorization auth = new Authorization();
        auth.setId("auth-" + System.currentTimeMillis());
        auth.setRegisteredClientId(client.getId());
        auth.setPrincipalName("test-user");
        auth.setAuthorizationGrantType("authorization_code");
        auth.setState("test-state-123");
        auth.setAuthorizationCodeValue("code-xyz");
        auth.setAuthorizationCodeIssuedAt(Instant.now());
        auth.setAuthorizationCodeExpiresAt(Instant.now().plusSeconds(300));

        // When
        Authorization saved = authorizationRepository.save(auth);

        // Then - 基础保存与读取
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(auth.getId());

        // Then - 通过 state 查询
        Optional<Authorization> byState = authorizationRepository.findByState("test-state-123");
        assertThat(byState).isPresent();
        assertThat(byState.get().getPrincipalName()).isEqualTo("test-user");

        // Then - 通过 authorization_code 查询
        Optional<Authorization> byCode = authorizationRepository.findByAuthorizationCodeValue("code-xyz");
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getRegisteredClientId()).isEqualTo(client.getId());

        // Then - 聚合查询方法（findByStateOrAuthorizationCodeValue...）
        Optional<Authorization> byToken = authorizationRepository
                .findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValueOrOidcIdTokenValueOrUserCodeValueOrDeviceCodeValue("test-state-123");
        assertThat(byToken).isPresent();
        assertThat(byToken.get().getId()).isEqualTo(auth.getId());
    }
}
