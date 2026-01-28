package com.github.spud.tinystore.auth.domain.audit;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;

public record AuditEvent(
  String userId,
  String subject,
  String clientId,
  String action,
  Set<String> scopes,
  boolean success,
  String ip,
  String userAgent,
  String detail,
  OffsetDateTime occurredAt
) {

  public AuditEvent {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  public static AuditEvent success(String userId,
    String subject,
    String clientId,
    String action,
    Set<String> scopes,
    String ip,
    String userAgent,
    String detail) {
    return new AuditEvent(userId, subject, clientId, action, scopes, true, ip, userAgent, detail,
      OffsetDateTime.now());
  }

  public static AuditEvent failure(String userId,
    String subject,
    String clientId,
    String action,
    Set<String> scopes,
    String ip,
    String userAgent,
    String detail) {
    return new AuditEvent(userId, subject, clientId, action, scopes, false, ip, userAgent, detail,
      OffsetDateTime.now());
  }
}
