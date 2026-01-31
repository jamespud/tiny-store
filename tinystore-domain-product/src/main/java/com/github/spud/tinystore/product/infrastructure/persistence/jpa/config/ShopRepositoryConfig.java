package com.github.spud.tinystore.product.infrastructure.persistence.jpa.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
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

/**
 * ShopRepositoryConfig - Configuration for shop context management
 * <p>
 * Provides ShopContextProvider that extracts shop ID from: HTTP request header X-Shop-Id
 * <p>
 * ShopId is stored in request scope for consistent access during a single request lifecycle.
 */
@Configuration
public class ShopRepositoryConfig {

	/**
	 * Request-scoped bean for shop context
	 *
	 * @return ShopContext holder for current request
	 */
	@Bean
	@RequestScope
	public ShopContext shopContext() {
		return new ShopContext();
	}

	/**
	 * Provider that resolves shop ID from request context
	 *
	 * @return ShopContextProvider
	 */
	@Bean
	public ShopContextProvider shopContextProvider() {
		return new ShopContextProvider();
	}

	/**
	 * ShopContext - Holds shop ID for current request
	 */
	public static class ShopContext {

		private String shopId;

		public String getShopId() {
			if (shopId == null) {
				throw new IllegalStateException("ShopId not set in current context");
			}
			return shopId;
		}

		public void setShopId(String shopId) {
			this.shopId = shopId;
		}
	}

	/**
	 * ShopContextProvider - Resolves shop ID from request or security context
	 * <p>
	 * Extraction strategy: X-Shop-Id header from HttpServletRequest
	 * with fallback to shop_id for compatibility 3. Throw IllegalStateException if neither available
	 */
	@Slf4j
	public static class ShopContextProvider {

		private static final String SHOP_HEADER = "X-Shop-Id";
		private static final String SHOP_CLAIM = "shop_id";

		/**
		 * Resolve current shop ID
		 *
		 * @return Shop identifier
		 * @throws IllegalStateException if shop ID cannot be resolved
		 */
		public String resolveShopId() {
			// Strategy 1: Extract from HTTP request header (highest priority)
			Optional<String> shopFromHeader = extractFromRequestHeader();
			if (shopFromHeader.isPresent()) {
				log.debug("Shop ID resolved from request header: {}", shopFromHeader.get());
				return shopFromHeader.get();
			}

			// Strategy 2: Extract from JWT claims in SecurityContext (fallback)
			Optional<String> shopFromJwt = extractFromSecurityContext();
			if (shopFromJwt.isPresent()) {
				log.debug("Shop ID resolved from JWT claims: {}", shopFromJwt.get());
				return shopFromJwt.get();
			}

			// Neither source available - fail fast
			throw new IllegalStateException(
				"Shop ID not found in request header (" + SHOP_HEADER + ") " +
					"or JWT claims (" + SHOP_CLAIM + ")"
			);
		}

		/**
		 * Extract shop ID from X-Shop-Id request header
		 */
		private Optional<String> extractFromRequestHeader() {
			try {
				ServletRequestAttributes attributes =
					(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

				if (attributes == null) {
					return Optional.empty();
				}

				HttpServletRequest request = attributes.getRequest();
				
				// Preferred: X-Shop-Id
				String shopId = request.getHeader(SHOP_HEADER);
				if (StringUtils.hasText(shopId)) {
					return Optional.of(shopId);
				}

				return Optional.empty();
			} catch (Exception e) {
				log.debug("Failed to extract shop ID from request header: {}", e.getMessage());
				return Optional.empty();
			}
		}

		/**
		 * Extract shop ID from JWT claims in SecurityContext
		 * Tries shop_id claim first, then shop_id for compatibility
		 */
		private Optional<String> extractFromSecurityContext() {
			try {
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

				if (authentication instanceof JwtAuthenticationToken jwtAuth) {
					Jwt jwt = jwtAuth.getToken();
					
					// Preferred: shop_id claim
					String shopId = jwt.getClaimAsString(SHOP_CLAIM);
					if (StringUtils.hasText(shopId)) {
						return Optional.of(shopId);
					}
					
				}

				return Optional.empty();
			} catch (Exception e) {
				log.debug("Failed to extract shop ID from SecurityContext: {}", e.getMessage());
				return Optional.empty();
			}
		}
	}
}
