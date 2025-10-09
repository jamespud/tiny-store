package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.ConsentView;
import com.github.spud.tinystore.auth.application.dto.LoadConsentCommand;
import com.github.spud.tinystore.auth.application.port.in.ConsentUseCase;
import com.github.spud.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.domain.service.ScopePolicyService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConsentApplicationService implements ConsentUseCase {

	private final AuthorizationStorePort authorizationStorePort;
	private final ScopePolicyService scopePolicyService;

	public ConsentApplicationService(AuthorizationStorePort authorizationStorePort,
	                                 ScopePolicyService scopePolicyService) {
		this.authorizationStorePort = authorizationStorePort;
		this.scopePolicyService = scopePolicyService;
	}

	@Override
	public ConsentView loadConsent(LoadConsentCommand command) {
		List<String> requestedScopesParam = command.requestedScopes();
		Set<String> requestedScopes = new LinkedHashSet<>();
		if (requestedScopesParam != null) {
			requestedScopesParam.forEach(scope -> {
				for (String part : scope.split(" ")) {
					if (!part.isBlank()) {
						requestedScopes.add(part.trim());
					}
				}
			});
		}

		Set<String> userVisibleScopes = requestedScopes.stream()
			.filter(scope -> scopePolicyService.isUserVisible(ScopeName.of(scope)))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		var userId = parseUserId(command.username());
		Set<ScopeName> consentedScopes = userId != null
			? authorizationStorePort.loadConsent(userId, ClientId.of(command.clientId()))
			: Set.of();
		Set<String> approvedUserScopes = consentedScopes.stream()
			.map(ScopeName::getValue)
			.filter(scope -> scopePolicyService.isUserVisible(ScopeName.of(scope)))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		Set<String> scopesToApprove = userVisibleScopes.stream()
			.filter(scope -> !approvedUserScopes.contains(scope))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		return new ConsentView(
			command.clientId(),
			command.state(),
			command.username(),
			scopesToApprove,
			approvedUserScopes
		);
	}

	private UserId parseUserId(String username) {
		try {
			return UserId.of(username);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}