package com.github.spud.tinystore.inventory.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import com.github.spud.tinystore.inventory.domain.command.ReserveCommand;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;
import com.github.spud.tinystore.inventory.domain.model.StockAggregate;
import com.github.spud.tinystore.inventory.domain.repository.ReservationRepository;
import com.github.spud.tinystore.inventory.domain.repository.StockRepository;
import com.github.spud.tinystore.inventory.domain.service.impl.InventoryDomainServiceImpl;
import com.github.spud.tinystore.inventory.infrastructure.kafka.StockEventProducer;
import com.github.spud.tinystore.inventory.infrastructure.redis.IdempotentReservationCache;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class InventoryDomainServiceIdempotentTest {

	private InventoryDomainServiceImpl service;
	private InMemoryStockRepository stockRepository;
	private InMemoryReservationRepository reservationRepository;
	private IdempotentReservationCache idempotentCache;

	@BeforeEach
	void setup() {
		stockRepository = new InMemoryStockRepository();
		reservationRepository = new InMemoryReservationRepository();
		Map<String, String> map = new HashMap<>();
		idempotentCache = Mockito.mock(IdempotentReservationCache.class);
		Mockito.when(idempotentCache.getReservationId(anyString()))
			.thenAnswer(inv -> Optional.ofNullable(map.get(inv.getArgument(0))));
		Mockito.doAnswer(inv -> {
			map.put(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(idempotentCache).put(anyString(), anyString(), anyInt());
		StockEventProducer producer = Mockito.mock(StockEventProducer.class);
		StockAggregate stock = new StockAggregate("s1", "sku1", 100, 0, 0);
		stockRepository.insert(stock);
		service = new InventoryDomainServiceImpl(stockRepository, reservationRepository,
			idempotentCache, producer);
	}

	@Test
	void reserveIdempotent() {
		ReserveCommand cmd = ReserveCommand.builder().shopId("s1").skuId("sku1").quantity(10)
			.expireSeconds(60).operationId("op1").build();
		Reservation r1 = service.reserve(cmd);
		Reservation r2 = service.reserve(cmd);
		Assertions.assertEquals(r1.getReservationId(), r2.getReservationId());
		StockAggregate stock = stockRepository.data.values().iterator().next();
		Assertions.assertEquals(10, stock.getReservedQuantity());
		Assertions.assertEquals(90, stock.getAvailable());
	}

	// In-memory repository implementations
	static class InMemoryStockRepository implements StockRepository {

		Map<String, StockAggregate> data = new HashMap<>();

		private String key(String shopId, String skuId) {
			return shopId + "|" + skuId;
		}

		@Override
		public Optional<StockAggregate> find(String shopId, String skuId) {
			return Optional.ofNullable(data.get(key(shopId, skuId)));
		}

		@Override
		public void insert(StockAggregate aggregate) {
			data.put(key(aggregate.getShopId(), aggregate.getSkuId()), aggregate);
		}

		@Override
		public boolean update(StockAggregate aggregate) {
			StockAggregate cur = data.get(key(aggregate.getShopId(), aggregate.getSkuId()));
			if (cur == null) {
				return false;
			}
			if (cur.getVersion() != aggregate.getVersion()) {
				return false;
			}
			// 乐观锁成功 -> 增加版本
			aggregate.setVersion(aggregate.getVersion() + 1);
			cur.setVersion(aggregate.getVersion());
			// quantities 已在对象中修改
			return true;
		}

		@Override
		public List<StockAggregate> batchFind(List<ShopSkuKey> keys) {
			List<StockAggregate> list = new ArrayList<>();
			for (ShopSkuKey k : keys) {
				find(k.shopId(), k.skuId()).ifPresent(list::add);
			}
			return list;
		}
	}

	static class InMemoryReservationRepository implements ReservationRepository {

		Map<String, Reservation> data = new HashMap<>();

		@Override
		public void insert(Reservation r) {
			data.put(r.getReservationId(), r);
		}

		@Override
		public Optional<Reservation> find(String reservationId) {
			return Optional.ofNullable(data.get(reservationId));
		}

		@Override
		public boolean updateState(String reservationId, ReservationState expect,
			ReservationState target, long version, String releaseReason) {
			return false;
		}

		@Override
		public boolean updateState(String reservationId,
			com.github.spud.tinystore.inventory.domain.model.ReservationState expect,
			com.github.spud.tinystore.inventory.domain.model.ReservationState target, long version) {
			Reservation r = data.get(reservationId);
			if (r == null) {
				return false;
			}
			if (r.getState() != expect) {
				return false;
			}
			return true;
		}

		@Override
		public List<Reservation> findPendingExpired(Instant cutoff, int limit) {
			return List.of();
		}
	}
}

