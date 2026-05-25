package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.InventoryProjectionVersion;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.model.InventoryOccupyPair;
import com.github.spud.tinystore.order.domain.model.InventoryReservationRef;
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
        // 查找现有实体
        ShopOrderEntity entity = shopOrderJpaRepository.findByOrderId(shopOrder.getOrderId())
            .orElse(null);
        
        boolean isNew = (entity == null);

        if (isNew) {
            // 创建新实体
            entity = toEntity(shopOrder);
            entity = shopOrderJpaRepository.save(entity);

            // 保存订单行（仅在创建时写入）
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
        } else {
            // 更新现有managed实体的字段（直接修改，JPA会自动脏检查）
            entity.setOrderStatus(shopOrder.getOrderStatus() != null ? shopOrder.getOrderStatus().getCode() : null);
            entity.setInventoryStatus(shopOrder.getInventoryStatus());
            entity.setPromotionStatus(shopOrder.getPromotionStatus());
            entity.setAcceptedAt(shopOrder.getAcceptedAt());
            entity.setUpdatedAt(LocalDateTime.now());
            
            // 更新库存占用凭证（仅 V2 occupyPairs）
            try {
                if (shopOrder.getInventoryOccupyPairs() != null && !shopOrder.getInventoryOccupyPairs().isEmpty()) {
                    entity.setInventoryPreOccupyIdsJson(objectMapper.writeValueAsString(shopOrder.getInventoryOccupyPairs()));
                } else {
                    entity.setInventoryPreOccupyIdsJson(null);
                }
            } catch (Exception e) {
                log.error("Failed to serialize inventory occupy info for orderId={}", shopOrder.getOrderId(), e);
            }

            // 更新 canonical reservation refs（V2 orders）
            try {
                if (shopOrder.getInventoryReservationRefs() != null && !shopOrder.getInventoryReservationRefs().isEmpty()) {
                    entity.setInventoryReservationRefsJson(objectMapper.writeValueAsString(shopOrder.getInventoryReservationRefs()));
                } else {
                    entity.setInventoryReservationRefsJson(null);
                }
            } catch (Exception e) {
                log.error("Failed to serialize inventory reservation refs for orderId={}", shopOrder.getOrderId(), e);
            }
            if (shopOrder.getInventoryProjectionVersion() != null) {
                entity.setInventoryProjectionVersion(shopOrder.getInventoryProjectionVersion().getValue());
            }
            
            // 显式调用save以确保更新（实际上JPA会在事务提交时自动flush）
            entity = shopOrderJpaRepository.save(entity);
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

    @Override
    public Boolean saveAll(List<ShopOrder> orders) {
        orders.forEach(this::save);
        return true;
    }

    // =========== Entity <-> Domain Model 转换 ===========

    private ShopOrderEntity toEntity(ShopOrder shopOrder) {
        String occupyPairsJson = null;
        try {
            if (shopOrder.getInventoryOccupyPairs() != null && !shopOrder.getInventoryOccupyPairs().isEmpty()) {
                occupyPairsJson = objectMapper.writeValueAsString(shopOrder.getInventoryOccupyPairs());
            }
        } catch (Exception e) {
            log.error("Failed to serialize inventory occupy info for orderId={}", shopOrder.getOrderId(), e);
        }
        
        String reservationRefsJson = null;
        try {
            if (shopOrder.getInventoryReservationRefs() != null && !shopOrder.getInventoryReservationRefs().isEmpty()) {
                reservationRefsJson = objectMapper.writeValueAsString(shopOrder.getInventoryReservationRefs());
            }
        } catch (Exception e) {
            log.error("Failed to serialize inventory reservation refs for orderId={}", shopOrder.getOrderId(), e);
        }

        return ShopOrderEntity.builder()
            .id(shopOrder.getId())
            .orderId(shopOrder.getOrderId())
            .tradeId(shopOrder.getTradeId())
            .shopId(shopOrder.getShopId())
            .sellerId(shopOrder.getSellerId())
            .orderStatus(shopOrder.getOrderStatus() != null ? shopOrder.getOrderStatus().getCode() : null)
            .inventoryStatus(shopOrder.getInventoryStatus())
            .promotionStatus(shopOrder.getPromotionStatus())
            .inventoryPreOccupyIdsJson(occupyPairsJson)
            .inventoryReservationRefsJson(reservationRefsJson)
            .inventoryProjectionVersion(shopOrder.getInventoryProjectionVersion() != null
                    ? shopOrder.getInventoryProjectionVersion().getValue() : 1)
            .createdAt(shopOrder.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .acceptedAt(shopOrder.getAcceptedAt())
            .build();
    }

    private ShopOrder toDomain(ShopOrderEntity entity, List<OrderLine> orderLines) {
        List<InventoryOccupyPair> occupyPairs = List.of();
        try {
            String json = entity.getInventoryPreOccupyIdsJson();
            if (json != null && !json.isEmpty()) {
                occupyPairs = objectMapper.readValue(json,
                    new TypeReference<List<InventoryOccupyPair>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to deserialize inventory occupy info for orderId={}", entity.getOrderId(), e);
            occupyPairs = List.of();
        }
        
        List<InventoryReservationRef> reservationRefs = List.of();
        try {
            String refsJson = entity.getInventoryReservationRefsJson();
            if (refsJson != null && !refsJson.isEmpty()) {
                reservationRefs = objectMapper.readValue(refsJson,
                    new TypeReference<List<InventoryReservationRef>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to deserialize inventory reservation refs for orderId={}", entity.getOrderId(), e);
        }

        return ShopOrder.builder()
            .id(entity.getId())
            .orderId(entity.getOrderId())
            .tradeId(entity.getTradeId())
            .shopId(entity.getShopId())
            .sellerId(entity.getSellerId())
            .orderStatus(entity.getOrderStatus() != null ? OrderStatus.valueOf(entity.getOrderStatus()) : null)
            .inventoryStatus(entity.getInventoryStatus())
            .promotionStatus(entity.getPromotionStatus())
            .inventoryOccupyPairs(occupyPairs)
            .inventoryReservationRefs(reservationRefs)
            .inventoryProjectionVersion(InventoryProjectionVersion.fromValue(entity.getInventoryProjectionVersion()))
            .orderLines(orderLines)
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .acceptedAt(entity.getAcceptedAt())
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
