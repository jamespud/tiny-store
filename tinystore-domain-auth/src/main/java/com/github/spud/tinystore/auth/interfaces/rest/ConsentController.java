package com.github.spud.tinystore.auth.interfaces.rest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ConsentController {

	private final OAuth2AuthorizationConsentService consentService;

	public ConsentController(OAuth2AuthorizationConsentService consentService) {
		this.consentService = consentService;
	}

	@GetMapping("/oauth2/consent")
	public String consent(
		@RequestParam("client_id") String clientId,
		@RequestParam(value = "state", required = false) String state,
		@RequestParam(value = "scope", required = false) List<String> scopes,
		@AuthenticationPrincipal(expression = "username") String username,
		Model model) {

		Set<String> requestedScopes = new LinkedHashSet<>();
		if (scopes != null) {
			scopes.forEach(scopeParam -> requestedScopes.addAll(Arrays.asList(scopeParam.split(" "))));
		}
		Set<String> userRequestedScopes = requestedScopes.stream()
			.filter(scope -> scope.startsWith("user."))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		Set<String> previouslyApprovedScopes = Set.of();
		OAuth2AuthorizationConsent consent = consentService.findById(clientId, username);
		if (consent != null) {
			previouslyApprovedScopes = consent.getScopes();
		}
		Set<String> previouslyApprovedUserScopes = previouslyApprovedScopes.stream()
			.filter(scope -> scope.startsWith("user."))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		Set<String> scopesToApprove = userRequestedScopes.stream()
			.filter(scope -> !previouslyApprovedUserScopes.contains(scope))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		model.addAttribute("clientId", clientId);
		model.addAttribute("state", state);
		model.addAttribute("username", username);
		model.addAttribute("scopesToApprove", scopesToApprove);
		model.addAttribute("previouslyApprovedScopes", previouslyApprovedUserScopes);
		return "consent";
	}
}
