package com.github.spud.tinystore.product.interfaces.filter;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "tinystore.product.shop-context-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShopContextFilter implements Filter {

	private static final String SHOP_HEADER = "X-Shop-Id";

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
		// Trusted internal endpoints (e.g. order -> product SKU price lookup) carry an explicit
		// shopId parameter; they must not be forced to present a client-controlled X-Shop-Id header.
		if (httpRequest.getRequestURI().startsWith("/internal/")) {
			chain.doFilter(request, response);
			return;
		}
		// Validate: X-Shop-Id header is required for business endpoints.
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

		chain.doFilter(request, response);
	}
}
