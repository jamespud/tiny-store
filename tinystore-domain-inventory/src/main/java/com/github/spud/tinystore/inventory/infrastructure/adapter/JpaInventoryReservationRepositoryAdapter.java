package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import com.github.spud.tinystore.inventory.domain.port.InventoryReservationRepository;
import com.github.spud.tinystore.inventory.domain.value.ReservationRef;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the canonical InventoryReservationRepository domain port.
 */
@Slf4j
@Repository
public class JpaInventoryReservationRepositoryAdapter implements InventoryReservationRepository {

    private final JpaInventoryReservationRepository jpaRepo;

    public JpaInventoryReservationRepositoryAdapter(JpaInventoryReservationRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public Optional<ReservationRef> findByReservationIdForUpdate(String reservationId) {
        return jpaRepo.findByReservationIdForUpdate(reservationId)
                .map(e -> ReservationRef.builder()
                        .shopId(e.getShopId())
                        .skuId(e.getSkuId())
                        .reservationId(e.getReservationId())
                        .status(e.getStatus())
                        .quantity((int) e.getQuantity())
                        .build());
    }

    @Override
    public Optional<String> findStatusByReservationId(String reservationId) {
        return jpaRepo.findStatusByReservationId(reservationId);
    }

    @Override
    public Optional<java.time.OffsetDateTime> findExpireAtByReservationId(String reservationId) {
        return jpaRepo.findExpireAtByReservationId(reservationId);
    }

    @Override
    public Optional<Integer> findQuantityByReservationId(String reservationId) {
        return jpaRepo.findQuantityByReservationId(reservationId);
    }

    @Override
    public List<ReservationRef> findByOperationId(String operationId) {
        return jpaRepo.findByOperationId(operationId).stream()
                .map(e -> ReservationRef.builder()
                        .shopId(e.getShopId())
                        .skuId(e.getSkuId())
                        .reservationId(e.getReservationId())
                        .build())
                .toList();
    }

    @Override
    public void saveReservation(String reservationId, String shopId, String skuId,
                                int quantity, String tradeId, String orderId,
                                String operationId, OffsetDateTime expireAt) {
        InventoryReservationEntity entity = new InventoryReservationEntity()
                .setReservationId(reservationId)
                .setShopId(shopId)
                .setSkuId(skuId)
                .setQuantity(quantity)
                .setStatus(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                .setTradeId(tradeId)
                .setOperationId(operationId)
                .setExpireAt(expireAt);
        jpaRepo.save(entity);
        log.debug("Saved PRE_DEDUCTED reservation: reservationId={}, shopId={}, skuId={}",
                reservationId, shopId, skuId);
    }

    @Override
    public boolean transitionStatus(String reservationId,
                                    InventoryReservationStatus expectedStatus,
                                    InventoryReservationStatus targetStatus,
                                    OffsetDateTime confirmedAt,
                                    String releaseReason) {
        int updated = jpaRepo.transitionStatus(
                reservationId,
                expectedStatus.getCode(),
                targetStatus.getCode(),
                confirmedAt,
                releaseReason);
        if (updated == 0) {
            log.warn("transitionStatus CAS failed: reservationId={}, expected={}, target={}",
                    reservationId, expectedStatus, targetStatus);
        }
        return updated > 0;
    }

    @Override
    public List<String> findExpiredCandidateIds(OffsetDateTime before) {
        return jpaRepo.findExpiredCandidateIds(before);
    }
}
