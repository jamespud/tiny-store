package com.github.spud.tinystore.order.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.OrderItem;
import com.github.spud.tinystore.order.domain.model.line.LineItem;
import com.github.spud.tinystore.order.domain.model.line.SkuSnapshot;
import com.github.spud.tinystore.order.domain.statemachine.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.OrderStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.PaymentStatus;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderItemEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderMainEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderSubEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderItemJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderMainJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderSubJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderOutboxEventRepository;
import com.github.spud.tinystore.order.infrastructure.tenant.TenantContext;
import com.github.spud.tinystore.order.interfaces.error.OrderBusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/6
 */
@Slf4j
@Service
public class OrderDomainService {

  private final OrderMainJpaRepository orderMainJpaRepository;
  private final OrderSubJpaRepository orderSubJpaRepository;
  private final OrderItemJpaRepository orderItemJpaRepository;
  private final OrderOutboxEventRepository orderOutboxEventRepository;
  private final OutboxEventService outboxEventService;
  private final ObjectMapper objectMapper;

  public OrderDomainService(
    OrderMainJpaRepository orderMainJpaRepository,
    OrderSubJpaRepository orderSubJpaRepository,
    OrderItemJpaRepository orderItemJpaRepository,
    OrderOutboxEventRepository orderOutboxEventRepository,
    OutboxEventService outboxEventService,
    ObjectMapper objectMapper
  ) {
    this.orderMainJpaRepository = orderMainJpaRepository;
    this.orderSubJpaRepository = orderSubJpaRepository;
    this.orderItemJpaRepository = orderItemJpaRepository;
    this.orderOutboxEventRepository = orderOutboxEventRepository;
    this.outboxEventService = outboxEventService;
    this.objectMapper = objectMapper;
  }

  public void saveOrderLine(OrderItem order) {
    if (order == null || order.getOrderNo() == null || order.getOrderNo().isBlank()) {
      return;
    }
    save(order, order.getVersion() == null ? 0L : order.getVersion());
  }

  /**
   * 加载订单用于更新（带版本锁）
   *
   * @param orderId 订单ID
   * @return 订单聚合
   */
  public OrderItem loadForUpdate(String orderId) {
    String mainOrderNo = resolveMainOrderNo(orderId);
    if (mainOrderNo == null) {
      throw new OrderBusinessException("Order not found: " + orderId, "ORDER-4040",
        HttpStatus.NOT_FOUND);
    }
    OrderMainEntity main = orderMainJpaRepository.findForUpdateByOrderNo(mainOrderNo)
      .orElseThrow(() -> new OrderBusinessException("Order not found: " + mainOrderNo, "ORDER-4040",
        HttpStatus.NOT_FOUND));
    List<OrderItemEntity> items = orderItemJpaRepository.findByMainOrderNoOrderByLineNoAsc(
      mainOrderNo);
    List<LineItem> lines = new ArrayList<>();
    for (OrderItemEntity it : items) {
      lines.add(mapToLineItem(it));
    }
    OrderStatus status = new OrderStatus(
      CoreFlowStatus.valueOf(main.getCoreFlowStatus()),
      PaymentStatus.valueOf(main.getPaymentStatus()),
      FulfillmentStatus.valueOf(main.getFulfillmentStatus()),
      AfterSaleStatus.valueOf(main.getAfterSaleStatus())
    );
    OrderItem agg = new OrderItem();
    agg.setId(main.getId() != null ? String.valueOf(main.getId()) : null);
    agg.setOrderNo(main.getOrderNo());
    agg.setUserId(main.getUserId());
    agg.setTenantId(main.getTenantId());
    agg.setLines(lines);
    agg.setTotal(Money.ofCents(main.getTotalAmount() == null ? 0L : main.getTotalAmount(),
      main.getCurrency()));
    agg.setPayable(Money.ofCents(main.getPayableAmount() == null ? 0L : main.getPayableAmount(),
      main.getCurrency()));
    agg.setOrderStatus(status);
    agg.setVersion(main.getVersion() == null ? 0 : main.getVersion().intValue());
    return agg;
  }

  /**
   * 保存订单（带版本检查）
   *
   * @param order           订单聚合
   * @param expectedVersion 期望版本号
   */
  public void save(OrderItem order, long expectedVersion) {
    if (order == null || order.getOrderNo() == null || order.getOrderNo().isBlank()) {
      return;
    }
    OrderMainEntity main = orderMainJpaRepository.findByOrderNo(order.getOrderNo())
      .orElseThrow(
        () -> new OrderBusinessException("Order not found: " + order.getOrderNo(), "ORDER-4040",
          HttpStatus.NOT_FOUND));
    Long currentVersion = main.getVersion() == null ? 0L : main.getVersion();
    if (currentVersion != expectedVersion) {
      throw new OrderBusinessException("Version conflict", "ORDER-4090", HttpStatus.CONFLICT);
    }
    OrderStatus st = order.getOrderStatus();
    if (st != null) {
      main.setCoreFlowStatus(st.getCoreFlowStatus().name());
      main.setPaymentStatus(st.getPaymentStatus().name());
      main.setFulfillmentStatus(st.getFulfillmentStatus().name());
      main.setAfterSaleStatus(st.getAfterSaleStatus().name());
    }
    if (order.getTotal() != null) {
      main.setTotalAmount(order.getTotal().amount());
      main.setCurrency(order.getTotal().currency());
    }
    if (order.getPayable() != null) {
      main.setPayableAmount(order.getPayable().amount());
      main.setCurrency(order.getPayable().currency());
    }
    orderMainJpaRepository.save(main);
    List<OrderSubEntity> subs = orderSubJpaRepository.findByMainOrderNo(main.getOrderNo());
    if (st != null && subs != null && !subs.isEmpty()) {
      for (OrderSubEntity s : subs) {
        s.setCoreFlowStatus(st.getCoreFlowStatus().name());
        s.setPaymentStatus(st.getPaymentStatus().name());
        s.setFulfillmentStatus(st.getFulfillmentStatus().name());
        s.setAfterSaleStatus(st.getAfterSaleStatus().name());
      }
      orderSubJpaRepository.saveAll(subs);
    }
  }

  /**
   * 记录状态变更日志
   *
   * @param orderId 订单ID
   * @param from    原状态
   * @param to      目标状态
   * @param reason  变更原因
   * @param actor   操作者
   * @param key     幂等键或事件ID
   */
  public void appendStatusLog(UUID orderId, CoreFlowStatus from, CoreFlowStatus to,
    String reason, String actor, String key) {
    log.info("Order status change: orderId={}, from={}, to={}, reason={}, actor={}, key={}",
      orderId, from, to, reason, actor, key);
  }

  /**
   * 记录 Outbox 事件
   *
   * @param order     订单聚合
   * @param eventType 事件类型
   * @param payload   事件载荷
   */
  public void recordOutbox(OrderItem order, String eventType, Object payload) {
    if (order == null || order.getOrderNo() == null || order.getOrderNo().isBlank()) {
      return;
    }
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      tenantId = order.getTenantId();
    }
    String operatorId = MDC.get("actorId");
    if (operatorId == null || operatorId.isBlank()) {
      operatorId = TenantContext.getUserId();
    }
    if (operatorId == null || operatorId.isBlank()) {
      operatorId = "system";
    }
    Map<String, Object> envelope = outboxEventService.buildEvent(
      eventType,
      order.getOrderNo(),
      null,
      tenantId,
      operatorId,
      payload
    );
    String eventId = UUID.randomUUID().toString();
    String traceId = MDC.get("traceId");
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(envelope);
    } catch (Exception e) {
      payloadJson = "{}";
    }
    OrderOutboxEventPO po = new OrderOutboxEventPO()
      .setId(eventId)
      .setOrderId(order.getOrderNo())
      .setEventType(eventType)
      .setEventPayload(payloadJson)
      .setTraceId(traceId)
      .setStatus(OrderOutboxEventPO.OutboxEventStatus.PENDING)
      .setRetryCount(0);
    orderOutboxEventRepository.save(po);
  }

  private String resolveMainOrderNo(String orderIdOrNo) {
    if (orderIdOrNo == null || orderIdOrNo.isBlank()) {
      return null;
    }
    Optional<OrderMainEntity> main = orderMainJpaRepository.findByOrderNo(orderIdOrNo);
    if (main.isPresent()) {
      return orderIdOrNo;
    }
    Optional<OrderSubEntity> sub = orderSubJpaRepository.findBySubOrderNo(orderIdOrNo);
    return sub.map(OrderSubEntity::getMainOrderNo).orElse(null);
  }

  private LineItem mapToLineItem(OrderItemEntity it) {
    SkuSnapshot snap = null;
    if (it.getSkuSnapshot() != null && !it.getSkuSnapshot().isBlank()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = objectMapper.readValue(it.getSkuSnapshot(), Map.class);
        String skuId = m.get("skuId") != null ? String.valueOf(m.get("skuId")) : it.getSkuId();
        String title = m.get("title") != null ? String.valueOf(m.get("title")) : skuId;
        String specJson = m.get("specJson") != null ? String.valueOf(m.get("specJson")) : null;
        String currency =
          m.get("currency") != null ? String.valueOf(m.get("currency")) : it.getCurrency();
        long unitPriceCents = 0L;
        Object up = m.get("unitPriceCents");
        if (up instanceof Number n) {
          unitPriceCents = n.longValue();
        } else if (up != null) {
          try {
            unitPriceCents = Long.parseLong(String.valueOf(up));
          } catch (Exception ignored) {
          }
        }
        Instant snapshotTime = Instant.now();
        Object st = m.get("snapshotTime");
        if (st != null) {
          try {
            snapshotTime = Instant.parse(String.valueOf(st));
          } catch (Exception ignored) {
          }
        }
        snap = new SkuSnapshot(
          skuId,
          title,
          specJson,
          Money.ofCents(unitPriceCents, currency == null ? "CNY" : currency),
          currency == null ? "CNY" : currency,
          snapshotTime
        );
      } catch (Exception ignored) {
        snap = null;
      }
    }
    Money unit = Money.ofCents(it.getUnitPriceAmount() == null ? 0L : it.getUnitPriceAmount(),
      it.getCurrency());
    Money total = Money.ofCents(it.getLineTotalAmount() == null ? 0L : it.getLineTotalAmount(),
      it.getCurrency());
    Money discount = Money.ofCents(
      it.getLineDiscountAmount() == null ? 0L : it.getLineDiscountAmount(), it.getCurrency());
    Money payable = Money.ofCents(
      it.getLinePayableAmount() == null ? 0L : it.getLinePayableAmount(), it.getCurrency());
    return LineItem.builder()
      .skuSnapshot(snap)
      .quantity(it.getQuantity() == null ? 0 : it.getQuantity())
      .unitPrice(unit)
      .lineTotal(total)
      .lineDiscount(discount)
      .linePayable(payable)
      .build();
  }
}
