package com.github.spud.tinystore.order.infrastructure.compensation;

import com.github.spud.tinystore.order.domain.service.ResourceReleaseService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资源释放补偿扫描
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceReleaseCompensationService {

	private final ResourceReleaseTaskRepository repository;
	private final ResourceReleaseService resourceReleaseService;

	private static final int MAX_RETRY = 10;

	@Scheduled(fixedDelayString = "${order.compensation.resource-release.interval-ms:15000}")
	public void scanAndRetry() {
		var tasks = repository.findTop100ByCompletedFalseAndNextRetryTimeBeforeOrderByNextRetryTimeAsc(
			OffsetDateTime.now());
		for (var task : tasks) {
			try {
				// 简化：调用释放全部资源（未来可用 resourceType 拆分）
				resourceReleaseService.releaseByOrderId(task.getOrderId());
				task.setCompleted(true);
				task.setLastError(null);
			} catch (Exception ex) {
				task.setRetryCount(task.getRetryCount() + 1);
				task.setLastError(ex.getMessage());
				if (task.getRetryCount() >= MAX_RETRY) {
					log.error("资源释放补偿达到最大重试 orderId={} taskId={}", task.getOrderId(),
						task.getId());
					task.setCompleted(true); // 标记终止，人工处理
				} else {
					long delaySec = (long) Math.min(300, Math.pow(2, task.getRetryCount()));
					task.setNextRetryTime(OffsetDateTime.now().plusSeconds(delaySec));
				}
			}
			repository.save(task);
		}
	}
}