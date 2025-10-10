package com.github.spud.tinystore.auth.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthorizationServerConfig {

	private final RegisteredClientRepository registeredClientRepository;
	private final OAuth2AuthorizationService authorizationService;
	private final OAuth2AuthorizationConsentService authorizationConsentService;
	private final OAuth2TokenGenerator<?> tokenGenerator;

	public AuthorizationServerConfig(RegisteredClientRepository registeredClientRepository,
	                                 OAuth2AuthorizationService authorizationService,
	                                 OAuth2AuthorizationConsentService authorizationConsentService,
	                                 OAuth2TokenGenerator<?> tokenGenerator) {
		this.registeredClientRepository = registeredClientRepository;
		this.authorizationService = authorizationService;
		this.authorizationConsentService = authorizationConsentService;
		this.tokenGenerator = tokenGenerator;
	}

	@Bean
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
			OAuth2AuthorizationServerConfigurer.authorizationServer();

		http
			.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
			.with(authorizationServerConfigurer, (authorizationServer) ->
				authorizationServer
					.registeredClientRepository(registeredClientRepository)
					.authorizationService(authorizationService)
					.authorizationConsentService(authorizationConsentService)
					.tokenGenerator(tokenGenerator)
					.clientAuthentication(Customizer.withDefaults())
					.authorizationEndpoint(Customizer.withDefaults())
					.pushedAuthorizationRequestEndpoint(Customizer.withDefaults())
					.deviceAuthorizationEndpoint(Customizer.withDefaults())
					.deviceVerificationEndpoint(Customizer.withDefaults())
					.tokenEndpoint(Customizer.withDefaults())
					.tokenIntrospectionEndpoint(Customizer.withDefaults())
					.tokenRevocationEndpoint(Customizer.withDefaults())
					.authorizationServerMetadataEndpoint(Customizer.withDefaults())
					.oidc(oidc -> oidc
						.providerConfigurationEndpoint(Customizer.withDefaults())
						.logoutEndpoint(Customizer.withDefaults())
						.userInfoEndpoint(Customizer.withDefaults())
						.clientRegistrationEndpoint(Customizer.withDefaults())
					)
			);

		return http.build();
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(
		@Value("${spring.authorization-server.issuer:http://localhost:9000}") String issuer) {
		return AuthorizationServerSettings.builder()
			.issuer(issuer)
			.authorizationEndpoint("/oauth2/v1/authorize")
			.pushedAuthorizationRequestEndpoint("/oauth2/v1/par")
			.deviceAuthorizationEndpoint("/oauth2/v1/device_authorization")
			.deviceVerificationEndpoint("/oauth2/v1/device_verification")
			.tokenEndpoint("/oauth2/v1/token")
			.tokenIntrospectionEndpoint("/oauth2/v1/introspect")
			.tokenRevocationEndpoint("/oauth2/v1/revoke")
			.jwkSetEndpoint("/oauth2/v1/jwks")
			.oidcLogoutEndpoint("/connect/v1/logout")
			.oidcUserInfoEndpoint("/connect/v1/userinfo")
			.oidcClientRegistrationEndpoint("/connect/v1/register")
			.build();
	}
}
