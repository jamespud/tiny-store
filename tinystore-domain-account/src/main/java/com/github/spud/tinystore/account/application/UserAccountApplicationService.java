package com.github.spud.tinystore.account.application;

import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import com.github.spud.tinystore.account.infrastructure.persistence.repository.UserCoreRepository;
import com.github.spud.tinystore.account.infrastructure.id.UserIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.github.spud.tinystore.account.infrastructure.event.AccountEventPublisher;
import com.github.spud.tinystore.account.infrastructure.security.CredentialVersionStore;
import com.github.spud.tinystore.contracts.account.events.UserCreatedEvent;
import com.github.spud.tinystore.contracts.account.events.UserCredentialChangedEvent;
import com.github.spud.tinystore.contracts.account.events.UserStatusChangedEvent;
import com.github.spud.tinystore.contracts.account.events.UserUpdatedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountApplicationService {

    private final UserCoreRepository userCoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEventPublisher eventPublisher;
    private final CredentialVersionStore credentialVersionStore;
    private final UserIdGenerator userIdGenerator;

    @Cacheable(value = "users", keyGenerator = "userKeyGenerator")
    public Optional<UserCore> getUserById(String userId) {
        return userCoreRepository.findById(userId);
    }

    // 缓存配置禁用了 null 值（disableCachingNullValues），而"按手机号查不到用户"是完全正常的
    // 业务结果（新用户首次 OTP 登录）。方法返回的是 Optional，所以必须按 isEmpty() 判断——
    // 否则空结果会被当成 null 写入缓存并抛
    // "Cache 'usersByPhone' does not allow 'null' values"，把首次登录直接打挂。
    @Cacheable(value = "usersByPhone", keyGenerator = "userKeyGenerator",
        unless = "#result == null || #result.isEmpty()")
    public Optional<UserCore> getUserByPhone(String phone) {
        return nullSafe(userCoreRepository.findByAccount(phone));
    }

    @Cacheable(value = "usersByUsername", keyGenerator = "userKeyGenerator",
        unless = "#result == null || #result.isEmpty()")
    public Optional<UserCore> getUserByUsername(String username) {
        return nullSafe(userCoreRepository.findByAccount(username));
    }

    /**
     * 查询未命中时可能出现 null（缓存 null 策略 + Optional 返回值的组合），
     * 统一归一为 Optional.empty()：
     * - 避免 "Cache does not allow 'null' values" / SpEL 在 null 上取 isEmpty() 而 500；
     * - 保证调用方永远拿到非 null 的 Optional（首次 OTP 登录必须能走通）。
     */
    private static Optional<UserCore> nullSafe(Optional<UserCore> found) {
        return found == null ? Optional.empty() : found;
    }

    @Transactional
    @CacheEvict(value = {"usersByPhone", "usersByUsername"}, keyGenerator = "userKeyGenerator")
    public UserCore registerUser(String phone, String password, String nickname) {
        if (userCoreRepository.findByAccount(phone).isPresent()) {
            throw new IllegalArgumentException("用户已存在");
        }

        UserCore userCore = new UserCore();
        userCore.setUserId(userIdGenerator.generate());
        userCore.setAccount(phone);
        userCore.setPassword(passwordEncoder.encode(password));
        userCore.setNickname(nickname);
        userCore.setAccountStatus(1); // 1-正常
        userCore.setIsDelete(0); // 0-未删
        userCore.setRegisterTime(LocalDateTime.now());
        userCore.setCredentialVersion(1L);

        UserCore savedUser = userCoreRepository.save(userCore);

        credentialVersionStore.save(savedUser.getUserId(), savedUser.getCredentialVersion());
        
        // 发布用户创建事件
        eventPublisher.publishUserCreatedEvent(
                new UserCreatedEvent(
                        savedUser.getUserId(),
                        savedUser.getAccount(),
                        savedUser.getNickname(),
                        savedUser.getRegisterTime() != null
                                ? savedUser.getRegisterTime().atOffset(ZoneOffset.UTC)
                                : OffsetDateTime.now(ZoneOffset.UTC)
                )
        );
        
        return savedUser;
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, keyGenerator = "userKeyGenerator")
    public UserCore updateUserProfile(String userId, String nickname, String avatarUrl, String extJson) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (nickname != null) {
            userCore.setNickname(nickname);
        }
        if (avatarUrl != null) {
            userCore.setAvatarUrl(avatarUrl);
        }
        if (extJson != null) {
            userCore.setExtJson(extJson);
        }

        UserCore savedUser = userCoreRepository.save(userCore);
        
        // 发布用户更新事件
        eventPublisher.publishUserUpdatedEvent(
                new UserUpdatedEvent(
                        savedUser.getUserId(),
                        savedUser.getNickname(),
                        savedUser.getAvatarUrl(),
                        savedUser.getExtJson(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );
        
        return savedUser;
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, keyGenerator = "userKeyGenerator")
    public void resetPassword(String userId, String newPassword) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        userCore.setPassword(passwordEncoder.encode(newPassword));
        userCore.setCredentialVersion(
                userCore.getCredentialVersion() == null ? 1L : userCore.getCredentialVersion() + 1L);
        userCoreRepository.save(userCore);

        credentialVersionStore.save(userCore.getUserId(), userCore.getCredentialVersion());

        eventPublisher.publishUserCredentialChangedEvent(
                new UserCredentialChangedEvent(
                        userCore.getUserId(),
                        userCore.getCredentialVersion(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, keyGenerator = "userKeyGenerator")
    public void updateUserStatus(String userId, Integer accountStatus) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Integer oldStatus = userCore.getAccountStatus();
        userCore.setAccountStatus(accountStatus);
        userCore.setCredentialVersion(
                userCore.getCredentialVersion() == null ? 1L : userCore.getCredentialVersion() + 1L);
        userCoreRepository.save(userCore);

        credentialVersionStore.save(userCore.getUserId(), userCore.getCredentialVersion());
        
        // 发布用户状态变更事件
        eventPublisher.publishUserStatusChangedEvent(
                new UserStatusChangedEvent(
                        userId,
                        oldStatus,
                        accountStatus,
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );

        eventPublisher.publishUserCredentialChangedEvent(
                new UserCredentialChangedEvent(
                        userId,
                        userCore.getCredentialVersion(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, keyGenerator = "userKeyGenerator")
    public void deleteUser(String userId) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        userCore.setIsDelete(1); // 1-已删
        userCoreRepository.save(userCore);
    }
}
