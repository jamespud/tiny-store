package com.github.spud.tinystore.auth.infrastructure.sso.sas;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

/**
 * 装饰器：在保存/移除 SAS consent 时，调用适配器进行审计/历史/出站事件，保持外部端点契约不变。
 */
public class ConsentServiceFacade implements OAuth2AuthorizationConsentService {

  private final OAuth2AuthorizationConsentService delegate;
  private final SasConsentServiceAdapter adapter;

  public ConsentServiceFacade(OAuth2AuthorizationConsentService delegate,
      SasConsentServiceAdapter adapter) {
    this.delegate = delegate;
    this.adapter = adapter;
  }

  @Override
  public void save(OAuth2AuthorizationConsent authorizationConsent) {
    // 计算新增 scopes：当前授权 - 已存在授权
    OAuth2AuthorizationConsent existing = delegate.findById(
        authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
    Set<String> previously = existing != null ? existing.getScopes() : Set.of();
    delegate.save(authorizationConsent);
    Set<String> added = new LinkedHashSet<>(authorizationConsent.getScopes());
    added.removeAll(previously);
    adapter.afterConsentApproved(null, authorizationConsent.getPrincipalName(),
        authorizationConsent.getRegisteredClientId(), added, previously);
  }

  @Override
  public void remove(OAuth2AuthorizationConsent authorizationConsent) {
    // 计算撤销 scopes：已存在授权 - 当前授权（若为空）
    OAuth2AuthorizationConsent existing = delegate.findById(
        authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
    Set<String> revoked = existing != null ? existing.getScopes() : Set.of();
    delegate.remove(authorizationConsent);
    adapter.afterConsentRevoked(null, authorizationConsent.getPrincipalName(),
        authorizationConsent.getRegisteredClientId(), revoked, Set.of());
  }

  @Override
  public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
    return delegate.findById(registeredClientId, principalName);
  }
}