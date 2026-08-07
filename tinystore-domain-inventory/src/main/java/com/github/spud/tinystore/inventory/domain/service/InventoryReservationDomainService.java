package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReserveCommand;
import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import com.github.spud.tinystore.inventory.domain.port.IdempotencyRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort;
import com.github.spud.tinystore.inventory.domain.port.InventoryReservationRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryStockRepository;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.domain.value.ReservationRef;
import com.github.spud.tinystore.inventory.domain.value.ReservationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical inventory reservation domain service.
 * <p>
 * This is the single authoritative orchestrator for inventory reservation
 * lifecycle:
 * PRE_DEDUCTED → CONFIRMED | RELEASED | EXPIRED
 * <p>
 * All state transitions, idempotency decisions, and Redis/DB consistency
 * arbitration
 * are concentrated here. No other class may write reservation lifecycle states.
 * <p>
 * Invariants enforced by this service:
 * <ul>
 * <li>CONFIRMED is terminal and irreversible</li>
 * <li>RELEASED and EXPIRED are terminal</li>
 * <li>Only the expiry scheduler may produce EXPIRED</li>
 * <li>Redis failure never reverts a committed DB terminal state</li>
 * <li>inventory_deduct_record is an execution log only — CONFIRMED is never
 * written there</li>
 * </ul>
 */
@Slf4j
@Service
public class InventoryReservationDomainService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryStockRepository stockRepository;
    private final InventoryDeductGateway deductGateway;
    private final IdempotencyRepository idempotencyRepository;
    private final InventoryDeductRecordRepository deductRecordRepository;
    private final InventoryMetricsPort metricsPort;

    public InventoryReservationDomainService(
            InventoryReservationRepository reservationRepository,
            InventoryStockRepository stockRepository,
            InventoryDeductGateway deductGateway,
            IdempotencyRepository idempotencyRepository,
            InventoryDeductRecordRepository deductRecordRepository,
            InventoryMetricsPort metricsPort) {
        this.reservationRepository = reservationRepository;
        this.stockRepository = stockRepository;
        this.deductGateway = deductGateway;
        this.idempotencyRepository = idempotencyRepository;
        this.deductRecordRepository = deductRecordRepository;
        this.metricsPort = metricsPort;
    }

    // ======================================================
    // RESERVE: PRE_DEDUCTED
    // ======================================================

    /**
     * Reserve inventory for all items in the command.
     * <p>
     * On success, all items get PRE_DEDUCTED reservations.
     * On any single failure, all already-admitted items are rolled back
     * (all-or-nothing).
     * Redis admission is done first; DB reservation is written inside a
     * transaction.
     */
    @Transactional
    public ReservationResult reserve(InventoryReserveCommand command) {
        // Idempotency: if this key has been processed, replay the result
        Optional<String> cachedResult = idempotencyRepository.getDeductOrderId(command.getIdempotencyKey());
        if (cachedResult.isPresent()) {
            log.info("Reserve idempotent hit: idempotencyKey={}", command.getIdempotencyKey());
            List<ReservationRef> replayedRefs = reservationRepository.findByOperationId(command.getIdempotencyKey());
            if (replayedRefs == null || replayedRefs.isEmpty()) {
                log.error("Reserve idempotent hit missing reservation refs: idempotencyKey={}",
                        command.getIdempotencyKey());
                return ReservationResult.fail("IDEMPOTENT_REPLAY_MISSING_REFS");
            }
            metricsPort.reserveSuccess();
            return ReservationResult.ok(replayedRefs, InventoryReservationStatus.PRE_DEDUCTED);
        }

        // Validate: no duplicate (shopId, skuId) within a single command
        Set<String> seen = new HashSet<>();
        for (InventoryReserveCommand.Item item : command.getItems()) {
            String key = item.getShopId() + ":" + item.getSkuId();
            if (!seen.add(key)) {
                metricsPort.reserveFail();
                return ReservationResult.fail(List.of(item.getSkuId()), "DUPLICATE_SKU: " + item.getSkuId());
            }
        }

        OffsetDateTime expireAt = command.getExpireAt() != null
                ? command.getExpireAt()
                : OffsetDateTime.now().plusMinutes(15);

        List<ReservationRef> successRefs = new ArrayList<>();
        List<OccupyPair> redisSuccessForRollback = new ArrayList<>();

        for (InventoryReserveCommand.Item item : command.getItems()) {
            // Step 1: Redis admission (preDeduct)
            Optional<String> occupyId = deductGateway.preDeduct(
                    item.getShopId(), item.getSkuId(), item.getQuantity(), command.getOrderId());

            if (occupyId.isEmpty()) {
                // Rollback all already-admitted items
                rollbackRedisAll(redisSuccessForRollback);
                metricsPort.reserveFail();
                return ReservationResult.fail(List.of(item.getSkuId()), "STOCK_LACK: " + item.getSkuId());
            }

            String reservationId = occupyId.get(); // occupyId IS the reservationId

            // Step 2: Write PRE_DEDUCTED reservation in DB
            try {
                reservationRepository.saveReservation(
                        reservationId,
                        item.getShopId(),
                        item.getSkuId(),
                        item.getQuantity(),
                        command.getTradeId(),
                        command.getOrderId(),
                        command.getIdempotencyKey(),
                        expireAt);
            } catch (Exception e) {
                log.error("Failed to write reservation to DB, rolling back Redis: reservationId={}", reservationId, e);
                // Rollback this item's Redis admission
                deductGateway.rollback(item.getShopId(), item.getSkuId(), reservationId);
                // Rollback previously succeeded items
                rollbackRedisAll(redisSuccessForRollback);
                metricsPort.reserveFail();
                // Throw to trigger @Transactional rollback for all previously saved
                // reservations (all-or-nothing)
                throw new IllegalStateException("DB_WRITE_FAILED for reservationId=" + reservationId, e);
            }

            redisSuccessForRollback.add(OccupyPair.builder()
                    .shopId(item.getShopId()).skuId(item.getSkuId()).occupyId(reservationId).build());
            successRefs.add(ReservationRef.builder()
                    .shopId(item.getShopId()).skuId(item.getSkuId()).reservationId(reservationId).build());
        }

        // Mark idempotency as processed (best-effort)
        try {
            idempotencyRepository.bindDeductOrderIdIfAbsent(command.getIdempotencyKey(), command.getOrderId());
        } catch (Exception e) {
            log.warn("Failed to mark reserve idempotency: key={}", command.getIdempotencyKey(), e);
        }

        metricsPort.reserveSuccess();
        return ReservationResult.ok(successRefs, InventoryReservationStatus.PRE_DEDUCTED);
    }

    // ======================================================
    // CONFIRM: PRE_DEDUCTED → CONFIRMED (terminal)
    // ======================================================

    /**
     * Confirm reservations on payment success.
     * <p>
     * All occupyPairs in the command must transition PRE_DEDUCTED → CONFIRMED
     * atomically.
     * If any reservation is already in a terminal state that conflicts
     * (RELEASED/EXPIRED),
     * the entire confirm is rejected with a deterministic conflict result.
     * <p>
     * Idempotency key must be derived from paymentId.
     */
    @Transactional
    public ReservationResult confirm(InventoryConfirmCommand command) {
        // Idempotency check
        if (idempotencyRepository.exists(command.getIdempotencyKey())) {
            log.info("Confirm idempotent hit: idempotencyKey={}", command.getIdempotencyKey());
            // Replay original confirmed refs from DB-authoritative records (per Bug-Fix-7
            // SEVERE-3).
            // Must NOT use command payload shopId/skuId to prevent tampering.
            List<ReservationRef> replayedRefs = new ArrayList<>();
            for (OccupyPair pair : command.getOccupyPairs()) {
                String reservationId = pair.getOccupyId();
                Optional<ReservationRef> refOpt = reservationRepository.findByReservationIdForUpdate(reservationId);
                if (refOpt.isEmpty()) {
                    log.error(
                            "Confirm idempotent hit: reservation not found in DB: reservationId={}, idempotencyKey={} — SYSTEM_INCONSISTENCY",
                            reservationId, command.getIdempotencyKey());
                    throw new IllegalStateException(
                            "RESERVATION_NOT_FOUND during idempotent replay: " + reservationId
                                    + " — system inconsistency");
                }
                ReservationRef ref = refOpt.get();
                InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(ref.getStatus());
                if (currentStatus != InventoryReservationStatus.CONFIRMED) {
                    log.error(
                            "Confirm idempotent hit: reservation not in CONFIRMED state: reservationId={}, status={}, idempotencyKey={} — SYSTEM_INCONSISTENCY",
                            reservationId, currentStatus, command.getIdempotencyKey());
                    throw new IllegalStateException(
                            "RESERVATION_NOT_CONFIRMED during idempotent replay: " + reservationId + ", status="
                                    + currentStatus + " — system inconsistency");
                }
                replayedRefs.add(ReservationRef.builder()
                        .shopId(ref.getShopId())
                        .skuId(ref.getSkuId())
                        .reservationId(reservationId)
                        .build());
            }
            return ReservationResult.ok(replayedRefs, InventoryReservationStatus.CONFIRMED);
        }

        List<String> conflictIds = new ArrayList<>();

        // Pass 1: read-only pre-check — classify statuses; throw on NOT_FOUND (system
        // error).
        // Does NOT populate confirmedRefs to avoid using untrusted command payload
        // shopId/skuId.
        for (OccupyPair pair : command.getOccupyPairs()) {
            String reservationId = pair.getOccupyId();
            Optional<String> statusOpt = reservationRepository.findStatusByReservationId(reservationId);

            if (statusOpt.isEmpty()) {
                log.error(
                        "Reservation not found during confirm: reservationId={}, tradeId={} — SYSTEM_INCONSISTENCY, manual intervention required",
                        reservationId, command.getTradeId());
                throw new IllegalStateException(
                        "RESERVATION_NOT_FOUND: " + reservationId
                                + " — system inconsistency, manual intervention required");
            }

            InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(statusOpt.get());

            if (currentStatus == InventoryReservationStatus.RELEASED
                    || currentStatus == InventoryReservationStatus.EXPIRED) {
                // Deterministic conflict — do NOT change state
                log.warn("Confirm conflict: reservationId={} is in terminal state {}", reservationId, currentStatus);
                conflictIds.add(reservationId);
            } else if (currentStatus == InventoryReservationStatus.PRE_DEDUCTED) {
                // 逻辑过期检查：expiry scheduler 有 5s 调度窗口，confirm 可能抢先成功——
                // 直接拒绝已过期（expireAt < now）但状态未流转的 reservation
                Optional<java.time.OffsetDateTime> expireAtOpt =
                        reservationRepository.findExpireAtByReservationId(reservationId);
                if (expireAtOpt.isPresent()
                        && expireAtOpt.get().isBefore(java.time.OffsetDateTime.now())) {
                    log.warn("Confirm conflict: reservationId={} is logically expired (expireAt={})",
                            reservationId, expireAtOpt.get());
                    conflictIds.add(reservationId);
                }
            }
            // CONFIRMED and PRE_DEDUCTED items proceed to pass 2
        }

        // If any conflicts found in pre-check, abort the entire confirm
        if (!conflictIds.isEmpty()) {
            metricsPort.confirmConflict();
            return ReservationResult.conflict(conflictIds, "RESERVATION_CONFLICT");
        }

        // Pass 2a: SELECT FOR UPDATE — acquire ALL row locks before performing any
        // writes.
        // Locking all items first prevents partial commits if a race conflict is
        // detected mid-batch.
        // All refs are built exclusively from locked DB records (not command payload —
        // tamper prevention).
        // (per failure-arbitration.md §4; prohibits optimistic CAS for expire/confirm
        // race).
        List<ReservationRef> allLockedRefs = new ArrayList<>(command.getOccupyPairs().size());
        List<String> raceConflictIds = new ArrayList<>();
        for (OccupyPair pair : command.getOccupyPairs()) {
            String reservationId = pair.getOccupyId();
            ReservationRef lockedRef = reservationRepository.findByReservationIdForUpdate(reservationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "RESERVATION_NOT_FOUND under lock: " + reservationId + " — system inconsistency"));
            InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(lockedRef.getStatus());
            if (currentStatus == InventoryReservationStatus.RELEASED
                    || currentStatus == InventoryReservationStatus.EXPIRED) {
                // Raced into terminal conflict between pre-check and lock acquisition
                log.warn("Confirm conflict race: reservationId={} transitioned to {} between pre-check and lock",
                        reservationId, currentStatus);
                raceConflictIds.add(reservationId);
            }
            allLockedRefs.add(lockedRef);
        }

        // If any race conflicts found, return conflict — no writes have been performed,
        // safe to return normally.
        if (!raceConflictIds.isEmpty()) {
            metricsPort.confirmConflict();
            return ReservationResult.conflict(raceConflictIds, "RESERVATION_CONFLICT_RACE");
        }

        // Pass 2b: all items confirmed safe (CONFIRMED or PRE_DEDUCTED under lock) —
        // perform transitions.
        List<ReservationRef> confirmedRefs = new ArrayList<>();
        for (int i = 0; i < command.getOccupyPairs().size(); i++) {
            ReservationRef lockedRef = allLockedRefs.get(i);
            String reservationId = lockedRef.getReservationId();
            InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(lockedRef.getStatus());

            if (currentStatus == InventoryReservationStatus.CONFIRMED) {
                // Idempotent — ref built from authoritative DB record (not command payload)
                confirmedRefs.add(ReservationRef.builder()
                        .shopId(lockedRef.getShopId()).skuId(lockedRef.getSkuId()).reservationId(reservationId)
                        .build());
                continue;
            }

            // Transition PRE_DEDUCTED → CONFIRMED
            boolean updated = reservationRepository.transitionStatus(
                    reservationId,
                    InventoryReservationStatus.PRE_DEDUCTED,
                    InventoryReservationStatus.CONFIRMED,
                    OffsetDateTime.now(),
                    null);

            if (!updated) {
                // CAS failed — rollback entire batch via transaction rollback
                throw new IllegalStateException("CAS_FAILED_CONFIRM: " + reservationId);
            }

            // Both quantity and shopId/skuId come from the locked DB record (not command
            // payload — tamper prevention).
            Integer quantity = lockedRef.getQuantity();
            if (quantity == null || quantity <= 0) {
                throw new IllegalStateException("Invalid quantity in locked reservation: " + reservationId);
            }
            stockRepository.deductConfirmed(lockedRef.getShopId(), lockedRef.getSkuId(), quantity);

            confirmedRefs.add(ReservationRef.builder()
                    .shopId(lockedRef.getShopId()).skuId(lockedRef.getSkuId()).reservationId(reservationId).build());
        }

        // Mark idempotency
        try {
            idempotencyRepository.saveDeductResult(command.getIdempotencyKey(),
                    com.github.spud.tinystore.inventory.domain.value.DeductResult.builder()
                            .success(true).message("confirmed").build());
        } catch (Exception e) {
            log.warn("Failed to mark confirm idempotency: key={}", command.getIdempotencyKey(), e);
        }

        return ReservationResult.ok(confirmedRefs, InventoryReservationStatus.CONFIRMED);
    }

    // ======================================================
    // RELEASE: PRE_DEDUCTED → RELEASED (terminal)
    // ======================================================

    /**
     * Release reservations on order cancellation.
     * <p>
     * CONFIRMED → RELEASED is rejected (must go through after-sale/refund).
     * RELEASED/EXPIRED → RELEASED is idempotent success.
     */
    @Transactional
    public ReservationResult release(InventoryReleaseCommand command) {
        if (idempotencyRepository.isReleased(command.getIdempotencyKey())) {
            log.info("Release idempotent hit: idempotencyKey={}", command.getIdempotencyKey());
            // Replay original released refs from DB-authoritative records (per Bug-Fix-10).
            // Must NOT use command payload shopId/skuId to prevent tampering.
            List<ReservationRef> replayedRefs = new ArrayList<>();
            for (OccupyPair pair : command.getOccupyPairs()) {
                String reservationId = pair.getOccupyId();
                Optional<ReservationRef> refOpt = reservationRepository.findByReservationIdForUpdate(reservationId);
                if (refOpt.isEmpty()) {
                    log.warn(
                            "Release idempotent hit: reservation not found in DB: reservationId={}, idempotencyKey={} — treating as already released",
                            reservationId, command.getIdempotencyKey());
                    // Treat as already released — return ref from command payload (best-effort)
                    replayedRefs.add(ReservationRef.builder()
                            .shopId(pair.getShopId())
                            .skuId(pair.getSkuId())
                            .reservationId(reservationId)
                            .build());
                    continue;
                }
                ReservationRef ref = refOpt.get();
                InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(ref.getStatus());
                if (currentStatus != InventoryReservationStatus.RELEASED
                        && currentStatus != InventoryReservationStatus.EXPIRED) {
                    log.error(
                            "Release idempotent hit: reservation not in RELEASED/EXPIRED state: reservationId={}, status={}, idempotencyKey={} — SYSTEM_INCONSISTENCY",
                            reservationId, currentStatus, command.getIdempotencyKey());
                    throw new IllegalStateException(
                            "RESERVATION_NOT_RELEASED during idempotent replay: " + reservationId + ", status="
                                    + currentStatus + " — system inconsistency");
                }
                replayedRefs.add(ReservationRef.builder()
                        .shopId(ref.getShopId())
                        .skuId(ref.getSkuId())
                        .reservationId(reservationId)
                        .build());
            }
            return ReservationResult.ok(replayedRefs, InventoryReservationStatus.RELEASED);
        }

        // Pass 1: pre-check for CONFIRMED conflicts — no writes, guarantees atomicity
        // of write pass
        for (OccupyPair pair : command.getOccupyPairs()) {
            String reservationId = pair.getOccupyId();
            Optional<String> statusOpt = reservationRepository.findStatusByReservationId(reservationId);
            if (statusOpt.isPresent()) {
                InventoryReservationStatus status = InventoryReservationStatus.fromCode(statusOpt.get());
                if (status == InventoryReservationStatus.CONFIRMED) {
                    log.warn("Release rejected: reservationId={} is CONFIRMED (must use refund path)", reservationId);
                    return ReservationResult.conflict(List.of(reservationId), "RELEASE_CONFLICT_CONFIRMED");
                }
            }
        }

        // Pass 2a: SELECT FOR UPDATE — acquire ALL row locks before performing any
        // writes.
        // Locking all items first prevents partial commits if a CONFIRMED race is
        // detected mid-batch.
        // (per failure-arbitration.md §4; prohibits optimistic CAS for concurrent
        // release/expire).
        List<ReservationRef> lockedRefs = new ArrayList<>(command.getOccupyPairs().size());
        List<Boolean> notFoundFlags = new ArrayList<>(command.getOccupyPairs().size());
        List<String> raceConflictIds = new ArrayList<>();
        for (OccupyPair pair : command.getOccupyPairs()) {
            String reservationId = pair.getOccupyId();
            Optional<ReservationRef> lockedRefOpt = reservationRepository.findByReservationIdForUpdate(reservationId);
            if (lockedRefOpt.isEmpty()) {
                lockedRefs.add(null);
                notFoundFlags.add(true);
                continue;
            }
            ReservationRef lockedRef = lockedRefOpt.get();
            InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(lockedRef.getStatus());
            if (currentStatus == InventoryReservationStatus.CONFIRMED) {
                // Raced into CONFIRMED between pre-check and lock acquisition
                log.warn("Release conflict race: reservationId={} is CONFIRMED under lock (must use refund path)",
                        reservationId);
                raceConflictIds.add(reservationId);
            }
            lockedRefs.add(lockedRef);
            notFoundFlags.add(false);
        }

        // If any race conflicts found, return conflict — no writes have been performed,
        // safe to return normally.
        if (!raceConflictIds.isEmpty()) {
            return ReservationResult.conflict(raceConflictIds, "RELEASE_CONFLICT_CONFIRMED_RACE");
        }

        // Pass 2b: all items confirmed safe to release — perform transitions.
        List<ReservationRef> releasedRefs = new ArrayList<>();
        List<OccupyPair> redisRollbackPairs = new ArrayList<>();
        for (int i = 0; i < command.getOccupyPairs().size(); i++) {
            OccupyPair pair = command.getOccupyPairs().get(i);
            String reservationId = pair.getOccupyId();
            ReservationRef lockedRef = lockedRefs.get(i);

            if (lockedRef == null) {
                log.warn("Reservation not found during release (treating as already released): reservationId={}",
                        reservationId);
                releasedRefs.add(ReservationRef.builder()
                        .shopId(pair.getShopId()).skuId(pair.getSkuId()).reservationId(reservationId).build());
                continue;
            }

            InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(lockedRef.getStatus());

            if (currentStatus == InventoryReservationStatus.RELEASED
                    || currentStatus == InventoryReservationStatus.EXPIRED) {
                // Idempotent
                releasedRefs.add(ReservationRef.builder()
                        .shopId(lockedRef.getShopId()).skuId(lockedRef.getSkuId()).reservationId(reservationId)
                        .build());
                continue;
            }

            // PRE_DEDUCTED → RELEASED
            boolean updated = reservationRepository.transitionStatus(
                    reservationId,
                    InventoryReservationStatus.PRE_DEDUCTED,
                    InventoryReservationStatus.RELEASED,
                    null,
                    command.getReason());

            if (!updated) {
                // CAS failed — rollback entire batch via transaction rollback
                throw new IllegalStateException("CAS_FAILED_DURING_RELEASE: " + reservationId);
            }

            releasedRefs.add(ReservationRef.builder()
                    .shopId(lockedRef.getShopId()).skuId(lockedRef.getSkuId()).reservationId(reservationId).build());
            // Use authoritative shopId/skuId from locked record (not command payload —
            // tamper prevention)
            redisRollbackPairs.add(OccupyPair.builder()
                    .shopId(lockedRef.getShopId()).skuId(lockedRef.getSkuId()).occupyId(reservationId).build());
        }

        // Pass 3: Redis rollbacks — best-effort; exceptions must NOT trigger
        // @Transactional rollback.
        // DB terminal state RELEASED is already written; failure-arbitration.md §2
        // principle 1.
        // principle 3: all Redis compensation failures must write
        // inventory_deduct_record execution log.
        for (OccupyPair pair : redisRollbackPairs) {
            try {
                boolean redisOk = deductGateway.rollback(pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
                if (!redisOk) {
                    log.error("Redis admission restore failed after DB RELEASED — alert required: " +
                            "shopId={}, skuId={}, reservationId={}", pair.getShopId(), pair.getSkuId(),
                            pair.getOccupyId());
                    writeRedisRollbackFailedLog(pair.getOccupyId(), pair.getShopId(), pair.getSkuId(),
                            "REDIS_ROLLBACK_FAILED_AFTER_RELEASED");
                }
            } catch (Exception e) {
                log.error("Redis rollback threw exception after DB RELEASED — alert required: " +
                        "shopId={}, skuId={}, reservationId={}", pair.getShopId(), pair.getSkuId(), pair.getOccupyId(),
                        e);
                writeRedisRollbackFailedLog(pair.getOccupyId(), pair.getShopId(), pair.getSkuId(),
                        "REDIS_ROLLBACK_EXCEPTION_AFTER_RELEASED: " + e.getMessage());
                // DB state is terminal (RELEASED) — catch to prevent @Transactional rollback
            }
        }

        // Mark idempotency
        try {
            idempotencyRepository.markReleased(command.getIdempotencyKey());
        } catch (Exception e) {
            log.warn("Failed to mark release idempotency: key={}", command.getIdempotencyKey(), e);
        }

        return ReservationResult.ok(releasedRefs, InventoryReservationStatus.RELEASED);
    }


    // ======================================================
    // RESERVE REDIS-ONLY (async split: sync Redis + async DB)
    // ======================================================

    /**
     * Redis preDeduct only (sync, for async reserve split). Returns reservationId.
     * Does NOT write DB. The caller writes an Outbox event; the consumer calls saveReservation().
     */
    public ReservationResult reserveRedisOnly(InventoryReserveCommand command) {
        Set<String> seen = new HashSet<>();
        for (InventoryReserveCommand.Item item : command.getItems()) {
            if (!seen.add(item.getShopId() + ":" + item.getSkuId())) {
                metricsPort.reserveFail();
                return ReservationResult.fail("DUPLICATE_SKU: " + item.getSkuId());
            }
        }

        OffsetDateTime expireAt = command.getExpireAt() != null
                ? command.getExpireAt() : OffsetDateTime.now().plusMinutes(15);

        List<ReservationRef> successRefs = new ArrayList<>();
        List<OccupyPair> redisSuccessForRollback = new ArrayList<>();

        for (InventoryReserveCommand.Item item : command.getItems()) {
            Optional<String> occupyId = deductGateway.preDeduct(
                    item.getShopId(), item.getSkuId(), item.getQuantity(), command.getOrderId());

            if (occupyId.isEmpty()) {
                rollbackRedisAll(redisSuccessForRollback);
                metricsPort.reserveFail();
                return ReservationResult.fail(List.of(item.getSkuId()), "STOCK_LACK: " + item.getSkuId());
            }

            String reservationId = occupyId.get();
            redisSuccessForRollback.add(OccupyPair.builder()
                    .shopId(item.getShopId()).skuId(item.getSkuId()).occupyId(reservationId).build());
            successRefs.add(ReservationRef.builder()
                    .shopId(item.getShopId()).skuId(item.getSkuId()).reservationId(reservationId).build());
        }

        metricsPort.reserveSuccess();
        return ReservationResult.ok(successRefs, InventoryReservationStatus.PRE_DEDUCTED);
    }

    /**
     * DB saveReservation only (async, called by Kafka consumer). No Redis.
     */
    public void saveReservation(String reservationId, String shopId, String skuId,
                            int quantity, String tradeId, String orderId,
                            String operationId, OffsetDateTime expireAt) {
        reservationRepository.saveReservation(reservationId, shopId, skuId,
                quantity, tradeId, orderId, operationId, expireAt);
    }

    /**
     * Redis rollback only (for createTrade compensation). No DB.
     */
    public boolean rollbackRedis(String shopId, String skuId, String reservationId) {
        return deductGateway.rollback(shopId, skuId, reservationId);
    }

    // ======================================================
    // EXPIRE: PRE_DEDUCTED → EXPIRED (terminal, scheduler only)
    // ======================================================

    /**
     * Expire overdue PRE_DEDUCTED reservations.
     * <p>
     * This method is called ONLY by the Inventory expiry scheduler.
     * Order domain must NOT call this directly.
     *
     * @return count of actually expired reservations
     */
    @Transactional
    public int expireExpiredReservations(OffsetDateTime now) {
        List<String> candidateIds = reservationRepository.findExpiredCandidateIds(now);
        if (candidateIds.isEmpty()) {
            return 0;
        }

        int expiredCount = 0;
        for (String reservationId : candidateIds) {
            try {
                expiredCount += expireOne(reservationId);
            } catch (Exception e) {
                log.error("Failed to expire reservation: reservationId={}", reservationId, e);
                // Continue with other candidates
            }
        }

        return expiredCount;
    }

    private int expireOne(String reservationId) {
        Optional<String> statusOpt = reservationRepository.findStatusByReservationId(reservationId);
        if (statusOpt.isEmpty()) {
            return 0;
        }

        InventoryReservationStatus currentStatus = InventoryReservationStatus.fromCode(statusOpt.get());

        if (currentStatus != InventoryReservationStatus.PRE_DEDUCTED) {
            // Already transitioned (e.g., confirmed or released concurrently) — skip
            return 0;
        }

        // Acquire row lock and retrieve shopId/skuId needed for Redis rollback after
        // expiry
        Optional<ReservationRef> refOpt = reservationRepository.findByReservationIdForUpdate(reservationId);
        if (refOpt.isEmpty()) {
            return 0;
        }
        ReservationRef ref = refOpt.get();

        // Transition PRE_DEDUCTED → EXPIRED (CAS under lock)
        boolean updated = reservationRepository.transitionStatus(
                reservationId,
                InventoryReservationStatus.PRE_DEDUCTED,
                InventoryReservationStatus.EXPIRED,
                null,
                "EXPIRED_BY_SCHEDULER");

        if (!updated) {
            // CAS failed — state changed between read and write (e.g., concurrent confirm)
            log.info("Expire CAS failed (likely concurrent confirm): reservationId={}", reservationId);
            return 0;
        }

        // Restore Redis admission count (best-effort — DB state is terminal;
        // failure-arbitration.md §2)
        // principle 3: all Redis compensation failures must write
        // inventory_deduct_record execution log.
        try {
            boolean redisOk = deductGateway.rollback(ref.getShopId(), ref.getSkuId(), reservationId);
            if (!redisOk) {
                log.error("Redis admission restore failed after DB EXPIRED — alert required: " +
                        "shopId={}, skuId={}, reservationId={}", ref.getShopId(), ref.getSkuId(), reservationId);
                writeRedisRollbackFailedLog(reservationId, ref.getShopId(), ref.getSkuId(),
                        "REDIS_ROLLBACK_FAILED_AFTER_EXPIRED");
            }
        } catch (Exception e) {
            log.error("Redis rollback threw exception after DB EXPIRED — alert required: " +
                    "shopId={}, skuId={}, reservationId={}", ref.getShopId(), ref.getSkuId(), reservationId, e);
            writeRedisRollbackFailedLog(reservationId, ref.getShopId(), ref.getSkuId(),
                    "REDIS_ROLLBACK_EXCEPTION_AFTER_EXPIRED: " + e.getMessage());
            // DB state is terminal (EXPIRED) — catch to prevent propagation
        }

        log.info("Expired reservation: reservationId={}", reservationId);
        return 1;
    }

    // ======================================================
    // Private helpers
    // ======================================================

    private void writeRedisRollbackFailedLog(String reservationId, String shopId, String skuId, String reason) {
        metricsPort.redisRollbackFailed();
        try {
            deductRecordRepository.saveRedisRollbackFailed(reservationId, shopId, skuId, reason);
        } catch (Exception ex) {
            log.error("Failed to persist Redis rollback failure log: reservationId={}", reservationId, ex);
        }
    }

    private void rollbackRedisAll(List<OccupyPair> pairs) {
        for (OccupyPair pair : pairs) {
            boolean ok = deductGateway.rollback(pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
            if (!ok) {
                log.error("Redis rollback failed during reserve cleanup: shopId={}, skuId={}, occupyId={}",
                        pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
            }
        }
    }
}
