package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuditQuery;
import com.github.spud.tinystore.auth.application.dto.AuditRecordView;
import com.github.spud.tinystore.auth.application.port.in.AuditQueryUseCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditQueryApplicationService implements AuditQueryUseCase {

	private final AuditLogPort auditLogPort;

	public AuditQueryApplicationService(AuditLogPort auditLogPort) {
		this.auditLogPort = auditLogPort;
	}

	@Override
	public List<AuditRecordView> query(AuditQuery query) {
		return auditLogPort.query(query);
	}
}