package com.github.spud.tinystore.inventory.domain.service.impl;

import com.github.spud.tinystore.inventory.domain.command.AdjustTotalCommand;
import com.github.spud.tinystore.inventory.domain.command.BatchAvailableQuery;
import com.github.spud.tinystore.inventory.domain.command.ConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.ReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.ReserveCommand;
import com.github.spud.tinystore.inventory.domain.event.StockEvent;
import com.github.spud.tinystore.inventory.domain.event.StockEventType;
import com.github.spud.tinystore.inventory.domain.exception.InventoryBusinessException;
import com.github.spud.tinystore.inventory.domain.exception.InventoryErrorCode;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;
import com.github.spud.tinystore.inventory.domain.model.StockAggregate;
import com.github.spud.tinystore.inventory.domain.repository.ReservationRepository;
import com.github.spud.tinystore.inventory.domain.repository.StockRepository;
import com.github.spud.tinystore.inventory.domain.service.InventoryDomainService;
import com.github.spud.tinystore.inventory.infrastructure.kafka.StockEventProducer;
import com.github.spud.tinystore.inventory.infrastructure.redis.IdempotentReservationCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * InventoryDomainService 实现 (Phase1)
 */
@Service
public class InventoryDomainServiceImpl implements InventoryDomainService {

	private static final Logger log = LoggerFactory.getLogger(InventoryDomainServiceImpl.class);
	private static final int MAX_RETRY = 3;

	private final StockRepository stockRepository;
	private final ReservationRepository reservationRepository;
	private final IdempotentReservationCache idempotentCache;
	private final StockEventProducer eventProducer;

	public InventoryDomainServiceImpl(StockRepository stockRepository,
		ReservationRepository reservationRepository,
		IdempotentReservationCache idempotentCache,
		StockEventProducer eventProducer) {
		this.stockRepository = stockRepository;
		this.reservationRepository = reservationRepository;
		this.idempotentCache = idempotentCache;
		this.eventProducer = eventProducer;
	}

	@Override
	@Transactional
	public Reservation reserve(ReserveCommand cmd) {
		String opId = cmd.getOperationId();
		if (opId != null) {
			Optional<String> existing = idempotentCache.getReservationId(opId);
			if (existing.isPresent()) {
				Reservation found = reservationRepository.find(existing.get())
					.orElseThrow(
						() -> new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_NOT_FOUND,
							"幂等 reservation 丢失"));
				return found;
			}
		}
		StockAggregate stock = stockRepository.find(cmd.getShopId(), cmd.getSkuId())
			.orElseThrow(
				() -> new InventoryBusinessException(InventoryErrorCode.INV_STOCK_NOT_FOUND, "库存不存在"));
		int attempts = 0;
		while (true) {
			attempts++;
			long originalVersion = stock.getVersion();
			// 先在内存中尝试预留（会修改 reservedQuantity）
			Reservation reservation = stock.reserve(cmd.getQuantity(), cmd.getExpireSeconds(), opId);
			boolean updated = stockRepository.update(stock); // 尝试乐观锁更新 stock（包含 reservedQuantity 增加）
			if (updated) {
				stock.setVersion(originalVersion + 1);
				reservationRepository.insert(reservation); // 库存成功后再持久化 reservation
				if (opId != null) {
					idempotentCache.put(opId, reservation.getReservationId(), cmd.getExpireSeconds());
				}
				publish(buildEvent(StockEventType.RESERVED, stock.getShopId(), stock.getSkuId(),
					reservation.getReservationId(), null, reservation.getQuantity(),
					reservation.getQuantity(), stock.getVersion(), opId));
				return reservation;
			}
			if (attempts >= MAX_RETRY) {
				throw new InventoryBusinessException(InventoryErrorCode.INV_AVAILABLE_NOT_ENOUGH,
					"预留重试失败(冲突过多)");
			}
			// 重载最新 stock，放弃该 reservation 对象（未持久化）
			stock = stockRepository.find(cmd.getShopId(), cmd.getSkuId())
				.orElseThrow(() -> new InventoryBusinessException(InventoryErrorCode.INV_STOCK_NOT_FOUND,
					"库存不存在(重试)"));
		}
	}

	@Override
	@Transactional
	public void confirm(ConfirmCommand cmd) {
		Reservation r = reservationRepository.find(cmd.getReservationId())
			.orElseThrow(
				() -> new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_NOT_FOUND,
					"reservation 不存在"));
		if (r.getState() != ReservationState.PENDING) {
			return;
		}
		StockAggregate stock = stockRepository.find(r.getShopId(), r.getSkuId())
			.orElseThrow(
				() -> new InventoryBusinessException(InventoryErrorCode.INV_STOCK_NOT_FOUND, "库存不存在"));
		int attempts = 0;
		while (true) {
			attempts++;
			long originalVersion = stock.getVersion();
			stock.confirm(r); // 内存迁移
			boolean updated = stockRepository.update(stock);
			if (updated) {
				stock.setVersion(originalVersion + 1);
				boolean stateOk = reservationRepository.updateState(r.getReservationId(),
					ReservationState.PENDING, ReservationState.CONFIRMED, r.getVersion(), null);
				if (!stateOk) {
					log.warn("Reservation state CAS 失败 reservationId={}", r.getReservationId());
				}
				publish(buildEvent(StockEventType.CONFIRMED, stock.getShopId(), stock.getSkuId(),
					r.getReservationId(), -r.getQuantity(), -r.getQuantity(), r.getQuantity(),
					stock.getVersion(), r.getOperationId()));
				return;
			}
			if (attempts >= MAX_RETRY) {
				throw new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_STATE_INVALID,
					"confirm 冲突过多");
			}
			stock = stockRepository.find(r.getShopId(), r.getSkuId()).orElseThrow();
		}
	}

	@Override
	@Transactional
	public void release(ReleaseCommand cmd) {
		Reservation r = reservationRepository.find(cmd.getReservationId())
			.orElseThrow(
				() -> new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_NOT_FOUND,
					"reservation 不存在"));
		if (r.getState() != ReservationState.PENDING) {
			return;
		}
		StockAggregate stock = stockRepository.find(r.getShopId(), r.getSkuId())
			.orElseThrow(
				() -> new InventoryBusinessException(InventoryErrorCode.INV_STOCK_NOT_FOUND, "库存不存在"));
		int attempts = 0;
		while (true) {
			attempts++;
			long originalVersion = stock.getVersion();
			stock.release(r, cmd.getReason());
			boolean updated = stockRepository.update(stock);
			if (updated) {
				stock.setVersion(originalVersion + 1);
				reservationRepository.updateState(r.getReservationId(), ReservationState.PENDING,
					ReservationState.RELEASED, r.getVersion(), cmd.getReason());
				publish(buildEvent(StockEventType.RELEASED, stock.getShopId(), stock.getSkuId(),
					r.getReservationId(), null, -r.getQuantity(), r.getQuantity(), stock.getVersion(),
					r.getOperationId()));
				return;
			}
			if (attempts >= MAX_RETRY) {
				throw new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_STATE_INVALID,
					"release 冲突过多");
			}
			stock = stockRepository.find(r.getShopId(), r.getSkuId()).orElseThrow();
		}
	}

	@Override
	@Transactional
	public void adjustTotal(AdjustTotalCommand cmd) {
		StockAggregate stock = stockRepository.find(cmd.getShopId(), cmd.getSkuId())
			.orElseGet(() -> new StockAggregate(cmd.getShopId(), cmd.getSkuId(), 0L, 0L, 0L));
		int attempts = 0;
		boolean inserted =
			stock.getVersion() == 0 && stock.getTotalQuantity() == 0 && stock.getReservedQuantity() == 0;
		while (true) {
			attempts++;
			long originalVersion = stock.getVersion();
			stock.adjustTotal(cmd.getDelta(), cmd.getReason());
			if (inserted) {
				try {
					stockRepository.insert(stock);
					publish(buildEvent(StockEventType.TOTAL_ADJUST, stock.getShopId(), stock.getSkuId(), null,
						cmd.getDelta(), null, null, stock.getVersion(), null));
					return;
				} catch (Exception ex) {
					inserted = false;
				}
			}
			boolean ok = stockRepository.update(stock);
			if (ok) {
				stock.setVersion(originalVersion + 1);
				publish(buildEvent(StockEventType.TOTAL_ADJUST, stock.getShopId(), stock.getSkuId(), null,
					cmd.getDelta(), null, null, stock.getVersion(), null));
				return;
			}
			if (attempts >= MAX_RETRY) {
				throw new InventoryBusinessException(InventoryErrorCode.INV_ADJUST_ILLEGAL,
					"adjust 冲突过多");
			}
			stock = stockRepository.find(cmd.getShopId(), cmd.getSkuId()).orElseThrow();
		}
	}

	@Override
	public Map<String, Long> batchAvailable(BatchAvailableQuery query) {
		List<StockRepository.ShopSkuKey> keys = new ArrayList<>();
		for (BatchAvailableQuery.Item i : query.getItems()) {
			keys.add(new StockRepository.ShopSkuKey(i.getShopId(), i.getSkuId()));
		}
		List<StockAggregate> list = stockRepository.batchFind(keys);
		Map<String, Long> map = new HashMap<>();
		for (StockAggregate s : list) {
			map.put(s.getShopId() + ":" + s.getSkuId(), s.getAvailable());
		}
		for (StockRepository.ShopSkuKey k : keys) {
			map.putIfAbsent(k.shopId() + ":" + k.skuId(), 0L);
		}
		// TODO Phase2: 缓存预热 & Lua 批量可用路径
		return map;
	}

	@Override
	public long available(String shopId, String skuId) {
		return stockRepository.find(shopId, skuId).map(StockAggregate::getAvailable).orElse(0L);
	}

	private void publish(StockEvent event) {
		try {
			eventProducer.send(event);
		} catch (Exception e) {
			log.warn("事件发送异常", e);
		}
	}

	private StockEvent buildEvent(StockEventType type, String shopId, String skuId,
		String reservationId,
		Long deltaTotal, Long deltaReserved, Long quantity, long version, String correlationId) {
		return new StockEvent(type, shopId, skuId, reservationId, deltaTotal, deltaReserved, quantity,
			version, correlationId);
	}

	@Transactional
	public void expireReservation(String reservationId) {
		reservationRepository.find(reservationId).ifPresent(r -> {
			if (r.getState() != ReservationState.PENDING) {
				return;
			}
			StockAggregate stock = stockRepository.find(r.getShopId(), r.getSkuId()).orElse(null);
			if (stock == null) {
				return;
			}
			int attempts = 0;
			boolean done = false;
			while (!done) {
				attempts++;
				long originalVersion = stock.getVersion();
				stock.expire(r);
				boolean ok = stockRepository.update(stock);
				if (ok) {
					stock.setVersion(originalVersion + 1);
					reservationRepository.updateState(r.getReservationId(), ReservationState.PENDING,
						ReservationState.EXPIRED, r.getVersion(), null);
					publish(buildEvent(StockEventType.EXPIRED, stock.getShopId(), stock.getSkuId(),
						r.getReservationId(), null, -r.getQuantity(), r.getQuantity(), stock.getVersion(),
						r.getOperationId()));
					done = true;
				} else if (attempts >= MAX_RETRY) {
					log.warn("expire 冲突过多 reservationId={}", r.getReservationId());
					return;
				} else {
					stock = stockRepository.find(r.getShopId(), r.getSkuId()).orElse(null);
					if (stock == null) {
						return;
					}
				}
			}
		});
	}
}

