package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderLineEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ShopOrderEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderLineJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 店铺订单仓储实现（JPA 适配器）
 * 负责 ShopOrderEntity <-> ShopOrder 领域模型转换
 */
@Slf4j
@Repository
public class ShopOrderRepositoryImpl implements ShopOrderRepository {

    @Autowired
    private ShopOrderJpaRepository shopOrderJpaRepository;

    @Autowired
    private OrderLineJpaRepository orderLineJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ShopOrder save(ShopOrder shopOrder) {
        ShopOrderEntity entity = toEntity(shopOrder);
        entity = shopOrderJpaRepository.save(entity);

        // 保存订单行
        for (OrderLine line : shopOrder.getOrderLines()) {
            OrderLineEntity lineEntity = OrderLineEntity.builder()
                .orderId(entity.getOrderId())
                .skuId(line.getSkuId())
                .productId(line.getProductId())
                .productName(line.getProductName())
                .quantity(line.getQuantity())
                .priceCents(line.getPriceCents())
                .lineAmountCents(line.getLineAmountCents())
                .createdAt(LocalDateTime.now())
                .build();
            orderLineJpaRepository.save(lineEntity);
        }

        return toDomain(entity, shopOrder.getOrderLines());
    }

    @Override
    public Optional<ShopOrder> findByOrderId(String orderId) {
        return shopOrderJpaRepository.findByOrderId(orderId)
            .map(entity -> {
                List<OrderLineEntity> lineEntities = orderLineJpaRepository.findByOrderId(orderId);
                List<OrderLine> lines = lineEntities.stream()
                    .map(this::toOrderLineDomain)
                    .collect(Collectors.toList());
                return toDomain(entity, lines);
            });
    }

    @Override
    public List<ShopOrder> findByTradeId(String tradeId) {
        List<ShopOrderEntity> entities = shopOrderJpaRepository.findByTradeId(tradeId);
        return entities.stream()
            .map(entity -> {
                List<OrderLineEntity> lineEntities = orderLineJpaRepository.findByOrderId(entity.getOrderId());
                List<OrderLine> lines = lineEntities.stream()
                    .map(this::toOrderLineDomain)
                    .collect(Collectors.toList());
                return toDomain(entity, lines);
            })
            .collect(Collectors.toList());
    }

    // =========== Entity <-> Domain Model 转换 ===========

    private ShopOrderEntity toEntity(ShopOrder shopOrder) {
        String preOccupyIdsJson = null;
        try {
            if (shopOrder.getInventoryPreOccupyIds() != null && !shopOrder.getInventoryPreOccupyIds().isEmpty()) {
                preOccupyIdsJson = objectMapper.writeValueAsString(shopOrder.getInventoryPreOccupyIds());
            }
        } catch (Exception e) {
            log.error("Failed to serialize inventoryPreOccupyIds for orderId={}", shopOrder.getOrderId(), e);
        }
        
        return ShopOrderEntity.builder()
            .orderId(shopOrder.getOrderId())
            .tradeId(shopOrder.getTradeId())
            .shopId(shopOrder.getShopId())
            .sellerId(shopOrder.getSellerId())
            .orderStatus(shopOrder.getOrderStatus() != null ? shopOrder.getOrderStatus().getCode() : null)
            .inventoryStatus(shopOrder.getInventoryStatus())
            .promotionStatus(shopOrder.getPromotionStatus())
            .inventoryPreOccupyIdsJson(preOccupyIdsJson)
            .createdAt(shopOrder.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private ShopOrder toDomain(ShopOrderEntity entity, List<OrderLine> orderLines) {
        List<String> preOccupyIds = new ArrayList<>();
        try {
            if (entity.getInventoryPreOccupyIdsJson() != null && !entity.getInventoryPreOccupyIdsJson().isEmpty()) {
                preOccupyIds = objectMapper.readValue(
                    entity.getInventoryPreOccupyIdsJson(),
                    new TypeReference<List<String>>() {}
                );
            }
        } catch (Exception e) {
            log.error("Failed to deserialize inventoryPreOccupyIds for orderId={}", entity.getOrderId(), e);
        }
        
        return ShopOrder.builder()
            .orderId(entity.getOrderId())
            .tradeId(entity.getTradeId())
            .shopId(entity.getShopId())
            .sellerId(entity.getSellerId())
            .orderStatus(entity.getOrderStatus() != null ? OrderStatus.valueOf(entity.getOrderStatus()) : null)
            .inventoryStatus(entity.getInventoryStatus())
            .promotionStatus(entity.getPromotionStatus())
            .inventoryPreOccupyIds(preOccupyIds)
            .orderLines(orderLines)
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private OrderLine toOrderLineDomain(OrderLineEntity entity) {
        return OrderLine.builder()
            .skuId(entity.getSkuId())
            .productId(entity.getProductId())
            .productName(entity.getProductName())
            .quantity(entity.getQuantity())
            .priceCents(entity.getPriceCents())
            .lineAmountCents(entity.getLineAmountCents())
            .build();
    }
}
