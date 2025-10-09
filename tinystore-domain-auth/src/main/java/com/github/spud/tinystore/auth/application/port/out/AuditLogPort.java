package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.application.dto.AuditQuery;
import com.github.spud.tinystore.auth.application.dto.AuditRecordView;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;

import java.util.List;

public interface AuditLogPort {

	void append(AuditEvent event);

	List<AuditRecordView> query(AuditQuery query);
}