package com.github.spud.tinystore.inventory.application.service;

import cn.hutool.core.lang.Pair;
import com.github.spud.tinystore.infrastructure.tool.JsonUtils;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryAdjustmentEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import com.github.spud.tinystore.inventory.infrastructure.producer.StockDeductProducer;
import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;
import com.github.spud.tinystore.inventory.interfaces.dto.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 库存旧应用服务（DB 预占 + Redis reserve 链路）
 *
 * @deprecated 使用 {@link InventoryDeductAppService} 替代
 */
@Deprecated
@Slf4j
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
    private final InventoryRedisManager inventoryRedisManager;
    private final StringRedisTemplate redisTemplate;
    private final StockDeductProducer stockDeductProducer;

    public StockAppService(
            JpaInventoryStockRepository stockRepository,
            JpaInventoryReservationRepository reservationRepository,
            JpaInventoryAdjustmentRepository adjustmentRepository, InventoryRedisManager inventoryRedisManager,
            StringRedisTemplate redisTemplate,
            StockDeductProducer stockDeductProducer) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.inventoryRedisManager = inventoryRedisManager;
        this.redisTemplate = redisTemplate;
        this.stockDeductProducer = stockDeductProducer;
    }

    public StockPreOccupyResponse reserve(String idempotencyKey, StockReserveRequest request) {
        Boolean duplicated = redisTemplate.hasKey(idempotencyKey);
        if (duplicated) {
            return JsonUtils.fromJson(redisTemplate.opsForValue().get(idempotencyKey), StockPreOccupyResponse.class);
        }

        for (StockReserveRequest.OrderLine orderLine : request.getOrderLines()) {
            if (orderLine.hasDuplicateSkus()) {
                return StockPreOccupyResponse.fail(List.of(), "DUPLICATE_SKU_ID");
            }
        }
        
        OffsetDateTime expireAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(request.getExpiresAtEpochMs()), ZoneOffset.UTC);
        if (expireAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            return StockPreOccupyResponse.fail(List.of(), "EXPIRED_AT_IN_PAST");
        }

        HashMap<String, Integer> lackSkuMap = new HashMap<>();
        List<InventoryRedisManager.DeductResult> successes = new ArrayList<>();
        
        List<StockReserveRequest.OrderLine> orderLines = request.getOrderLines();
        for (StockReserveRequest.OrderLine orderLine : orderLines) {
            if (!lackSkuMap.isEmpty()) {
                break;
            }
            for (StockReserveRequest.SkuLine line : orderLine.getSkuLines()) {
                String rs = inventoryRedisManager.preDeductInventory(line.getSkuId(), line.getQuantity(), orderLine.getOrderId());
                if (!StringUtils.hasText(rs)) {
                    lackSkuMap.put(line.getSkuId(), line.getQuantity());
                    break;
                } else {
                    successes.add(new InventoryRedisManager.DeductResult(orderLine.getOrderId(), line.getSkuId(), String.valueOf(line.getQuantity()), rs));
                }
            }
        }
        
        if (!lackSkuMap.isEmpty()) {
            for (InventoryRedisManager.DeductResult result : successes) {
                inventoryRedisManager.rollbackPreDeduct(result.getSkuId(), result.getReserveId());
            }
            return StockPreOccupyResponse.fail(new ArrayList<>(lackSkuMap.keySet()), "STOCK_LACK");
        }
        
        // TODO: 写入kafka
        boolean produced = stockDeductProducer.produce(successes);
        if (!produced) {
            // TODO: 处理发送失败
        }

        List<String> list = successes.stream()
                .map(InventoryRedisManager.DeductResult::getReserveId)
                .toList();
        StockPreOccupyResponse response = StockPreOccupyResponse.ok(list, request.getExpiresAtEpochMs());
        redisTemplate.opsForValue().set(idempotencyKey, JsonUtils.toJson(response));
        return response;
    }
    
    public Object releaseRedis(String idempotencyKey, StockReleaseRequest request) {
        if (redisTemplate.hasKey(idempotencyKey)) {
            // TODO: 返回幂等结果
            return StockReleaseResponse.ok("ok");
        }
        for (Pair<String, String> pair : request.getSkuIdOccupyIdPairs()) {
            // TODO: 异常处理
            inventoryRedisManager.rollbackPreDeduct(pair.getKey(), pair.getValue());
        }
        
        return StockReleaseResponse.ok("ok");
    }
    
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    private void releaseStockTask() {
        // TODO: 获取所有需要清理的SKU列表
        List<String> skudIds = List.of();
        long timeoutTimestamp = System.currentTimeMillis() - 30 * 60 * 1000;
        for (String skuId : skudIds) {
            Long released = inventoryRedisManager.cleanTimeoutUncommit(skuId, timeoutTimestamp);
            log.info("Released {} uncommitted reservations for SKU {}", released, skuId);
            // TODO: 写入kafka
        }
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
            Optional<InventoryStockEntity> stockOpt = stockRepository.findByShopIdAndSkuIdForUpdate(request.getShopId(), line.getSkuId());
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
                    .setShopId(request.getShopId())
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
            if (!reservation.getShopId().equals(request.getShopId()) || !reservation.getTradeId().equals(request.getTradeId())) {
                return StockCommitResponse.fail("RESERVATION_MISMATCH");
            }
            if (STATUS_COMMITTED.equals(reservation.getStatus())) {
                continue;
            }
            if (!STATUS_RESERVED.equals(reservation.getStatus())) {
                return StockCommitResponse.fail("RESERVATION_NOT_RESERVED");
            }
            InventoryStockEntity stock = stockRepository.findByShopIdAndSkuIdForUpdate(reservation.getShopId(), reservation.getSkuId())
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
            if (!reservation.getShopId().equals(request.getShopId()) || !reservation.getTradeId().equals(request.getTradeId())) {
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

            InventoryStockEntity stock = stockRepository.findByShopIdAndSkuIdForUpdate(reservation.getShopId(), reservation.getSkuId())
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
                .setShopId(request.getShopId())
                .setSkuId(first.getSkuId())
                .setDeltaTotal(totalDelta)
                .setReason(ADJUST_REASON_RESTOCK_REFUND)
                .setReferenceId(refundId));

        for (StockRestockRequest.Line line : request.getItems()) {
            InventoryStockEntity stock = stockRepository.findByShopIdAndSkuIdForUpdate(request.getShopId(), line.getSkuId())
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
            InventoryStockEntity stock = stockRepository.findByShopIdAndSkuIdForUpdate(locked.getShopId(), locked.getSkuId()).orElse(null);
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
            if (!reservation.getShopId().equals(request.getShopId()) || !reservation.getTradeId().equals(request.getTradeId())) {
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

