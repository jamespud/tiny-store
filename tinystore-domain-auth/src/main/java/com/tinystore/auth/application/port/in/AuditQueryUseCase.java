package com.tinystore.auth.application.port.in;

import java.util.List;

import com.tinystore.auth.application.dto.AuditQuery;
import com.tinystore.auth.application.dto.AuditRecordView;

public interface AuditQueryUseCase {

	List<AuditRecordView> query(AuditQuery query);
}