package com.tinystore.auth.infrastructure.audit;

import com.tinystore.auth.application.dto.AuditQuery;
import com.tinystore.auth.application.dto.AuditRecordView;
import com.tinystore.auth.application.port.out.AuditLogPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class NoOpAuditLogAdapter implements AuditLogPort {

	@Override
	public void append(AuditRecordView record) {
		// Phase 1: no-op
	}

	@Override
	public List<AuditRecordView> query(AuditQuery query) {
		return Collections.emptyList();
	}
}