package com.github.spud.tinystore.order.infrastructure.audit;

import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderStatusAuditPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderStatusAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单状态审计服务
 * 记录订单状态变更的完整审计链路
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusAuditService {

	private final OrderStatusAuditRepository auditRepository;

	/**
	 * 记录状态变更审计
	 *
	 * @param orderNo    订单号
	 * @param fromStatus 变更前状态
	 * @param toStatus   变更后状态
	 * @param actorType  操作者类型
	 * @param actorId    操作者ID
	 * @param reason     变更原因
	 * @param eventId    关联的领域事件ID
	 */
	@Transactional
	public void recordStatusChange(String orderNo,
	                               String fromStatus,
	                               String toStatus,
	                               OrderStatusAuditPO.ActorType actorType,
	                               String actorId,
	                               String reason,
	                               UUID eventId) {
		OrderStatusAuditPO audit = new OrderStatusAuditPO()
			.setOrderNo(orderNo)
			.setFromStatus(fromStatus)
			.setToStatus(toStatus)
			.setActorType(actorType)
			.setActorId(actorId)
			.setReason(reason)
			.setEventId(eventId)
			.setTraceId(MDC.get("traceId"));

		auditRepository.save(audit);

		log.info("Recorded status change audit: orderNo={}, {}→{}, actor={}:{}, reason={}",
			orderNo, fromStatus, toStatus, actorType, actorId, reason);
	}

	/**
	 * 记录用户操作的状态变更
	 */
	@Transactional
	public void recordUserStatusChange(String orderNo,
	                                   String fromStatus,
	                                   String toStatus,
	                                   String userId,
	                                   String reason,
	                                   UUID eventId) {
		recordStatusChange(orderNo, fromStatus, toStatus,
			OrderStatusAuditPO.ActorType.USER, userId, reason, eventId);
	}

	/**
	 * 记录商户操作的状态变更
	 */
	@Transactional
	public void recordMerchantStatusChange(String orderNo,
	                                       String fromStatus,
	                                       String toStatus,
	                                       String merchantId,
	                                       String reason,
	                                       UUID eventId) {
		recordStatusChange(orderNo, fromStatus, toStatus,
			OrderStatusAuditPO.ActorType.MERCHANT, merchantId, reason, eventId);
	}

	/**
	 * 记录系统操作的状态变更
	 */
	@Transactional
	public void recordSystemStatusChange(String orderNo,
	                                     String fromStatus,
	                                     String toStatus,
	                                     String systemComponent,
	                                     String reason,
	                                     UUID eventId) {
		recordStatusChange(orderNo, fromStatus, toStatus,
			OrderStatusAuditPO.ActorType.SYSTEM, systemComponent, reason, eventId);
	}

	/**
	 * 查询订单的状态变更历史
	 *
	 * @param orderNo 订单号
	 * @return 状态变更历史列表（按时间倒序）
	 */
	@Transactional(readOnly = true)
	public List<OrderStatusAuditPO> getStatusChangeHistory(String orderNo) {
		return auditRepository.findByOrderNoOrderByCreatedAtDesc(orderNo);
	}

	/**
	 * 查询指定时间范围内的状态变更
	 *
	 * @param orderNo   订单号
	 * @param startTime 开始时间
	 * @param endTime   结束时间
	 * @return 状态变更列表（按时间倒序）
	 */
	@Transactional(readOnly = true)
	public List<OrderStatusAuditPO> getStatusChangeHistory(String orderNo,
	                                                       OffsetDateTime startTime,
	                                                       OffsetDateTime endTime) {
		return auditRepository.findByOrderNoAndTimeRange(orderNo, startTime, endTime);
	}

	/**
	 * 查询操作者的状态变更记录
	 *
	 * @param actorType 操作者类型
	 * @param actorId   操作者ID
	 * @return 状态变更列表（按时间倒序）
	 */
	@Transactional(readOnly = true)
	public List<OrderStatusAuditPO> getStatusChangesByActor(OrderStatusAuditPO.ActorType actorType,
	                                                        String actorId) {
		return auditRepository.findByActorTypeAndActorIdOrderByCreatedAtDesc(actorType, actorId);
	}

	/**
	 * 统计指定时间段内的状态变更次数
	 *
	 * @param orderNo 订单号
	 * @param since   统计起始时间
	 * @return 状态变更次数
	 */
	@Transactional(readOnly = true)
	public Long countStatusChangesSince(String orderNo, OffsetDateTime since) {
		return auditRepository.countStatusChangesSince(orderNo, since);
	}

	/**
	 * 获取订单的最后一次状态变更
	 *
	 * @param orderNo 订单号
	 * @return 最后一次状态变更记录
	 */
	@Transactional(readOnly = true)
	public OrderStatusAuditPO getLastStatusChange(String orderNo) {
		return auditRepository.findLastStatusChange(orderNo);
	}

	/**
	 * 检查订单是否存在频繁状态变更（可能的异常情况）
	 *
	 * @param orderNo         订单号
	 * @param timeWindowHours 时间窗口（小时）
	 * @param threshold       阈值
	 * @return 是否存在频繁变更
	 */
	@Transactional(readOnly = true)
	public boolean hasFrequentStatusChanges(String orderNo, int timeWindowHours, int threshold) {
		OffsetDateTime since = OffsetDateTime.now().minusHours(timeWindowHours);
		Long changeCount = countStatusChangesSince(orderNo, since);

		boolean isFrequent = changeCount > threshold;

		if (isFrequent) {
			log.warn("Frequent status changes detected: orderNo={}, changes={} in {}h",
				orderNo, changeCount, timeWindowHours);
		}

		return isFrequent;
	}

	/**
	 * 查询追踪链路相关的状态变更
	 *
	 * @param traceId 追踪ID
	 * @return 状态变更列表（按时间倒序）
	 */
	@Transactional(readOnly = true)
	public List<OrderStatusAuditPO> getStatusChangesByTrace(String traceId) {
		return auditRepository.findByTraceIdOrderByCreatedAtDesc(traceId);
	}
}