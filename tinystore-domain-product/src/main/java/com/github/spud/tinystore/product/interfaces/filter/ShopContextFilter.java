package com.github.spud.tinystore.product.interfaces.filter;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.ShopRepositoryConfig.ShopContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ShopContextFilter - Extract shop ID from HTTP request header
 * <p>
 * This filter runs with highest precedence to ensure shop context is available for all subsequent
 * processing.
 * <p>
 * Shop ID extraction order: X-Shop-Id request header 
 * <p>
 * The shop ID is injected into request-scoped ShopContext bean for use by repositories and
 * services.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ShopContextFilter implements Filter {

	private static final String SHOP_HEADER = "X-Shop-Id";

	private final ObjectProvider<ShopContext> shopContextProvider;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
		throws IOException, ServletException {

		if (!(request instanceof HttpServletRequest)) {
			chain.doFilter(request, response);
			return;
		}

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		
		// Skip shop context validation for actuator endpoints (management port)
		if (httpRequest.getRequestURI().startsWith("/actuator/")) {
			chain.doFilter(request, response);
			return;
		}
		ShopContext shopContext = shopContextProvider.getIfAvailable();
		if (shopContext == null) {
			chain.doFilter(request, response);
			return;
		}

		try {
			// Preferred: X-Shop-Id header
			String shopId = httpRequest.getHeader(SHOP_HEADER);

			if (!StringUtils.hasText(shopId)) {
				log.warn("Missing shop ID in request header: {}", httpRequest.getRequestURI());
				httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				httpResponse.setContentType("application/json");
				httpResponse.getWriter().write(
					"{\"code\":\"MISSING_SHOP_ID\"," +
						"\"message\":\"X-Shop-Id header is required\"}"
				);
				return;
			}

			// Inject shop ID into request-scoped context
			shopContext.setShopId(shopId);
			log.debug("Shop context set: {} for request: {}", shopId, httpRequest.getRequestURI());

			// Continue with filter chain
			chain.doFilter(request, response);

		} finally {
			// Clear shop context to prevent memory leaks
			// Note: Spring will destroy request-scoped bean automatically,
			// but explicit cleanup is good practice
			try {
				shopContext.setShopId(null);
			} catch (Exception e) {
				log.debug("Failed to clear shop context (may be already destroyed): {}", e.getMessage());
			}
		}
	}
}
