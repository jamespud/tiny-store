package com.github.spud.tinystore.order.infrastructure.audit;

import com.github.spud.tinystore.order.infrastructure.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditService {

    public void record(String orderId, String action, String operatorType, String operatorId,
                       boolean success, Map<String, Object> extra) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();
        String traceId = MDC.get("traceId");
        log.info("AUDIT orderId={}, action={}, success={}, operatorType={}, operatorId={}, tenantId={}, userId={}, traceId={}, at={}, extra={}",
            orderId, action, success, operatorType, operatorId, tenantId, userId, traceId, OffsetDateTime.now(), extra);
    }
}
