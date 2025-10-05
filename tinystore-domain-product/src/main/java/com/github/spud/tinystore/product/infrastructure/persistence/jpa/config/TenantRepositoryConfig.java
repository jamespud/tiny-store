package com.github.spud.tinystore.product.infrastructure.persistence.jpa.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

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
     * Extraction strategy:
     * 1. X-Tenant-Id header from HttpServletRequest (preferred)
     * 2. tenant_id claim from JWT in SecurityContext (fallback)
     * 3. Throw IllegalStateException if neither available
     */
    @Slf4j
    public static class TenantContextProvider {
        
        private static final String TENANT_HEADER = "X-Tenant-Id";
        private static final String TENANT_CLAIM = "tenant_id";
        
        /**
         * Resolve current tenant ID
         * 
         * @return Tenant identifier
         * @throws IllegalStateException if tenant ID cannot be resolved
         */
        public String resolveTenantId() {
            // Strategy 1: Extract from HTTP request header (highest priority)
            Optional<String> tenantFromHeader = extractFromRequestHeader();
            if (tenantFromHeader.isPresent()) {
                log.debug("Tenant ID resolved from request header: {}", tenantFromHeader.get());
                return tenantFromHeader.get();
            }
            
            // Strategy 2: Extract from JWT claims in SecurityContext (fallback)
            Optional<String> tenantFromJwt = extractFromSecurityContext();
            if (tenantFromJwt.isPresent()) {
                log.debug("Tenant ID resolved from JWT claims: {}", tenantFromJwt.get());
                return tenantFromJwt.get();
            }
            
            // Neither source available - fail fast
            throw new IllegalStateException(
                "Tenant ID not found in request header (" + TENANT_HEADER + ") " +
                "or JWT claims (" + TENANT_CLAIM + ")"
            );
        }
        
        /**
         * Extract tenant ID from X-Tenant-Id request header
         */
        private Optional<String> extractFromRequestHeader() {
            try {
                ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                
                if (attributes == null) {
                    return Optional.empty();
                }
                
                HttpServletRequest request = attributes.getRequest();
                String tenantId = request.getHeader(TENANT_HEADER);
                
                return StringUtils.hasText(tenantId) ? Optional.of(tenantId) : Optional.empty();
            } catch (Exception e) {
                log.debug("Failed to extract tenant ID from request header: {}", e.getMessage());
                return Optional.empty();
            }
        }
        
        /**
         * Extract tenant ID from JWT claims in SecurityContext
         */
        private Optional<String> extractFromSecurityContext() {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                
                if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                    Jwt jwt = jwtAuth.getToken();
                    String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
                    
                    return StringUtils.hasText(tenantId) ? Optional.of(tenantId) : Optional.empty();
                }
                
                return Optional.empty();
            } catch (Exception e) {
                log.debug("Failed to extract tenant ID from SecurityContext: {}", e.getMessage());
                return Optional.empty();
            }
        }
    }
}
