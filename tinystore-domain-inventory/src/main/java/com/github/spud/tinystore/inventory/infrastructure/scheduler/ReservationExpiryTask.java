package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryTask {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpiryTask.class);

	private final StockAppService stockAppService;

	public ReservationExpiryTask(StockAppService stockAppService) {
		this.stockAppService = stockAppService;
	}

	@Scheduled(fixedDelayString = "PT1M")
	public void expireQuotes() {
		int expired = stockAppService.expireReservations();
		if (expired > 0) {
			log.info("expired reservations: {}", expired);
		}
	}
}

