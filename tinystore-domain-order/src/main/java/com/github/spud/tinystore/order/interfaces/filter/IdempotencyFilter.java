package com.github.spud.tinystore.order.interfaces.filter;

import com.github.spud.tinystore.order.interfaces.util.IdempotencyHelper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 幂等性和关联ID处理过滤器 自动提取请求头并设置到 MDC 中，用于日志追踪
 *
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
@Component
@Order(1) // 高优先级，确保在其他过滤器之前执行
public class IdempotencyFilter implements Filter {

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
			// 提取并设置幂等性键和关联ID到 MDC
			String idempotencyKey = IdempotencyHelper.extractAndSetContext(httpRequest);

			// 将关联ID添加到响应头，便于客户端追踪
			String correlationId = IdempotencyHelper.getCurrentCorrelationId();
			if (StringUtils.hasText(correlationId)) {
				httpResponse.setHeader(IdempotencyHelper.CORRELATION_ID_HEADER, correlationId);
			}

			// 如果有幂等性键，也添加到响应头
			if (StringUtils.hasText(idempotencyKey)) {
				httpResponse.setHeader(IdempotencyHelper.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
			}

			// 继续处理请求
			chain.doFilter(request, response);

		} finally {
			// 清理 MDC，避免内存泄漏
			IdempotencyHelper.clearContext();
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		log.info("IdempotencyFilter initialized");
	}

	@Override
	public void destroy() {
		log.info("IdempotencyFilter destroyed");
	}
}