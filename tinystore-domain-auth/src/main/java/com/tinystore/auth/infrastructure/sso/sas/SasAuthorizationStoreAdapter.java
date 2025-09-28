package com.tinystore.auth.infrastructure.sso.sas;

import com.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.tinystore.auth.domain.primitives.ClientId;
import com.tinystore.auth.domain.primitives.ScopeName;
import com.tinystore.auth.domain.primitives.UserId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SasAuthorizationStoreAdapter implements AuthorizationStorePort {

	private final JdbcTemplate jdbcTemplate;
	private final OAuth2AuthorizationConsentService consentService;

	public SasAuthorizationStoreAdapter(JdbcTemplate jdbcTemplate,
	                                   OAuth2AuthorizationConsentService consentService) {
		this.jdbcTemplate = jdbcTemplate;
		this.consentService = consentService;
	}

	@Override
	public void clearAuthorizationsOf(UserId userId) {
		jdbcTemplate.update("delete from oauth2_authorization where principal_name = ?", userId.toString());
	}

	@Override
	public Set<ScopeName> loadConsent(UserId userId, ClientId clientId) {
		OAuth2AuthorizationConsent consent = consentService.findById(clientId.getValue(), userId.toString());
		if (consent == null) {
			return Set.of();
		}
		return consent.getScopes().stream()
			.map(ScopeName::of)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	@Override
	public void persistConsent(UserId userId, ClientId clientId, Set<ScopeName> scopes) {
		if (scopes.isEmpty()) {
			OAuth2AuthorizationConsent consent = consentService.findById(clientId.getValue(), userId.toString());
			if (consent != null) {
				consentService.remove(consent);
			}
			return;
		}
		OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(clientId.getValue(), userId.toString());
		scopes.stream().map(ScopeName::getValue).forEach(builder::scope);
		consentService.save(builder.build());
	}
}