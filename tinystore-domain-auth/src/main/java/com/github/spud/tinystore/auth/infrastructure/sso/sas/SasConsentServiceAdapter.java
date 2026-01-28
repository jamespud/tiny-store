package com.github.spud.tinystore.auth.infrastructure.sso.sas;

import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.OutboxPort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Component;

/**
 * SAS 同意适配器：在同意发生时双写 SAS consent 与审计/历史，并写出站事件。 注意：本适配器提供方法由同意处理流程调用（例如在同意提交后）。
 */
@Component
public class SasConsentServiceAdapter {

  private final OAuth2AuthorizationConsentService consentService;
  private final AuditLogPort auditLogPort;
  private final OutboxPort outboxPort;

  public SasConsentServiceAdapter(
    OAuth2AuthorizationConsentService consentService,
    AuditLogPort auditLogPort,
    OutboxPort outboxPort) {
    this.consentService = consentService;
    this.auditLogPort = auditLogPort;
    this.outboxPort = outboxPort;
  }

  public void afterConsentApproved(String userId, String username, String clientId,
    Set<String> addedScopes, Set<String> previouslyApprovedScopes) {
    // 写入/更新 SAS consent（基于 authorities 表示 scopes）
    OAuth2AuthorizationConsent existing = consentService.findById(clientId, username);
    Set<String> previously =
      existing != null ? previouslyApprovedScopes : Set.of();
    Set<String> union = new LinkedHashSet<>(previously);
    union.addAll(addedScopes);
    OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(clientId,
      username);
    for (String scope : union) {
      builder.authority(
        new SimpleGrantedAuthority("SCOPE_" + scope));
    }
    consentService.save(builder.build());

    // 审计
    auditLogPort.append(AuditEvent.success(userId, username, clientId,
      "CONSENT_APPROVE", addedScopes, null, null, null));

    // 历史与事件（通过 outbox 记录事件，历史表由 Outbox 发布器或独立适配插入，视实现而定）
    ConsentChangedEvent evt = new ConsentChangedEvent(UserId.of(userId), ClientId.of(clientId),
      addedScopes.stream().map(ScopeName::of).collect(Collectors.toSet()),
      previouslyApprovedScopes.stream().map(ScopeName::of).collect(Collectors.toSet()),
      OffsetDateTime.now());
    outboxPort.save(evt);
  }

  public void afterConsentRevoked(String userId, String username, String clientId,
    Set<String> revokedScopes, Set<String> remainingScopes) {
    // 更新 SAS consent（移除被撤销的 scopes，基于 authorities 表示）
    OAuth2AuthorizationConsent existing = consentService.findById(clientId, username);
    Set<String> remained = new LinkedHashSet<>();
    if (existing != null && existing.getAuthorities() != null) {
      for (org.springframework.security.core.GrantedAuthority authority : existing.getAuthorities()) {
        String auth = authority.getAuthority();
        if (auth != null && auth.startsWith("SCOPE_")) {
          String scope = auth.substring("SCOPE_".length());
          if (!revokedScopes.contains(scope)) {
            remained.add(scope);
          }
        }
      }
    }
    OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(clientId,
      username);
    for (String scope : remained) {
      builder.authority(
        new SimpleGrantedAuthority("SCOPE_" + scope));
    }
    consentService.save(builder.build());

    // 审计
    auditLogPort.append(AuditEvent.success(userId, username, clientId,
      "CONSENT_REVOKE", revokedScopes, null, null, null));

    // 历史与事件
    ConsentChangedEvent evt = new ConsentChangedEvent(
      UserId.of(userId), ClientId.of(clientId),
      remainingScopes.stream().map(ScopeName::of).collect(Collectors.toSet()),
      revokedScopes.stream().map(ScopeName::of).collect(Collectors.toSet()),
      OffsetDateTime.now());
    outboxPort.save(evt);
  }
}