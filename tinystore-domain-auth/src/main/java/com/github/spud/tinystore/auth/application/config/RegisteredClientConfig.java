package com.github.spud.tinystore.auth.application.config;

import com.github.spud.tinystore.auth.application.port.out.RegisteredClientStorePort;
import com.github.spud.tinystore.auth.infrastructure.persistence.adapter.SasRegisteredClientStoreAdapter;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

@Configuration
public class RegisteredClientConfig {

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
      if (repo.findByClientId("tinystore-web") == null) {
        RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("tinystore-web")
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
            .build();
        repo.save(web);
      }

      // internal m2m client
      if (repo.findByClientId("tinystore-internal") == null) {
        String secret = System.getenv()
            .getOrDefault("TINYSTORE_INTERNAL_CLIENT_SECRET", "changeit");
        RegisteredClient m2m = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("tinystore-internal")
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
        repo.save(m2m);
      }

      // tool client
      if (repo.findByClientId("tinystore-tool") == null) {
        RegisteredClient tool = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("tinystore-tool")
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
            .build();
        repo.save(tool);
      }
    };
  }
}
