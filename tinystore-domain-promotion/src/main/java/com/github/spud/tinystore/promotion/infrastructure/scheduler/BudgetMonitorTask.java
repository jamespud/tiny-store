package com.github.spud.tinystore.promotion.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;

public class BudgetMonitorTask {

	@Scheduled(fixedDelayString = "PT5M")
	public void monitorBudget() {
		// TODO: Calculate budget usage ratio, emit warnings when exceeding threshold.
	}
}
