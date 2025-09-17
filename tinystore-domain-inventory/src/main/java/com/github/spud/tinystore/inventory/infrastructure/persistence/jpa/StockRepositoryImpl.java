package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa;

import com.github.spud.tinystore.inventory.domain.model.StockAggregate;
import com.github.spud.tinystore.inventory.domain.repository.StockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StockRepositoryImpl implements StockRepository {

	@PersistenceContext
	private EntityManager em;

	@Override
	public Optional<StockAggregate> find(String shopId, String skuId) {
		TypedQuery<StockEntity> q = em.createQuery(
			"select s from StockEntity s where s.shopId=:shop and s.skuId=:sku", StockEntity.class);
		q.setParameter("shop", shopId);
		q.setParameter("sku", skuId);
		List<StockEntity> list = q.getResultList();
		if (list.isEmpty()) {
			return Optional.empty();
		}
		StockEntity e = list.get(0);
		return Optional.of(
			new StockAggregate(e.getShopId(), e.getSkuId(), e.getTotalQuantity(), e.getReservedQuantity(),
				e.getVersion()));
	}

	@Override
	@Transactional
	public void insert(StockAggregate aggregate) {
		StockEntity e = new StockEntity();
		e.setShopId(aggregate.getShopId());
		e.setSkuId(aggregate.getSkuId());
		e.setTotalQuantity(aggregate.getTotalQuantity());
		e.setReservedQuantity(aggregate.getReservedQuantity());
		e.setVersion(aggregate.getVersion());
		Instant now = Instant.now();
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		em.persist(e);
	}

	@Override
	@Transactional
	public boolean update(StockAggregate aggregate) {
		int updated = em.createQuery(
				"update StockEntity s set s.totalQuantity=:t, s.reservedQuantity=:r, s.version=s.version+1, s.updatedAt=:u where s.shopId=:shop and s.skuId=:sku and s.version=:ver")
			.setParameter("t", aggregate.getTotalQuantity())
			.setParameter("r", aggregate.getReservedQuantity())
			.setParameter("u", Instant.now())
			.setParameter("shop", aggregate.getShopId())
			.setParameter("sku", aggregate.getSkuId())
			.setParameter("ver", aggregate.getVersion())
			.executeUpdate();
		return updated == 1;
	}

	@Override
	public List<StockAggregate> batchFind(List<ShopSkuKey> keys) {
		if (keys.isEmpty()) {
			return List.of();
		}
		List<String> shopIds = new ArrayList<>();
		List<String> skuIds = new ArrayList<>();
		for (ShopSkuKey k : keys) {
			shopIds.add(k.shopId());
			skuIds.add(k.skuId());
		}
		List<StockEntity> list = em.createQuery(
				"select s from StockEntity s where s.shopId in :shops and s.skuId in :skus",
				StockEntity.class)
			.setParameter("shops", shopIds)
			.setParameter("skus", skuIds)
			.getResultList();
		List<StockAggregate> out = new ArrayList<>();
		for (StockEntity e : list) {
			out.add(new StockAggregate(e.getShopId(), e.getSkuId(), e.getTotalQuantity(),
				e.getReservedQuantity(), e.getVersion()));
		}
		return out;
	}
}

