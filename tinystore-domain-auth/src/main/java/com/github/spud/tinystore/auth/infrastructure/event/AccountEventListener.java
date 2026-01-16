package com.github.spud.tinystore.auth.infrastructure.event;

import com.github.spud.tinystore.account.domain.event.UserCreatedEvent;
import com.github.spud.tinystore.account.domain.event.UserStatusChangedEvent;
import com.github.spud.tinystore.account.domain.event.UserUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AccountEventListener {

    private final CacheManager cacheManager;

    @Bean
    public Consumer<UserCreatedEvent> userCreated() {
        return event -> {
            log.info("Received UserCreatedEvent: {}", event);
            // 可以在这里实现用户创建后的逻辑，比如初始化用户权限、发送欢迎短信等
            // 清理相关缓存
            if (cacheManager != null) {
                cacheManager.getCache("users").evict(event.getUserId());
                cacheManager.getCache("usersByPhone").evict(event.getPhone());
            }
        };
    }

    @Bean
    public Consumer<UserUpdatedEvent> userUpdated() {
        return event -> {
            log.info("Received UserUpdatedEvent: {}", event);
            // 清理相关缓存
            if (cacheManager != null) {
                cacheManager.getCache("users").evict(event.getUserId());
            }
        };
    }

    @Bean
    public Consumer<UserStatusChangedEvent> userStatusChanged() {
        return event -> {
            log.info("Received UserStatusChangedEvent: {}", event);
            // 清理相关缓存
            if (cacheManager != null) {
                cacheManager.getCache("users").evict(event.getUserId());
            }
            // 如果用户被禁用，可以在这里实现强制下线逻辑
            if (event.getNewStatus() == 0) {
                log.warn("User {} has been disabled, need to force logout", event.getUserId());
                // TODO: 实现强制下线逻辑
            }
        };
    }
}