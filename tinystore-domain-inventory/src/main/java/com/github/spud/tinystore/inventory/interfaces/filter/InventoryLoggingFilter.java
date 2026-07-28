package com.github.spud.tinystore.inventory.interfaces.filter;

import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC logId/traceId from the X-Trace-Id header (or a generated UUID) for every
 * inventory request, and writes it back on the response. Mirrors promotion's
 * PromotionLoggingFilter. logId == traceId (shared logback pattern shows [log-id: ...]).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = StringUtils.hasText(request.getHeader(LogConstant.HEADER_TRACE_ID))
                ? request.getHeader(LogConstant.HEADER_TRACE_ID)
                : UUID.randomUUID().toString();
        MDC.put(LogConstant.MDC_LOG_ID, traceId);
        MDC.put("traceId", traceId);
        response.setHeader(LogConstant.HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
