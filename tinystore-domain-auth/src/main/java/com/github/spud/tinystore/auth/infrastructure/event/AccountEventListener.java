package com.github.spud.tinystore.auth.infrastructure.event;

import com.github.spud.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.contracts.account.events.UserCreatedEvent;
import com.github.spud.tinystore.contracts.account.events.UserCredentialChangedEvent;
import com.github.spud.tinystore.contracts.account.events.UserStatusChangedEvent;
import com.github.spud.tinystore.contracts.account.events.UserUpdatedEvent;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AccountEventListener {

  private final CacheManager cacheManager;
  private final AuthorizationStorePort authorizationStorePort;

  @Bean
  public Consumer<UserCreatedEvent> userCreated() {
    return event -> {
      log.info("Received UserCreatedEvent: {}", event);
      if (cacheManager != null) {
        cacheManager.getCache("users").evict(String.valueOf(event.userId()));
        cacheManager.getCache("usersByPhone").evict(event.phone());
      }
    };
  }

  @Bean
  public Consumer<UserUpdatedEvent> userUpdated() {
    return event -> {
      log.info("Received UserUpdatedEvent: {}", event);
      // 清理相关缓存
      if (cacheManager != null) {
        cacheManager.getCache("users").evict(String.valueOf(event.userId()));
      }
    };
  }

  @Bean
  public Consumer<UserStatusChangedEvent> userStatusChanged() {
    return event -> {
      log.info("Received UserStatusChangedEvent: {}", event);
      // 清理相关缓存
      if (cacheManager != null) {
        cacheManager.getCache("users").evict(String.valueOf(event.userId()));
      }
      if (event.newStatus() != null && event.newStatus() != 1) {
        authorizationStorePort.clearAuthorizationsOf(UserId.of(String.valueOf(event.userId())));
      }
    };
  }

  @Bean
  public Consumer<UserCredentialChangedEvent> userCredentialChanged() {
    return event -> {
      log.info("Received UserCredentialChangedEvent: {}", event);
      authorizationStorePort.clearAuthorizationsOf(UserId.of(String.valueOf(event.userId())));
    };
  }
}
