package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.model.FulfillmentPackage;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.repository.FulfillmentPackageRepository;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PackageOrderRefEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PackageOrderRefJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商家履约应用服务（发货、确认收货等）
 */
@Slf4j
@Service
public class MerchantFulfillmentService {

    @Autowired
    private ShopOrderRepository shopOrderRepository;

    @Autowired
    private FulfillmentPackageRepository fulfillmentPackageRepository;

    @Autowired
    private PackageOrderRefJpaRepository packageOrderRefJpaRepository;

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 商家同意订单（接单逻辑，可选）
     *
     * @param orderId 子单ID
     * @param traceId 追踪ID
     */
    @Transactional
    public void merchantAcceptOrder(String orderId, String traceId) {
        try {
            ShopOrder shopOrder = shopOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DomainConflictException("ORDER_NOT_FOUND",
                    "ShopOrder not found: " + orderId));

            // 接单（调用聚合根方法）
            shopOrder.accept();
            shopOrderRepository.save(shopOrder);

            log.info("Merchant accepted order: orderId={}", orderId);

        } catch (Exception e) {
            log.error("Merchant order acceptance failed: orderId={}", orderId, e);
            throw e;
        }
    }

    /**
     * 商家发货
     *
     * @param orderId 子单ID
     * @param packageId 包裹ID（新生成或复用）
     * @param waybillNo 运单号
     * @param logistics 物流公司
     * @param traceId 追踪ID
     */
    @Transactional
    public void shipOrder(String orderId, String packageId, String waybillNo, 
                         String logistics, String traceId) throws Exception {
        try {
            ShopOrder shopOrder = shopOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DomainConflictException("ORDER_NOT_FOUND",
                    "ShopOrder not found: " + orderId));

            // 查找或创建 FulfillmentPackage（使用聚合根）
            FulfillmentPackage pkg = fulfillmentPackageRepository.findByPackageId(packageId)
                .orElse(FulfillmentPackage.builder()
                    .packageId(packageId)
                    .tradeId(shopOrder.getTradeId())
                    .createdAt(LocalDateTime.now())
                    .build());

            // 发货（调用聚合根方法）
            pkg.ship(waybillNo, logistics);
            fulfillmentPackageRepository.save(pkg);

            // 创建 package-order 关联关系
            PackageOrderRefEntity ref = PackageOrderRefEntity.builder()
                .packageId(packageId)
                .orderId(orderId)
                .createdAt(LocalDateTime.now())
                .build();
            packageOrderRefJpaRepository.save(ref);

            // 更新 ShopOrder 状态（调用聚合根方法）
            shopOrder.markAsPendingReceive();
            shopOrderRepository.save(shopOrder);

            // 写入 Outbox 事件
            OrderDomainEvent shippedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.PACKAGE_SHIPPED)
                .aggregateType("PACKAGE")
                .aggregateId(packageId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "packageId", packageId,
                    "orderId", orderId,
                    "waybillNo", waybillNo,
                    "logistics", logistics
                )))
                .build();
            outboxEventService.saveEvent(shippedEvent);

            log.info("Order shipped: orderId={}, packageId={}, waybillNo={}", 
                orderId, packageId, waybillNo);

        } catch (Exception e) {
            log.error("Order shipment failed: orderId={}, packageId={}", orderId, packageId, e);
            throw e;
        }
    }

    /**
     * 买家确认收货
     *
     * @param orderId 子单ID
     * @param traceId 追踪ID
     */
    @Transactional
    public void confirmReceipt(String orderId, String traceId) throws Exception {
        try {
            ShopOrder shopOrder = shopOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DomainConflictException("ORDER_NOT_FOUND",
                    "ShopOrder not found: " + orderId));

            // 确认收货（使用聚合根方法）
            shopOrder.markAsSuccess();
            shopOrderRepository.save(shopOrder);

            // 更新关联的 Package 状态
            List<PackageOrderRefEntity> refs = packageOrderRefJpaRepository.findByOrderId(orderId);
            for (PackageOrderRefEntity ref : refs) {
                FulfillmentPackage pkg = fulfillmentPackageRepository.findByPackageId(ref.getPackageId())
                    .orElse(null);
                if (pkg != null) {
                    pkg.deliver();
                    fulfillmentPackageRepository.save(pkg);

                    // 写入 Package 已签收事件
                    OrderDomainEvent deliveredEvent = OrderDomainEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(OrderEventType.PACKAGE_DELIVERED)
                        .aggregateType("PACKAGE")
                        .aggregateId(pkg.getPackageId())
                        .occurredAt(LocalDateTime.now())
                        .traceId(traceId)
                        .payloadJson(objectMapper.writeValueAsString(Map.of(
                            "packageId", pkg.getPackageId(),
                            "deliveredAt", pkg.getDeliveredAt().toString()
                        )))
                        .build();
                    outboxEventService.saveEvent(deliveredEvent);
                }
            }

            // 写入 Order 成功事件
            OrderDomainEvent successEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.ORDER_PAID) // 或 SUCCESS，取决于业务需求
                .aggregateType("ORDER")
                .aggregateId(orderId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderId", orderId,
                    "status", "SUCCESS"
                )))
                .build();
            outboxEventService.saveEvent(successEvent);

            log.info("Order receipt confirmed: orderId={}", orderId);

        } catch (Exception e) {
            log.error("Confirm receipt failed: orderId={}", orderId, e);
            throw e;
        }
    }

    /**
     * 包裹签收（物流侧回调或买家确认）
     *
     * @param packageId 包裹ID
     * @param traceId 追踪ID
     */
    @Transactional
    public void markPackageDelivered(String packageId, String traceId) throws Exception {
        try {
            FulfillmentPackage pkg = fulfillmentPackageRepository.findByPackageId(packageId)
                .orElseThrow(() -> new DomainConflictException("PACKAGE_NOT_FOUND",
                    "Package not found: " + packageId));

            // 标记为已签收（使用聚合根方法）
            pkg.deliver();
            fulfillmentPackageRepository.save(pkg);

            // 检查所有关联订单的包裹是否都已签收，若是则更新订单状态
            List<PackageOrderRefEntity> refs = packageOrderRefJpaRepository.findByPackageId(packageId);
            for (PackageOrderRefEntity ref : refs) {
                ShopOrder order = shopOrderRepository.findByOrderId(ref.getOrderId())
                    .orElse(null);
                if (order != null && OrderStatus.PENDING_RECEIVE.getCode().equals(order.getOrderStatus())) {
                    // 检查该订单的所有包裹是否都已签收
                    List<PackageOrderRefEntity> orderRefs = packageOrderRefJpaRepository.findByOrderId(order.getOrderId());
                    boolean allDelivered = orderRefs.stream().allMatch(r -> {
                        return fulfillmentPackageRepository.findByPackageId(r.getPackageId())
                            .map(p -> p.getDeliveredAt() != null)
                            .orElse(false);
                    });

                    if (allDelivered) {
                        order.markAsSuccess();
                        shopOrderRepository.save(order);
                    }
                }
            }

            // 写入 Outbox 事件
            OrderDomainEvent deliveredEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.PACKAGE_DELIVERED)
                .aggregateType("PACKAGE")
                .aggregateId(packageId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "packageId", packageId,
                    "deliveredAt", pkg.getDeliveredAt().toString()
                )))
                .build();
            outboxEventService.saveEvent(deliveredEvent);

            log.info("Package delivered: packageId={}", packageId);

        } catch (Exception e) {
            log.error("Mark package delivered failed: packageId={}", packageId, e);
            throw e;
        }
    }
}
