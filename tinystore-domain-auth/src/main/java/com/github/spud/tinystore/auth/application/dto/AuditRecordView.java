package com.github.spud.tinystore.auth.application.dto;

import java.time.OffsetDateTime;

public record AuditRecordView(String userId,
                              String clientId,
                              String action,
                              String scopes,
                              String ip,
                              String userAgent,
                              OffsetDateTime occurredAt) {

}