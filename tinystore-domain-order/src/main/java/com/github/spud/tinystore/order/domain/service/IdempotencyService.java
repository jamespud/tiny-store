package com.github.spud.tinystore.order.domain.service;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等性服务接口
 * 
 * @author Spud
 * @date 2025/9/22
 */
public interface IdempotencyService {
    
    /**
     * 尝试使用幂等键
     * 
     * @param scope 作用域（如接口名称）
     * @param key 幂等键
     * @param ttl 过期时间
     * @return true-首次使用，false-重复请求
     */
    boolean tryUse(String scope, String key, Duration ttl);
    
    /**
     * 查找幂等记录
     * 
     * @param scope 作用域
     * @param key 幂等键
     * @return 幂等记录（如果存在）
     */
    Optional<IdempotencyRecord> find(String scope, String key);
    
    /**
     * 幂等记录
     */
    record IdempotencyRecord(
        String scope,
        String key,
        String result,
        long createdAt,
        long expireAt
    ) {}
}