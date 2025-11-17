package com.github.spud.tinystore.order.interfaces.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import com.github.spud.tinystore.order.domain.exception.OrderDomainException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 幂等性和关联ID处理工具类
 *
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
public class IdempotencyHelper {

	// Header 名称常量
	public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
	public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
	public static final String REQUEST_ID_HEADER = "X-Request-ID";

	// MDC 键名常量
	public static final String MDC_CORRELATION_ID = "correlationId";
	public static final String MDC_IDEMPOTENCY_KEY = "idempotencyKey";
	public static final String MDC_REQUEST_ID = "requestId";

	/**
	 * 从请求中提取并设置幂等性键和关联ID到 MDC 用于日志记录和追踪
	 *
	 * @param request HTTP 请求
	 * @return 提取的幂等性键（如果存在）
	 */
	public static String extractAndSetContext(HttpServletRequest request) {
		// 提取或生成关联ID
		String correlationId = request.getHeader(CORRELATION_ID_HEADER);
		if (!StringUtils.hasText(correlationId)) {
			correlationId = UUID.randomUUID().toString();
		}

		// 提取或生成请求ID
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (!StringUtils.hasText(requestId)) {
			requestId = UUID.randomUUID().toString();
		}

		// 提取幂等性键
		String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

		// 设置到 MDC，用于日志追踪
		MDC.put(MDC_CORRELATION_ID, correlationId);
		MDC.put(MDC_REQUEST_ID, requestId);
		if (StringUtils.hasText(idempotencyKey)) {
			MDC.put(MDC_IDEMPOTENCY_KEY, idempotencyKey);
		}

		// 记录请求开始日志（包含幂等性信息）
		if (StringUtils.hasText(idempotencyKey)) {
			log.info("Request started with idempotency key: {} (gateway enforced)", idempotencyKey);
		} else {
			log.debug("Request started without idempotency key");
		}

		return idempotencyKey;
	}

	/**
	 * 清理 MDC 上下文 避免内存泄漏
	 */
	public static void clearContext() {
		MDC.remove(MDC_CORRELATION_ID);
		MDC.remove(MDC_REQUEST_ID);
		MDC.remove(MDC_IDEMPOTENCY_KEY);
	}

	/**
	 * 获取当前的关联ID
	 *
	 * @return 关联ID
	 */
	public static String getCurrentCorrelationId() {
		return MDC.get(MDC_CORRELATION_ID);
	}

	/**
	 * 获取当前的幂等性键
	 *
	 * @return 幂等性键（可能为 null）
	 */
	public static String getCurrentIdempotencyKey() {
		return MDC.get(MDC_IDEMPOTENCY_KEY);
	}

	/**
	 * 获取当前的请求ID
	 *
	 * @return 请求ID
	 */
	public static String getCurrentRequestId() {
		return MDC.get(MDC_REQUEST_ID);
	}

	/**
	 * 记录幂等性相关操作日志
	 *
	 * @param operation 操作名称
	 * @param orderId   订单ID
	 * @param message   消息
	 */
	public static void logIdempotencyOperation(String operation, String orderId, String message) {
		String idempotencyKey = getCurrentIdempotencyKey();
		if (StringUtils.hasText(idempotencyKey)) {
			log.info("Idempotency operation: {} for order: {}, key: {}, message: {}",
				operation, orderId, idempotencyKey, message);
		} else {
			log.debug("Operation: {} for order: {}, message: {} (no idempotency key)",
				operation, orderId, message);
		}
	}

	/**
	 * 验证请求是否包含必需的幂等性键 注意：这只用于日志记录，不用于强制执行（由网关处理）
	 *
	 * @param requireIdempotency 是否需要幂等性键
	 * @return 是否包含幂等性键
	 */
	public static boolean validateIdempotencyKey(boolean requireIdempotency) {
		String idempotencyKey = getCurrentIdempotencyKey();
		boolean hasKey = StringUtils.hasText(idempotencyKey);

		if (requireIdempotency && !hasKey) {
			log.warn(
				"Operation requires idempotency key but none provided (should be caught by gateway)");
		}

		return hasKey;
	}

    /**
     * 严格要求必须存在幂等性键，不存在则抛业务异常（400）。
     */
    public static void requireIdempotencyKey() {
        String idempotencyKey = getCurrentIdempotencyKey();
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new OrderDomainException("Missing X-Idempotency-Key header", "ORDER-4001");
        }
    }
}