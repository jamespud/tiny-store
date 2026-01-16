package com.github.spud.tinystore.auth.testsupport;

import com.github.spud.tinystore.auth.application.port.out.LockAndRateLimitPort;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 内存版本的锁和限流端口实现，用于测试
 * 使用 ConcurrentHashMap 和 ReentrantLock 提供稳定的并发控制
 */
public class InMemoryLockAndRateLimitPort implements LockAndRateLimitPort {

    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
    private final Map<String, String> storage = new ConcurrentHashMap<>();
    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> expiryTimes = new ConcurrentHashMap<>();

    @Override
    public <T> T withLock(String key, Duration lockTimeout, Supplier<T> action) {
        Lock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        
        boolean acquired;
        try {
            acquired = lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to acquire lock for key: " + key, e);
        }
        
        if (!acquired) {
            throw new RuntimeException("Failed to acquire lock for key: " + key);
        }

        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String get(String key) {
        cleanExpired(key);
        return storage.get(key);
    }

    @Override
    public void setIfAbsent(String key, String value, Duration ttl) {
        storage.putIfAbsent(key, value);
        if (ttl != null) {
            expiryTimes.put(key, System.currentTimeMillis() + ttl.toMillis());
        }
    }

    @Override
    public long increment(String key, Duration window) {
        cleanExpired(key);
        
        Long currentValue = counters.compute(key, (k, v) -> (v == null) ? 1L : v + 1);
        
        if (!expiryTimes.containsKey(key) && window != null) {
            expiryTimes.put(key, System.currentTimeMillis() + window.toMillis());
        }
        
        return currentValue;
    }

    /**
     * 清理过期的键
     */
    private void cleanExpired(String key) {
        Long expiryTime = expiryTimes.get(key);
        if (expiryTime != null && System.currentTimeMillis() > expiryTime) {
            storage.remove(key);
            counters.remove(key);
            expiryTimes.remove(key);
        }
    }

    /**
     * 清空所有数据（用于测试之间的清理）
     */
    public void clear() {
        locks.clear();
        storage.clear();
        counters.clear();
        expiryTimes.clear();
    }

    /**
     * 获取当前计数器值（用于测试断言）
     */
    public Long getCounter(String key) {
        cleanExpired(key);
        return counters.get(key);
    }

    /**
     * 检查键是否存在（用于测试断言）
     */
    public boolean exists(String key) {
        cleanExpired(key);
        return storage.containsKey(key) || counters.containsKey(key);
    }
}
