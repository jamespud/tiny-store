package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.repository.ReservationRepository;
import com.github.spud.tinystore.inventory.domain.service.impl.InventoryDomainServiceImpl;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预留过期回收任务 (Phase1 简化实现)
 */
@Component
public class ReservationExpireScheduler {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpireScheduler.class);
	private final ReservationRepository reservationRepository;
	private final InventoryDomainServiceImpl domainService;
	private final int batchSize = 200; // 可配置
	private final int maxLoops = 5;    // 防止长时间占用

	public ReservationExpireScheduler(ReservationRepository reservationRepository,
		InventoryDomainServiceImpl domainService) {
		this.reservationRepository = reservationRepository;
		this.domainService = domainService;
	}

	@Scheduled(fixedDelay = 5000L)
	public void scan() {
		int loops = 0;
		int processed = 0;
		while (loops < maxLoops) {
			List<Reservation> list = reservationRepository.findPendingExpired(Instant.now(), batchSize);
			if (list.isEmpty()) {
				break;
			}
			for (Reservation r : list) {
				domainService.expireReservation(r.getReservationId());
				processed++;
			}
			if (list.size() < batchSize) {
				break;
			}
			loops++;
		}
		if (processed > 0) {
			log.info("expire 扫描回收 {} 条", processed);
		}
	}
}

