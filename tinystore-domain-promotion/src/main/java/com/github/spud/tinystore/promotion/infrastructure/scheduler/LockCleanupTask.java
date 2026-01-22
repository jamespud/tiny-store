package com.github.spud.tinystore.promotion.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LockCleanupTask {

	@Scheduled(fixedDelayString = "PT10M")
	public void cleanupExpiredLocks() {
		// TODO: Scan locked records, restore stock, clear Redis locks.
	}
}
