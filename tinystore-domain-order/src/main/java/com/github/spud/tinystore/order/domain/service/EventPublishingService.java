package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Outbox;

/**
 * 事件发布服务接口 负责将 Outbox 事件发布到消息队列或其他事件总线
 *
 * @author Spud
 * @date 2025/9/22
 */
public interface EventPublishingService {

  /**
   * 发布事件到外部系统（如消息队列）
   *
   * @param event Outbox 事件
   * @throws Exception 发布失败时抛出异常
   */
  void publish(Outbox event) throws Exception;
}