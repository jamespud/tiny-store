package com.github.spud.tinystore.auth.application.config;

import com.github.spud.tinystore.auth.infrastructure.persistence.repository.ClientRepository;
import com.github.spud.tinystore.auth.infrastructure.persistence.repository.JpaRegisteredClientRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

@Configuration
public class RegisteredClientConfig {

  private static final Logger LOG = LoggerFactory.getLogger(RegisteredClientConfig.class);

  @Bean
  public RegisteredClientRepository registeredClientRepository(ClientRepository clientRepository) {
    return new JpaRegisteredClientRepository(clientRepository);
  }

  /**
   * Deterministic primary key for a seeded client.
   *
   * <p>Name-based rather than random on purpose: every replica seeding the same clientId derives the
   * same row identity, so a second concurrent INSERT collides on the primary key instead of quietly
   * creating a duplicate row, and a restart re-derives the same id.
   */
  static String seededClientId(String clientId) {
    return UUID.nameUUIDFromBytes(("tinystore-registered-client:" + clientId)
      .getBytes(StandardCharsets.UTF_8)).toString();
  }

  /**
   * Idempotent AND concurrency-safe client seed.
   *
   * <p>Plain check-then-insert is unsafe when several replicas boot at once: two replicas can both
   * observe "absent" and both INSERT, and the loser dies with
   * {@code duplicate key value violates unique constraint "uk_oauth2_registered_client_client_id"},
   * which surfaces as {@code Application run failed} and takes that whole replica down. Under
   * {@code MULTI_REPLICAS=3} this reliably killed one auth replica, so the stack never reached N
   * healthy instances.
   *
   * <p>A lost race is therefore not an error: if the insert conflicts but the row is present now,
   * another replica seeded identical content and we carry on.
   */
  private void seedIfAbsent(RegisteredClientRepository repo, String clientId,
    Supplier<RegisteredClient> factory) {
    if (repo.findByClientId(clientId) != null) {
      return;
    }
    try {
      repo.save(factory.get());
      LOG.info("Seeded registered client '{}'", clientId);
    } catch (DataIntegrityViolationException e) {
      if (repo.findByClientId(clientId) != null) {
        LOG.info("Registered client '{}' was seeded concurrently by another replica; continuing.",
          clientId);
        return;
      }
      throw e;
    }
  }

  @Value("${tinystore.auth.tokens.user.access-token-ttl:PT2H}")
  private Duration userAccessTokenTtl;

  @Value("${tinystore.auth.tokens.user.refresh-token-ttl:P30D}")
  private Duration userRefreshTokenTtl;

  @Value("${tinystore.auth.tokens.user.id-token-ttl:PT2H}")
  private Duration userIdTokenTtl;

  @Value("${tinystore.auth.tokens.m2m.access-token-ttl:PT5M}")
  private Duration m2mAccessTokenTtl;

  @Bean
  public OAuth2AuthorizationService authorizationService(
    JdbcTemplate jdbcTemplate,
    RegisteredClientRepository repo) {
    // 显式提供 AuthorizationService，避免隐式自动装配
    return new JdbcOAuth2AuthorizationService(jdbcTemplate, repo);
  }

  @Bean
  CommandLineRunner initClients(RegisteredClientRepository repo, PasswordEncoder encoder) {
    return args -> {
      // web client (PKCE)
      seedIfAbsent(repo, "tinystore-web", () -> RegisteredClient.withId(seededClientId("tinystore-web"))
        .clientId("tinystore-web")
        .clientIdIssuedAt(Instant.now())
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://localhost:3000/callback")
        .redirectUri("http://localhost:8080/login/oauth2/code/tinystore-web")
        .scope("openid")
        .scope("user.profile")
        .scope("user.phone")
        .scope("user.address")
        .scope("user.follow")
        .scope("user.payment")
        .clientSettings(ClientSettings.builder()
          .requireProofKey(true)
          .requireAuthorizationConsent(true)
          .build())
        .tokenSettings(TokenSettings.builder()
          .accessTokenTimeToLive(userAccessTokenTtl)
          .refreshTokenTimeToLive(userRefreshTokenTtl)
          .reuseRefreshTokens(false)
          .build())
        .build());

      // internal m2m client
      // 轮换语义（评审 P2）：seedIfAbsent 只在客户端不存在时插入，因此**改环境变量不会轮换**已经写进
      // DB 的 secret；轮换需要显式更新路径（改 clientSecret 或写迁移）。prod 下缺失/默认 secret 会由
      // DefaultCredentialGuard 直接拒绝启动。
      seedIfAbsent(repo, "tinystore-internal", () -> {
        String secret = System.getenv()
          .getOrDefault("TINYSTORE_INTERNAL_CLIENT_SECRET", "changeit");
        return RegisteredClient.withId(seededClientId("tinystore-internal"))
          .clientId("tinystore-internal")
          .clientIdIssuedAt(Instant.now())
          .clientSecret(encoder.encode(secret))
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
          .scope("svc.inventory.read").scope("svc.inventory.write")
          .scope("svc.order.read").scope("svc.order.write")
          .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(m2mAccessTokenTtl)
            .build())
          .clientSettings(ClientSettings.builder().build())
          .build();
      });

      // tool client
      seedIfAbsent(repo, "tinystore-tool", () -> RegisteredClient.withId(seededClientId("tinystore-tool"))
        .clientId("tinystore-tool")
        .clientIdIssuedAt(Instant.now())
        .clientSecret(encoder.encode("changeit"))
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://localhost:8081/callback")
        .scope("openid")
        .scope("user.profile")
        .scope("user.phone")
        .scope("user.address")
        .scope("user.follow")
        .scope("user.payment")
        .scope("svc.admin")
        .clientSettings(ClientSettings.builder()
          .requireProofKey(false)
          .requireAuthorizationConsent(true)
          .build())
        .tokenSettings(TokenSettings.builder()
          .accessTokenTimeToLive(userAccessTokenTtl)
          .refreshTokenTimeToLive(userRefreshTokenTtl)
          .reuseRefreshTokens(false)
          .build())
        .build());
    };
  }
}
