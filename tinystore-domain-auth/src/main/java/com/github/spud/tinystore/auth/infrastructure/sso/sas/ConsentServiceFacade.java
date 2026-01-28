package com.github.spud.tinystore.auth.infrastructure.sso.sas;

import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    Set<String> previously = Collections.emptySet();
    if (existing != null && existing.getAuthorities() != null) {
      previously = existing.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a != null && a.startsWith("SCOPE_"))
        .map(a -> a.substring("SCOPE_".length()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    delegate.save(authorizationConsent);
    Set<String> added = new LinkedHashSet<>();
    if (authorizationConsent.getAuthorities() != null) {
      added = authorizationConsent.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a != null && a.startsWith("SCOPE_"))
        .map(a -> a.substring("SCOPE_".length()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
      added.removeAll(previously);
    }
    String userId = resolveUserId();
    adapter.afterConsentApproved(userId, authorizationConsent.getPrincipalName(),
      authorizationConsent.getRegisteredClientId(), added, previously);
  }

  @Override
  public void remove(OAuth2AuthorizationConsent authorizationConsent) {
    // 计算撤销 scopes：已存在授权 - 当前授权（若为空）
    OAuth2AuthorizationConsent existing = delegate.findById(
      authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
    Set<String> revoked = Collections.emptySet();
    if (existing != null && existing.getAuthorities() != null) {
      revoked = existing.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a != null && a.startsWith("SCOPE_"))
        .map(a -> a.substring("SCOPE_".length()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    delegate.remove(authorizationConsent);
    String userId = resolveUserId();
    adapter.afterConsentRevoked(userId, authorizationConsent.getPrincipalName(),
      authorizationConsent.getRegisteredClientId(), revoked, Set.of());
  }

  @Override
  public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
    return delegate.findById(registeredClientId, principalName);
  }

  /**
   * 从当前安全上下文解析 userId（若可用）。 支持 MallUser 作为 principal；其他类型返回 null 以保持流程兼容。
   */
  private String resolveUserId() {
    var context = SecurityContextHolder.getContext();
    if (context == null) {
      return null;
    }
    var authentication = context.getAuthentication();
    if (authentication == null) {
      return null;
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof MallUser user) {
      if (user.getId() != null) {
        return user.getId().value();
      }
    }
    return null;
  }
}