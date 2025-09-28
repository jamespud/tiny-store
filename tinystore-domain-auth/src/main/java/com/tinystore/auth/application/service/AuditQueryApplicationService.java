package com.tinystore.auth.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tinystore.auth.application.dto.AuditQuery;
import com.tinystore.auth.application.dto.AuditRecordView;
import com.tinystore.auth.application.port.in.AuditQueryUseCase;
import com.tinystore.auth.application.port.out.AuditLogPort;

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