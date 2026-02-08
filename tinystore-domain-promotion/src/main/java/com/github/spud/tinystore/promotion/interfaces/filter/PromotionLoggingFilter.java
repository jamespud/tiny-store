package com.github.spud.tinystore.promotion.interfaces.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PromotionLoggingFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String traceId = firstNonBlank(request.getHeader("X-Trace-Id"), request.getHeader("Trace-Id"));
		if (traceId == null) {
			traceId = UUID.randomUUID().toString();
		}
		MDC.put("traceId", traceId);
		MDC.put(LogConstant.MDC_LOG_ID, traceId);
		putIfPresent("userId", firstNonBlank(request.getHeader("X-User-Id"), request.getHeader("User-Id")));
		putIfPresent("tradeId", firstNonBlank(request.getHeader("X-Trade-Id"), firstNonBlank(request.getHeader("X-Order-No"), request.getHeader("Order-No"))));
		putIfPresent("quoteId", firstNonBlank(request.getHeader("X-Quote-Id"), request.getHeader("Quote-Id")));
		response.setHeader("X-Trace-Id", traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.clear();
		}
	}

	private void putIfPresent(String k, String v) {
		if (v != null && !v.isBlank()) {
			MDC.put(k, v);
		}
	}

	private String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) {
			return a;
		}
		if (b != null && !b.isBlank()) {
			return b;
		}
		return null;
	}
}
