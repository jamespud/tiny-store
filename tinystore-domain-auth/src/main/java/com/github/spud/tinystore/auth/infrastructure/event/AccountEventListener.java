package com.github.spud.tinystore.auth.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.event.UserCreatedEvent;
import com.github.spud.tinystore.auth.domain.event.UserStatusChangedEvent;
import com.github.spud.tinystore.auth.domain.event.UserUpdatedEvent;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AccountEventListener {

    private final CacheManager cacheManager;
    private final UserRepository userRepository;
    private final AccountServiceFeignClient accountServiceFeignClient;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public Consumer<UserCreatedEvent> userCreated() {
        return event -> {
            log.info("Received UserCreatedEvent: {}", event);
            try {
                // 通过 Feign 获取完整用户信息（包含 password）
                AccountServiceFeignClient.UserCoreDto userDto = accountServiceFeignClient.getUserById(Long.parseLong(event.userId().value()));
                if (userDto != null) {
                    // 按"密码写入规则"：判断是否已加密，未加密则 encode
                    String passwordToStore = normalizePassword(userDto.password());
                    
                    // 构建并保存到 auth_user
                    MallUser user = MallUser.restore(
                        event.userId(),
                        PhoneNumber.of(event.phone()),
                        userDto.nickname(),
                        userDto.avatarUrl(),
                        passwordToStore,
                        intToStatus(userDto.accountStatus()),
                        com.github.spud.tinystore.auth.domain.primitives.RtVersion.of(1)
                    );
                    userRepository.save(user);
                    log.info("Synced user {} to auth_user", event.userId());
                }
            } catch (Exception e) {
                log.error("Failed to sync UserCreatedEvent for userId {}", event.userId(), e);
                throw new RuntimeException("Event sync failed, will retry", e);
            }
            
            // 清理相关缓存
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
            try {
                // 通过 Feign 拉取最新信息更新本地
                AccountServiceFeignClient.UserCoreDto userDto = accountServiceFeignClient.getUserById(Long.parseLong(event.userId().value()));
                if (userDto != null) {
                    userRepository.findById(event.userId()).ifPresentOrElse(
                        user -> {
                            // 更新字段
                            user.updateProfile(userDto.nickname(), userDto.avatarUrl());
                            String passwordToStore = normalizePassword(userDto.password());
                            // 手动更新 password 与 status（因为 MallUser 不直接暴露 setter）
                            userRepository.updatePassword(event.userId(), passwordToStore);
                            userRepository.updateStatus(event.userId(), userDto.accountStatus());
                            log.info("Updated user {} in auth_user", event.userId());
                        },
                        () -> {
                            // 若本地不存在，直接创建（补偿性同步）
                            MallUser user = MallUser.restore(
                                event.userId(),
                                PhoneNumber.of(userDto.account()),
                                userDto.nickname(),
                                userDto.avatarUrl(),
                                normalizePassword(userDto.password()),
                                intToStatus(userDto.accountStatus()),
                                com.github.spud.tinystore.auth.domain.primitives.RtVersion.of(1)
                            );
                            userRepository.save(user);
                            log.info("Created missing user {} in auth_user (compensating)", event.userId());
                        }
                    );
                }
            } catch (Exception e) {
                log.error("Failed to sync UserUpdatedEvent for userId {}", event.userId(), e);
                throw new RuntimeException("Event sync failed, will retry", e);
            }
            
            // 清理相关缓存
            if (cacheManager != null) {
                cacheManager.getCache("users").evict(event.userId().value());
            }
        };
    }

    @Bean
    public Consumer<UserStatusChangedEvent> userStatusChanged() {
        return event -> {
            log.info("Received UserStatusChangedEvent: {}", event);
            try {
                // 更新本地 auth_user 的 account_status
                int newStatus = event.newStatus().equals("0") ? 0 : 1;
                userRepository.updateStatus(event.userId(), newStatus);
                log.info("Updated user {} status to {} in auth_user", event.userId(), newStatus);
            } catch (Exception e) {
                log.error("Failed to sync UserStatusChangedEvent for userId {}", event.userId(), e);
                throw new RuntimeException("Event sync failed, will retry", e);
            }
            
            // 清理相关缓存
            if (cacheManager != null) {
                cacheManager.getCache("users").evict(event.userId().value());
            }
            // 如果用户被禁用，可以在这里实现强制下线逻辑
            if (event.newStatus().equals("0")) {
                log.warn("User {} has been disabled, need to force logout", event.userId());
                // TODO: 实现强制下线逻辑
            }
        };
    }
    
    /**
     * 规则：已加密识别（以 { 或 $2a$/$2b$/$2y$ 开头） → 原样保存；否则 encode
     */
    private String normalizePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        if (password.startsWith("{") || password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
            return password;
        }
        return passwordEncoder.encode(password);
    }
    
    private MallUserStatus intToStatus(Integer status) {
        return (status != null && status == 1) ? MallUserStatus.ACTIVE : MallUserStatus.FROZEN;
    }
}