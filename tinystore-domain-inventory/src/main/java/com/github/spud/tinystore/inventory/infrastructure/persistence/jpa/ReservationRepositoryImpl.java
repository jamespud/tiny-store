package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa;

import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;
import com.github.spud.tinystore.inventory.domain.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReservationRepositoryImpl implements ReservationRepository {

	@PersistenceContext
	private EntityManager em;

	@Override
	@Transactional
	public void insert(Reservation r) {
		ReservationEntity e = new ReservationEntity();
		e.setReservationId(r.getReservationId());
		e.setShopId(r.getShopId());
		e.setSkuId(r.getSkuId());
		e.setQuantity(r.getQuantity());
		e.setState(r.getState().name());
		e.setExpireAt(r.getExpireAt());
		e.setOperationId(r.getOperationId());
		e.setCreatedAt(r.getCreatedAt());
		e.setUpdatedAt(r.getUpdatedAt());
		e.setVersion(r.getVersion());
		e.setReleaseReason(r.getReleaseReason());
		em.persist(e);
	}

	@Override
	public Optional<Reservation> find(String reservationId) {
		ReservationEntity e = em.find(ReservationEntity.class, reservationId);
		if (e == null) {
			return Optional.empty();
		}
		return Optional.of(map(e));
	}

	private Reservation map(ReservationEntity e) {
		return new Reservation(e.getReservationId(), e.getShopId(), e.getSkuId(), e.getQuantity(),
			ReservationState.valueOf(e.getState()), e.getExpireAt(), e.getOperationId(), e.getCreatedAt(),
			e.getUpdatedAt(), e.getVersion());
	}

	@Override
	@Transactional
	public boolean updateState(String reservationId, ReservationState expect, ReservationState target,
		long version, String releaseReason) {
		int updated = em.createQuery(
				"update ReservationEntity r set r.state=:target, r.updatedAt=:u, r.releaseReason=:rr, r.version=r.version+1 where r.reservationId=:id and r.state=:expect and r.version=:ver")
			.setParameter("target", target.name())
			.setParameter("u", Instant.now())
			.setParameter("rr", releaseReason)
			.setParameter("id", reservationId)
			.setParameter("expect", expect.name())
			.setParameter("ver", version)
			.executeUpdate();
		return updated == 1;
	}

	@Override
	public List<Reservation> findPendingExpired(Instant cutoff, int limit) {
		TypedQuery<ReservationEntity> q = em.createQuery(
			"select r from ReservationEntity r where r.state=:st and r.expireAt < :cut order by r.expireAt asc",
			ReservationEntity.class);
		q.setParameter("st", ReservationState.PENDING.name());
		q.setParameter("cut", cutoff);
		q.setMaxResults(limit);
		return q.getResultList().stream().map(this::map).collect(Collectors.toList());
	}
}
