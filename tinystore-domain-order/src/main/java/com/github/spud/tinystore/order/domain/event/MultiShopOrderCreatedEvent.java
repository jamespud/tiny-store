package com.github.spud.tinystore.order.domain.event;

import java.util.List;

/**
 * @author Spud
 * @date 2025/10/6
 */
public class MultiShopOrderCreatedEvent {

  public MultiShopOrderCreatedEvent(String mainOrderNo, String tenantId, String userId,
    List<Object> collect) {
  }

  public MultiShopOrderCreatedEvent(String mainOrderNo, String userId,
    List<Object> collect) {
  }

  public static class SubOrderRef {

    public SubOrderRef(String subOrderNo, String merchantId, String stockPreOccupyIds,
      String couponLockId) {
    }
  }
}
