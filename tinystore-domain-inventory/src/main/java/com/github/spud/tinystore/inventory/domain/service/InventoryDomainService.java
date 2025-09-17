package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.AdjustTotalCommand;
import com.github.spud.tinystore.inventory.domain.command.BatchAvailableQuery;
import com.github.spud.tinystore.inventory.domain.command.ConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.ReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.ReserveCommand;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import java.util.Map;

/**
 * 领域服务接口：聚合装载、幂等、事件、仓储协调。
 */
public interface InventoryDomainService {

	Reservation reserve(ReserveCommand cmd);

	void confirm(ConfirmCommand cmd);

	void release(ReleaseCommand cmd);

	void adjustTotal(AdjustTotalCommand cmd);

	Map<String, Long> batchAvailable(BatchAvailableQuery query);

	long available(String shopId, String skuId);
}

