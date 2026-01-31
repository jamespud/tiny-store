package com.github.spud.tinystore.inventory.application.service;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryAdjustmentEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import com.github.spud.tinystore.inventory.interfaces.dto.StockCommitRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockCommitResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.StockReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockReleaseResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAppService {

	public static final String STATUS_RESERVED = "RESERVED";
	public static final String STATUS_COMMITTED = "COMMITTED";
	public static final String STATUS_RELEASED = "RELEASED";
	public static final String STATUS_EXPIRED = "EXPIRED";
	public static final String ADJUST_REASON_RESTOCK_REFUND = "RESTOCK_REFUND";

	private final JpaInventoryStockRepository stockRepository;
	private final JpaInventoryReservationRepository reservationRepository;
	private final JpaInventoryAdjustmentRepository adjustmentRepository;

	public StockAppService(
		JpaInventoryStockRepository stockRepository,
		JpaInventoryReservationRepository reservationRepository,
		JpaInventoryAdjustmentRepository adjustmentRepository
	) {
		this.stockRepository = stockRepository;
		this.reservationRepository = reservationRepository;
		this.adjustmentRepository = adjustmentRepository;
	}

	@Transactional
	public StockPreOccupyResponse preOccupy(String idempotencyKey, StockPreOccupyRequest request) {
		String operationId = idempotencyKey;
		List<InventoryReservationEntity> existing = reservationRepository.findByOperationId(operationId);
		if (!existing.isEmpty()) {
			return replayPreOccupy(existing, request);
		}

		Set<String> seenSkuIds = new HashSet<>();
		for (StockPreOccupyRequest.Line line : request.getLines()) {
			if (!seenSkuIds.add(line.getSkuId())) {
				return StockPreOccupyResponse.fail(List.of(line.getSkuId()), "DUPLICATE_SKU_ID");
			}
		}

		OffsetDateTime expireAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(request.getExpiresAtEpochMs()), ZoneOffset.UTC);
		if (expireAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			return StockPreOccupyResponse.fail(List.of(), "EXPIRED_AT_IN_PAST");
		}

		List<InventoryStockEntity> lockedStocks = new ArrayList<>();
		List<String> lackSkuIds = new ArrayList<>();
		for (StockPreOccupyRequest.Line line : request.getLines()) {
			Optional<InventoryStockEntity> stockOpt = stockRepository.findByTenantIdAndSkuIdForUpdate(request.getTenantId(), line.getSkuId());
			if (stockOpt.isEmpty()) {
				lackSkuIds.add(line.getSkuId());
				continue;
			}
			InventoryStockEntity stock = stockOpt.get();
			long available = stock.getTotalQuantity() - stock.getReservedQuantity();
			if (available < line.getQuantity()) {
				lackSkuIds.add(line.getSkuId());
				continue;
			}
			lockedStocks.add(stock);
		}
		if (!lackSkuIds.isEmpty()) {
			return StockPreOccupyResponse.fail(lackSkuIds, "STOCK_LACK");
		}

		Map<String, InventoryReservationEntity> reservationsBySku = new HashMap<>();
		for (StockPreOccupyRequest.Line line : request.getLines()) {
			InventoryStockEntity stock = lockedStocks.stream()
				.filter(s -> s.getSkuId().equals(line.getSkuId()))
				.findFirst()
				.orElseThrow();
			stock.setReservedQuantity(stock.getReservedQuantity() + line.getQuantity());
			stockRepository.save(stock);

			String reservationId = UUID.randomUUID().toString().replace("-", "");
			InventoryReservationEntity reservation = new InventoryReservationEntity()
				.setReservationId(reservationId)
				.setTenantId(request.getTenantId())
				.setTradeId(request.getTradeId())
				.setSkuId(line.getSkuId())
				.setQuantity(line.getQuantity())
				.setExpireAt(expireAt)
				.setStatus(STATUS_RESERVED)
				.setOperationId(operationId);
			reservationRepository.save(reservation);
			reservationsBySku.put(line.getSkuId(), reservation);
		}

		List<String> preOccupyIds = new ArrayList<>();
		for (StockPreOccupyRequest.Line line : request.getLines()) {
			preOccupyIds.add(reservationsBySku.get(line.getSkuId()).getReservationId());
		}
		return StockPreOccupyResponse.ok(preOccupyIds, request.getExpiresAtEpochMs());
	}

	@Transactional
	public StockCommitResponse commit(String idempotencyKey, StockCommitRequest request) {
		List<String> ids = request.getPreOccupyIds();
		for (String reservationId : ids) {
			InventoryReservationEntity reservation = reservationRepository.findByReservationIdForUpdate(reservationId)
				.orElse(null);
			if (reservation == null) {
				return StockCommitResponse.fail("RESERVATION_NOT_FOUND");
			}
			if (!reservation.getTenantId().equals(request.getTenantId()) || !reservation.getTradeId().equals(request.getTradeId())) {
				return StockCommitResponse.fail("RESERVATION_MISMATCH");
			}
			if (STATUS_COMMITTED.equals(reservation.getStatus())) {
				continue;
			}
			if (!STATUS_RESERVED.equals(reservation.getStatus())) {
				return StockCommitResponse.fail("RESERVATION_NOT_RESERVED");
			}
			InventoryStockEntity stock = stockRepository.findByTenantIdAndSkuIdForUpdate(reservation.getTenantId(), reservation.getSkuId())
				.orElse(null);
			if (stock == null) {
				return StockCommitResponse.fail("STOCK_NOT_FOUND");
			}
			stock.setTotalQuantity(stock.getTotalQuantity() - reservation.getQuantity());
			stock.setReservedQuantity(stock.getReservedQuantity() - reservation.getQuantity());
			stockRepository.save(stock);
			reservation.setStatus(STATUS_COMMITTED);
			reservationRepository.save(reservation);
		}
		return StockCommitResponse.ok("ok");
	}

	@Transactional
	public StockReleaseResponse release(String idempotencyKey, StockReleaseRequest request) {
		for (String reservationId : request.getPreOccupyIds()) {
			InventoryReservationEntity reservation = reservationRepository.findByReservationIdForUpdate(reservationId)
				.orElse(null);
			if (reservation == null) {
				continue;
			}
			if (!reservation.getTenantId().equals(request.getTenantId()) || !reservation.getTradeId().equals(request.getTradeId())) {
				return StockReleaseResponse.fail("RESERVATION_MISMATCH");
			}
			if (STATUS_RELEASED.equals(reservation.getStatus()) || STATUS_EXPIRED.equals(reservation.getStatus())) {
				continue;
			}
			if (STATUS_COMMITTED.equals(reservation.getStatus())) {
				return StockReleaseResponse.fail("RESERVATION_ALREADY_COMMITTED");
			}
			if (!STATUS_RESERVED.equals(reservation.getStatus())) {
				continue;
			}

			InventoryStockEntity stock = stockRepository.findByTenantIdAndSkuIdForUpdate(reservation.getTenantId(), reservation.getSkuId())
				.orElse(null);
			if (stock == null) {
				return StockReleaseResponse.fail("STOCK_NOT_FOUND");
			}
			stock.setReservedQuantity(stock.getReservedQuantity() - reservation.getQuantity());
			stockRepository.save(stock);
			reservation.setStatus(STATUS_RELEASED);
			reservation.setReleaseReason(request.getReason());
			reservationRepository.save(reservation);
		}
		return StockReleaseResponse.ok("ok");
	}

	@Transactional
	public StockRestockResponse restock(String idempotencyKey, StockRestockRequest request) {
		Set<String> seenSkuIds = new HashSet<>();
		for (StockRestockRequest.Line line : request.getItems()) {
			if (!seenSkuIds.add(line.getSkuId())) {
				return StockRestockResponse.fail("DUPLICATE_SKU_ID");
			}
		}

		String refundId = request.getRefundId();
		if (adjustmentRepository.existsByReasonAndReferenceId(ADJUST_REASON_RESTOCK_REFUND, refundId)) {
			return StockRestockResponse.ok("ok");
		}

		StockRestockRequest.Line first = request.getItems().get(0);
		long totalDelta = 0;
		for (StockRestockRequest.Line line : request.getItems()) {
			totalDelta += line.getQuantity();
		}
		adjustmentRepository.save(new InventoryAdjustmentEntity()
			.setTenantId(request.getTenantId())
			.setSkuId(first.getSkuId())
			.setDeltaTotal(totalDelta)
			.setReason(ADJUST_REASON_RESTOCK_REFUND)
			.setReferenceId(refundId));

		for (StockRestockRequest.Line line : request.getItems()) {
			InventoryStockEntity stock = stockRepository.findByTenantIdAndSkuIdForUpdate(request.getTenantId(), line.getSkuId())
				.orElse(null);
			if (stock == null) {
				return StockRestockResponse.fail("STOCK_NOT_FOUND");
			}
			stock.setTotalQuantity(stock.getTotalQuantity() + line.getQuantity());
			stockRepository.save(stock);
		}
		return StockRestockResponse.ok("ok");
	}

	@Transactional
	public int expireReservations() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		List<InventoryReservationEntity> expired = reservationRepository.findByStatusAndExpireAtBefore(STATUS_RESERVED, now);
		int expiredCount = 0;
		for (InventoryReservationEntity reservation : expired) {
			InventoryReservationEntity locked = reservationRepository.findByReservationIdForUpdate(reservation.getReservationId()).orElse(null);
			if (locked == null) {
				continue;
			}
			if (!STATUS_RESERVED.equals(locked.getStatus())) {
				continue;
			}
			InventoryStockEntity stock = stockRepository.findByTenantIdAndSkuIdForUpdate(locked.getTenantId(), locked.getSkuId()).orElse(null);
			if (stock == null) {
				continue;
			}
			stock.setReservedQuantity(stock.getReservedQuantity() - locked.getQuantity());
			stockRepository.save(stock);
			locked.setStatus(STATUS_EXPIRED);
			locked.setReleaseReason("EXPIRED");
			reservationRepository.save(locked);
			expiredCount++;
		}
		return expiredCount;
	}

	private StockPreOccupyResponse replayPreOccupy(List<InventoryReservationEntity> existing, StockPreOccupyRequest request) {
		if (existing.size() != request.getLines().size()) {
			return StockPreOccupyResponse.fail(List.of(), "IDEMPOTENCY_CONFLICT");
		}

		Map<String, InventoryReservationEntity> bySku = new HashMap<>();
		for (InventoryReservationEntity reservation : existing) {
			if (!reservation.getTenantId().equals(request.getTenantId()) || !reservation.getTradeId().equals(request.getTradeId())) {
				return StockPreOccupyResponse.fail(List.of(), "IDEMPOTENCY_CONFLICT");
			}
			bySku.put(reservation.getSkuId(), reservation);
		}

		List<String> preOccupyIds = new ArrayList<>();
		long expiresAtEpochMs = 0;
		for (StockPreOccupyRequest.Line line : request.getLines()) {
			InventoryReservationEntity reservation = bySku.get(line.getSkuId());
			if (reservation == null) {
				return StockPreOccupyResponse.fail(List.of(), "IDEMPOTENCY_CONFLICT");
			}
			if (reservation.getQuantity() != line.getQuantity()) {
				return StockPreOccupyResponse.fail(List.of(line.getSkuId()), "IDEMPOTENCY_CONFLICT");
			}
			preOccupyIds.add(reservation.getReservationId());
			expiresAtEpochMs = expiresAtEpochMs == 0 ? reservation.getExpireAt().toInstant().toEpochMilli() : Math.min(expiresAtEpochMs, reservation.getExpireAt().toInstant().toEpochMilli());
		}

		return StockPreOccupyResponse.ok(preOccupyIds, expiresAtEpochMs);
	}
}
