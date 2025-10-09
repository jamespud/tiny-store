package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.AuditQuery;
import com.github.spud.tinystore.auth.application.dto.AuditRecordView;

import java.util.List;

public interface AuditQueryUseCase {

	List<AuditRecordView> query(AuditQuery query);
}