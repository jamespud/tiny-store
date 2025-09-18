package com.github.spud.tinystore.auth.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
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

import java.time.Duration;
import java.util.UUID;

@Configuration
public class RegisteredClientConfig {

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository repo) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, repo);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CommandLineRunner initClients(RegisteredClientRepository repo, PasswordEncoder encoder) {
        return args -> {
            // web client (PKCE)
            if (((JdbcRegisteredClientRepository) repo).findByClientId("tinystore-web") == null) {
                RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("tinystore-web")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://localhost:3000/callback")
                        .redirectUri("http://localhost:8080/login/oauth2/code/tinystore-web")
                        .scope("openid").scope("profile").scope("email")
                        .scope("inventory.read").scope("order.read")
                        .clientSettings(ClientSettings.builder()
                                .requireProofKey(true)
                                .requireAuthorizationConsent(false)
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(10))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .build())
                        .build();
                ((JdbcRegisteredClientRepository) repo).save(web);
            }

            // internal m2m client
            if (((JdbcRegisteredClientRepository) repo).findByClientId("tinystore-internal") == null) {
                String secret = System.getenv().getOrDefault("TINYSTORE_INTERNAL_CLIENT_SECRET", "changeit");
                RegisteredClient m2m = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("tinystore-internal")
                        .clientSecret(encoder.encode(secret))
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("inventory.read").scope("inventory.write")
                        .scope("order.read").scope("order.write")
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(5))
                                .build())
                        .clientSettings(ClientSettings.builder().build())
                        .build();
                ((JdbcRegisteredClientRepository) repo).save(m2m);
            }

            // tool client
            if (((JdbcRegisteredClientRepository) repo).findByClientId("tinystore-tool") == null) {
                RegisteredClient tool = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("tinystore-tool")
                        .clientSecret(encoder.encode("changeit"))
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://localhost:8081/callback")
                        .scope("openid").scope("profile").scope("email").scope("admin")
                        .clientSettings(ClientSettings.builder()
                                .requireProofKey(false)
                                .requireAuthorizationConsent(true)
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(10))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .build())
                        .build();
                ((JdbcRegisteredClientRepository) repo).save(tool);
            }
        };
    }
}
