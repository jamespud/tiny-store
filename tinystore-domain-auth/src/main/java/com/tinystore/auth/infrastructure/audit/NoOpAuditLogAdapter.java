package com.tinystore.auth.infrastructure.audit;

import com.tinystore.auth.application.dto.AuditQuery;
import com.tinystore.auth.application.dto.AuditRecordView;
import com.tinystore.auth.application.port.out.AuditLogPort;
import com.tinystore.auth.domain.audit.AuditEvent;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class NoOpAuditLogAdapter implements AuditLogPort {

	@Override
	public void append(AuditEvent event) {
		// Phase 1: no-op
	}

	@Override
	public List<AuditRecordView> query(AuditQuery query) {
		return Collections.emptyList();
	}
}