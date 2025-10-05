package com.github.spud.tinystore.product.infrastructure.persistence.jpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

/**
 * TenantRepositoryConfig - Configuration for tenant context management
 * 
 * Provides TenantContextProvider that extracts tenant ID from:
 * 1. HTTP request header X-Tenant-Id (preferred)
 * 2. SecurityContext authenticated principal (fallback)
 * 3. Throws exception if neither available
 * 
 * TenantId is stored in request scope for consistent access during
 * a single request lifecycle.
 */
@Configuration
public class TenantRepositoryConfig {
    
    /**
     * Request-scoped bean for tenant context
     * 
     * @return TenantContext holder for current request
     */
    @Bean
    @RequestScope
    public TenantContext tenantContext() {
        return new TenantContext();
    }
    
    /**
     * Provider that resolves tenant ID from request context
     * 
     * @return TenantContextProvider
     */
    @Bean
    public TenantContextProvider tenantContextProvider() {
        return new TenantContextProvider();
    }
    
    /**
     * TenantContext - Holds tenant ID for current request
     */
    public static class TenantContext {
        private String tenantId;
        
        public String getTenantId() {
            if (tenantId == null) {
                throw new IllegalStateException("TenantId not set in current context");
            }
            return tenantId;
        }
        
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }
    
    /**
     * TenantContextProvider - Resolves tenant ID from request or security context
     * 
     * TODO: Implement extraction logic:
     * - Read X-Tenant-Id header from HttpServletRequest
     * - Fallback to SecurityContext principal tenant claim
     * - Throw TenantNotFoundException if neither available
     */
    public static class TenantContextProvider {
        
        /**
         * Resolve current tenant ID
         * 
         * @return Tenant identifier
         * @throws IllegalStateException if tenant ID cannot be resolved
         */
        public String resolveTenantId() {
            // TODO: Implement tenant resolution logic
            // 1. Check HttpServletRequest for X-Tenant-Id header
            // 2. Check SecurityContext for tenant claim
            // 3. Throw exception if not found
            throw new UnsupportedOperationException("TenantContextProvider.resolveTenantId not yet implemented");
        }
    }
}
