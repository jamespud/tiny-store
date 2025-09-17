package com.github.spud.tinystore.inventory.service;

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

public class InventoryDomainServiceExpireTest {

	private InventoryDomainServiceImpl service;
	private InMemoryStockRepository stockRepository;
	private InMemoryReservationRepository reservationRepository;

	@BeforeEach
	void setup() {
		stockRepository = new InMemoryStockRepository();
		reservationRepository = new InMemoryReservationRepository();
		IdempotentReservationCache idempotentCache = Mockito.mock(IdempotentReservationCache.class);
		StockEventProducer producer = Mockito.mock(StockEventProducer.class);
		StockAggregate stock = new StockAggregate("s1", "sku1", 100, 0, 0);
		stockRepository.insert(stock);

		// 构造已预留且过期的 reservation
		Reservation r = stock.reserve(20, 60, null);
		// 将 expireAt 改为过去
		Reservation expired = new Reservation(r.getReservationId(), r.getShopId(), r.getSkuId(),
			r.getQuantity(), r.getState(), Instant.now().minusSeconds(10), r.getOperationId(),
			r.getCreatedAt(), r.getUpdatedAt(), r.getVersion());
		reservationRepository.insert(expired);
		service = new InventoryDomainServiceImpl(stockRepository, reservationRepository,
			idempotentCache, producer);
	}

	@Test
	void expireReservationReducesReserved() {
		StockAggregate before = stockRepository.data.values().iterator().next();
		Assertions.assertEquals(20, before.getReservedQuantity());
		service.expireReservation(reservationRepository.data.keySet().iterator().next());
		StockAggregate after = stockRepository.data.values().iterator().next();
		Assertions.assertEquals(0, after.getReservedQuantity());
		Assertions.assertEquals(100, after.getTotalQuantity());
	}

	// In-memory repository implementations (简化，与 IdempotentTest 类似)
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
			aggregate.setVersion(aggregate.getVersion() + 1);
			cur.setVersion(aggregate.getVersion());
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
			return true;
		}

		@Override
		public List<Reservation> findPendingExpired(Instant cutoff, int limit) {
			return List.of();
		}
	}
}

