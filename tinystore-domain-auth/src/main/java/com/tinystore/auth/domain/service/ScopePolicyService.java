package com.tinystore.auth.domain.service;

import java.util.Set;

import com.tinystore.auth.domain.primitives.ScopeName;

public class ScopePolicyService {

	private static final String USER_PREFIX = "user.";
	private static final Set<String> DEFAULT_GRANTED = Set.of("user.profile");

	public boolean isUserVisible(ScopeName scope) {
		return scope.getValue().startsWith(USER_PREFIX);
	}

	public boolean requiresConsent(ScopeName scope) {
		return isUserVisible(scope) && !DEFAULT_GRANTED.contains(scope.getValue());
	}

	public boolean isDefaultGranted(ScopeName scope) {
		return DEFAULT_GRANTED.contains(scope.getValue());
	}
}