package com.github.spud.tinystore.auth.application.dto;

import java.time.OffsetDateTime;

public record AuditQuery(String userId,
                         String clientId,
                         OffsetDateTime from,
                         OffsetDateTime to,
                         int limit) {
}