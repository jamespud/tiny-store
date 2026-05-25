package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.application.service.InventoryReservationAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Canonical inventory reservation expiry scheduler.
 * <p>
 * Only processes PRE_DEDUCTED reservations whose expire_at has passed.
 * EXPIRED status is produced only here — Order domain must NOT write EXPIRED directly.
 * <p>
 * Enabled/disabled via: inventory.reservation.expiry.enabled
 */
@Component
@ConditionalOnProperty(name = "inventory.reservation.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryTask {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpiryTask.class);

	private final InventoryReservationAppService reservationAppService;

	public ReservationExpiryTask(InventoryReservationAppService reservationAppService) {
		this.reservationAppService = reservationAppService;
	}

	@Scheduled(fixedDelayString = "${inventory.reservation.expiry.fixed-delay:PT1M}")
	public void expireQuotes() {
		int expired = reservationAppService.expireExpiredReservations();
		if (expired > 0) {
			log.info("Expired {} PRE_DEDUCTED reservations", expired);
		}
	}
}

