package com.github.spud.tinystore.order.interfaces.filter;

import com.github.spud.tinystore.order.infrastructure.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            String tenantId = request.getHeader("X-Tenant-Id");
            String userId = request.getHeader("X-User-Id");
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            if (tenantId != null) TenantContext.setTenantId(tenantId);
            if (userId != null) TenantContext.setUserId(userId);
            MDC.put("tenantId", tenantId);
            MDC.put("userId", userId);
            MDC.put("traceId", traceId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clearAll();
            MDC.remove("tenantId");
            MDC.remove("userId");
            MDC.remove("traceId");
        }
    }
}
