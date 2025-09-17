package com.github.spud.tinystore.inventory.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预留过期扫描调度（占位） 流程：扫描 reservation state=PENDING 且 expireAt<now -> 回收 reserved -> 标记 EXPIRED -> 发送事件
 */
@Component
public class ReservationExpiryScheduler {

	@Scheduled(fixedDelayString = "${inventory.reservation.expireScan.fixedDelay:60000}")
	public void scanAndExpire() {
		// 占位：批量查询并处理过期预留
	}
}

