package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.AdjustTotalCommand;
import com.github.spud.tinystore.inventory.domain.command.ConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.ReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.ReserveCommand;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import org.springframework.stereotype.Service;

/**
 * 旧接口适配层。所有方法 @Deprecated 并转调新领域服务。
 */
@Service
public class DeprecatedLegacyInventoryService {

	private final InventoryDomainService domainService;

	public DeprecatedLegacyInventoryService(InventoryDomainService domainService) {
		this.domainService = domainService;
	}

	@Deprecated(forRemoval = true)
	public Reservation deductReserve(String shopId, String skuId, long quantity) {
		return domainService.reserve(ReserveCommand.builder()
			.shopId(shopId).skuId(skuId).quantity(quantity).expireSeconds(300).build());
	}

	@Deprecated(forRemoval = true)
	public Reservation increaseReserve(String shopId, String skuId, long quantity) {
		return deductReserve(shopId, skuId, quantity);
	}

	@Deprecated(forRemoval = true)
	public void increaseTotal(String shopId, String skuId, long quantity, String reason) {
		domainService.adjustTotal(
			AdjustTotalCommand.builder().shopId(shopId).skuId(skuId).delta(quantity).reason(reason)
				.build());
	}

	@Deprecated(forRemoval = true)
	public void decreaseTotal(String shopId, String skuId, long quantity, String reason) {
		domainService.adjustTotal(
			AdjustTotalCommand.builder().shopId(shopId).skuId(skuId).delta(-quantity).reason(reason)
				.build());
	}

	@Deprecated(forRemoval = true)
	public void confirmReservation(String reservationId) {
		domainService.confirm(ConfirmCommand.builder().reservationId(reservationId).build());
	}

	@Deprecated(forRemoval = true)
	public void releaseReservation(String reservationId, String reason) {
		domainService.release(
			ReleaseCommand.builder().reservationId(reservationId).reason(reason).build());
	}

	@Deprecated(forRemoval = true)
	public long available(String shopId, String skuId) {
		return domainService.available(shopId, skuId);
	}
}
