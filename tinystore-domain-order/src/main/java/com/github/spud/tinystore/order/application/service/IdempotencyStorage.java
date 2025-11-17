package com.github.spud.tinystore.order.application.service;

/**
 * 幂等结果回放存储抽象。
 * 说明：接口放置于应用层包名以匹配现有引用，便于依赖注入。
 */
public interface IdempotencyStorage {

  /**
   * 判断幂等键是否已存在成功结果。
   */
  boolean exists(String key);

  /**
   * 读取已存储的成功结果，并按类型转换返回；未命中或已过期返回 null。
   */
  <T> T getResponse(String key, Class<T> type);

  /**
   * 保存成功结果用于回放，按全局或入参 TTL 进行过期管理。
   */
  void saveResponse(String key, Object value);

  /**
   * 主动删除某个幂等键。
   */
  void evict(String key);
}
