package com.tinystore.auth.application.port.out;

import java.util.List;

import com.tinystore.auth.application.dto.AuditQuery;
import com.tinystore.auth.application.dto.AuditRecordView;
import com.tinystore.auth.domain.audit.AuditEvent;

public interface AuditLogPort {

	void append(AuditEvent event);

	List<AuditRecordView> query(AuditQuery query);
}