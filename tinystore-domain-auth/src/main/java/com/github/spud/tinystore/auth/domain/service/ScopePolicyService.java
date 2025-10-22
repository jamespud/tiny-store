package com.github.spud.tinystore.auth.domain.service;

import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import java.util.Set;

public class ScopePolicyService {

  private static final String USER_PREFIX = "user.";
  private static final Set<String> DEFAULT_GRANTED = Set.of("user.profile");

  public boolean isUserVisible(ScopeName scope) {
    return scope.value().startsWith(USER_PREFIX);
  }

  public boolean requiresConsent(ScopeName scope) {
    return isUserVisible(scope) && !DEFAULT_GRANTED.contains(scope.value());
  }

  public boolean isDefaultGranted(ScopeName scope) {
    return DEFAULT_GRANTED.contains(scope.value());
  }
}