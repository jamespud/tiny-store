package com.github.spud.tinystore.product.interfaces.filter;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * TenantContextFilter - Extract tenant ID from HTTP request header
 * 
 * This filter runs with highest precedence to ensure tenant context
 * is available for all subsequent processing.
 * 
 * Tenant ID extraction order:
 * 1. X-Tenant-Id request header (preferred)
 * 2. If missing, return 400 Bad Request
 * 
 * The tenant ID is injected into request-scoped TenantContext bean
 * for use by repositories and services.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TenantContextFilter implements Filter {
    
    private static final String TENANT_HEADER = "X-Tenant-Id";
    
    private final TenantContext tenantContext;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            String tenantId = httpRequest.getHeader(TENANT_HEADER);
            
            if (!StringUtils.hasText(tenantId)) {
                log.warn("Missing tenant ID in request header: {}", httpRequest.getRequestURI());
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(
                    "{\"code\":\"MISSING_TENANT_ID\"," +
                    "\"message\":\"X-Tenant-Id header is required\"}"
                );
                return;
            }
            
            // Inject tenant ID into request-scoped context
            tenantContext.setTenantId(tenantId);
            log.debug("Tenant context set: {} for request: {}", tenantId, httpRequest.getRequestURI());
            
            // Continue with filter chain
            chain.doFilter(request, response);
            
        } finally {
            // Clear tenant context to prevent memory leaks
            // Note: Spring will destroy request-scoped bean automatically,
            // but explicit cleanup is good practice
            try {
                tenantContext.setTenantId(null);
            } catch (Exception e) {
                log.debug("Failed to clear tenant context (may be already destroyed): {}", e.getMessage());
            }
        }
    }
}
