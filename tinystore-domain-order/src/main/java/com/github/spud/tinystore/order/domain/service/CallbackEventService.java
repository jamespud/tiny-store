package com.github.spud.tinystore.order.domain.service;

/**
 * 回调事件服务接口
 *
 * @author Spud
 * @date 2025/9/22
 */
public interface CallbackEventService {

	/**
	 * 确认事件只处理一次
	 *
	 * @param eventId 事件ID
	 * @param source  事件来源（如 payment、logistics、refund）
	 * @return true-首次处理，false-重复事件
	 */
	boolean ackOnce(String eventId, String source);

	/**
	 * 检查事件是否已处理
	 *
	 * @param eventId 事件ID
	 * @param source  事件来源
	 * @return true-已处理，false-未处理
	 */
	boolean isProcessed(String eventId, String source);
}