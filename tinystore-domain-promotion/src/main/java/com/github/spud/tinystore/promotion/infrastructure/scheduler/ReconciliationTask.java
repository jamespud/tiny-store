package com.github.spud.tinystore.promotion.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;

public class ReconciliationTask {

	@Scheduled(fixedDelayString = "PT5M")
	public void reconcileInventory() {
		// TODO: Compare DB stock with Redis stock and repair inconsistencies.
	}
}
