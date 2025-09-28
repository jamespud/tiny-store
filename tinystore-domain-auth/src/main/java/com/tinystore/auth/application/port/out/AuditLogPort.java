package com.tinystore.auth.application.port.out;

import java.util.List;

import com.tinystore.auth.application.dto.AuditQuery;
import com.tinystore.auth.application.dto.AuditRecordView;

public interface AuditLogPort {

	void append(AuditRecordView record);

	List<AuditRecordView> query(AuditQuery query);
}