package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.service.model.AuditEntry;

/**
 * 审计记录接口，用于记录应用服务层的命令执行轨迹。
 */
public interface AuditRecorder {
    void record(AuditEntry entry);
}
