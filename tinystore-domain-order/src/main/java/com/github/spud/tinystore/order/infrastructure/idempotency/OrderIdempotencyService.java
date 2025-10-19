package com.github.spud.tinystore.order.infrastructure.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderIdempotencyPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderIdempotencyRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单幂等性服务 基于 requestId 确保接口调用的幂等性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

	private final OrderIdempotencyRepository idempotencyRepository;
	private final ObjectMapper objectMapper;

	/**
	 * 检查并创建幂等性记录
	 *
	 * @param requestId 请求唯一标识
	 * @param orderNo   订单号
	 * @param operation 操作类型
	 * @return 如果是重复请求则返回已有结果，否则返回空
	 */
	@Transactional
	public Optional<IdempotencyResult> checkAndCreateIdempotency(String requestId,
		String orderNo,
		String operation) {
		// 查找有效的幂等性记录
		Optional<OrderIdempotencyPO> existingRecord =
			idempotencyRepository.findValidIdempotencyRecord(requestId, OffsetDateTime.now());

		if (existingRecord.isPresent()) {
			OrderIdempotencyPO record = existingRecord.get();

			// 如果已经完成，返回之前的结果
			if (record.getStatus() == OrderIdempotencyPO.IdempotencyStatus.COMPLETED) {
				log.info("Idempotent request found with completed result: requestId={}, orderNo={}",
					requestId, record.getOrderNo());

				return Optional.of(new IdempotencyResult(
					true,
					record.getOrderNo(),
					record.getResponseData()
				));
			}

			// 如果正在处理中，返回处理中状态
			if (record.getStatus() == OrderIdempotencyPO.IdempotencyStatus.PROCESSING) {
				log.info("Idempotent request found in processing: requestId={}, orderNo={}",
					requestId, record.getOrderNo());

				return Optional.of(new IdempotencyResult(
					true,
					record.getOrderNo(),
					null // 处理中，无响应数据
				));
			}
		}

		// 创建新的幂等性记录
		OrderIdempotencyPO newRecord = new OrderIdempotencyPO()
			.setRequestId(requestId)
			.setOrderNo(orderNo)
			.setOperation(operation)
			.setStatus(OrderIdempotencyPO.IdempotencyStatus.PROCESSING)
			.setTraceId(MDC.get("traceId"));

		idempotencyRepository.save(newRecord);

		log.info("Created new idempotency record: requestId={}, orderNo={}, operation={}",
			requestId, orderNo, operation);

		return Optional.empty(); // 不是重复请求
	}

	/**
	 * 完成幂等性处理，保存响应结果
	 *
	 * @param requestId    请求唯一标识
	 * @param responseData 响应数据
	 */
	@Transactional
	public void completeIdempotency(String requestId, Object responseData) {
		try {
			String responseJson = null;
			if (responseData != null) {
				responseJson = objectMapper.writeValueAsString(responseData);
			}

			idempotencyRepository.updateStatusAndResponse(
				requestId,
				OrderIdempotencyPO.IdempotencyStatus.COMPLETED,
				responseJson
			);

			log.info("Completed idempotency processing: requestId={}", requestId);

		} catch (JsonProcessingException e) {
			log.error("Failed to serialize response data: requestId={}", requestId, e);

			// 即使序列化失败，也要标记为完成状态
			idempotencyRepository.updateStatusAndResponse(
				requestId,
				OrderIdempotencyPO.IdempotencyStatus.COMPLETED,
				null
			);
		}
	}

	/**
	 * 标记幂等性处理失败
	 *
	 * @param requestId 请求唯一标识
	 */
	@Transactional
	public void failIdempotency(String requestId) {
		idempotencyRepository.updateStatusAndResponse(
			requestId,
			OrderIdempotencyPO.IdempotencyStatus.FAILED,
			null
		);

		log.info("Failed idempotency processing: requestId={}", requestId);
	}

	/**
	 * 清理过期的幂等性记录
	 */
	@Transactional
	public void cleanExpiredRecords() {
		idempotencyRepository.cleanExpiredRecords(OffsetDateTime.now());
		log.info("Cleaned expired idempotency records");
	}

	/**
	 * 查找超时的处理中记录
	 *
	 * @param timeoutMinutes 超时时间（分钟）
	 * @return 超时的记录列表
	 */
	@Transactional(readOnly = true)
	public java.util.List<OrderIdempotencyPO> findTimeoutProcessingRecords(int timeoutMinutes) {
		OffsetDateTime timeoutThreshold = OffsetDateTime.now().minusMinutes(timeoutMinutes);
		return idempotencyRepository.findTimeoutProcessingRecords(timeoutThreshold);
	}

	/**
	 * 幂等性检查结果
	 */
	public static class IdempotencyResult {

		private final boolean isDuplicate;
		private final String orderNo;
		private final String responseData;

		public IdempotencyResult(boolean isDuplicate, String orderNo, String responseData) {
			this.isDuplicate = isDuplicate;
			this.orderNo = orderNo;
			this.responseData = responseData;
		}

		public boolean isDuplicate() {
			return isDuplicate;
		}

		public String getOrderNo() {
			return orderNo;
		}

		public String getResponseData() {
			return responseData;
		}

		public boolean isProcessing() {
			return isDuplicate && responseData == null;
		}
	}
}