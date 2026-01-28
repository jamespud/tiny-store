package com.github.spud.tinystore.order.domain.event;

import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public class DomainEventPublisher {

  public void publish(MultiShopOrderCreatedEvent multiShopOrderCreatedEvent) {

  }
}
