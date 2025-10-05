package com.github.spud.tinystore.product.interfaces.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig - API documentation configuration for Product Service
 * 
 * Configures Springdoc OpenAPI for automatic API documentation generation.
 * Documentation will be available at:
 * - Swagger UI: /swagger-ui.html
 * - OpenAPI JSON: /v3/api-docs
 * - OpenAPI YAML: /v3/api-docs.yaml
 * 
 * Security:
 * - JWT Bearer Token authentication required for all endpoints
 * - Token should be included in Authorization header: "Bearer {token}"
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Product Service API",
        version = "1.0.0",
        description = """
            Product Service provides comprehensive product management capabilities including:
            - Product CRUD operations (create, update, publish, archive)
            - SKU management with dynamic specifications and attributes
            - Dynamic pricing with rule-based calculation engine
            - Multi-tenant isolation with tenant-scoped data access
            - Event-driven architecture with outbox pattern
            
            All endpoints require authentication via JWT Bearer token and tenant identification via X-Tenant-Id header.
            """,
        contact = @Contact(
            name = "TinyStore Team",
            email = "support@tinystore.com",
            url = "https://github.com/jamespud/tiny-store"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(
            url = "http://localhost:8080",
            description = "Local Development Server"
        ),
        @Server(
            url = "https://api-dev.tinystore.com",
            description = "Development Environment"
        ),
        @Server(
            url = "https://api.tinystore.com",
            description = "Production Environment"
        )
    },
    security = @SecurityRequirement(name = "bearerAuth"),
    tags = {
        @Tag(name = "Products", description = "Product lifecycle management - create, update, publish, archive"),
        @Tag(name = "SKUs", description = "SKU management - specifications, attributes, inventory references"),
        @Tag(name = "Pricing", description = "Dynamic pricing calculation based on rules and context")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = """
        JWT Bearer Token authentication.
        
        To obtain a token:
        1. Authenticate with Auth Service (/api/auth/login)
        2. Include the token in Authorization header: Bearer {token}
        
        Token claims should include:
        - sub: User ID
        - tenant_id: Tenant identifier
        - roles: User roles (MERCHANT_ADMIN, PLATFORM_ADMIN, etc.)
        
        Token expiration: 1 hour (configurable)
        Refresh tokens available for long-lived sessions
        """
)
public class OpenApiConfig {
    // Configuration is declarative via annotations
    // No additional beans needed for basic setup
}
