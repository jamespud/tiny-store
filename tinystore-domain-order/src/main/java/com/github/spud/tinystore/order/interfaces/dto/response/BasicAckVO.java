package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基础响应VO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasicAckVO {
	/**
	 * 状态
	 */
	private String status;

	/**
	 * 消息
	 */
	private String message;

	/**
	 * 追踪ID（可选）
	 */
	private String traceId;
}