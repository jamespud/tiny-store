package com.github.spud.tinystore.account.application;

import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import com.github.spud.tinystore.account.infrastructure.persistence.repository.UserCoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.github.spud.tinystore.account.infrastructure.event.AccountEventPublisher;
import com.github.spud.tinystore.account.domain.event.UserCreatedEvent;
import com.github.spud.tinystore.account.domain.event.UserUpdatedEvent;
import com.github.spud.tinystore.account.domain.event.UserStatusChangedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountApplicationService {

    private final UserCoreRepository userCoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEventPublisher eventPublisher;

    @Cacheable(value = "users", key = "#userId", keyGenerator = "userKeyGenerator")
    public Optional<UserCore> getUserById(Long userId) {
        return userCoreRepository.findById(userId);
    }

    @Cacheable(value = "usersByPhone", key = "#phone", keyGenerator = "userKeyGenerator")
    public Optional<UserCore> getUserByPhone(String phone) {
        return userCoreRepository.findByAccount(phone);
    }

    @Cacheable(value = "usersByUsername", key = "#username", keyGenerator = "userKeyGenerator")
    public Optional<UserCore> getUserByUsername(String username) {
        return userCoreRepository.findByAccount(username);
    }

    @Transactional
    @CacheEvict(value = {"usersByPhone", "usersByUsername"}, key = "#phone")
    public UserCore registerUser(String phone, String password, String nickname) {
        if (userCoreRepository.findByAccount(phone).isPresent()) {
            throw new IllegalArgumentException("用户已存在");
        }

        UserCore userCore = new UserCore();
        userCore.setAccount(phone);
        userCore.setPassword(passwordEncoder.encode(password));
        userCore.setNickname(nickname);
        userCore.setAccountStatus(1); // 1-正常
        userCore.setIsDelete(0); // 0-未删
        userCore.setRegisterTime(LocalDateTime.now());

        UserCore savedUser = userCoreRepository.save(userCore);
        
        // 发布用户创建事件
        eventPublisher.publishUserCreatedEvent(
                new UserCreatedEvent(
                        savedUser.getUserId(),
                        savedUser.getAccount(),
                        savedUser.getNickname(),
                        savedUser.getRegisterTime()
                )
        );
        
        return savedUser;
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, key = "#userId")
    public UserCore updateUserProfile(Long userId, String nickname, String avatarUrl, String extJson) {
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
                        LocalDateTime.now()
                )
        );
        
        return savedUser;
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, key = "#userId")
    public void resetPassword(Long userId, String newPassword) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        userCore.setPassword(passwordEncoder.encode(newPassword));
        userCoreRepository.save(userCore);
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, key = "#userId")
    public void updateUserStatus(Long userId, Integer accountStatus) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Integer oldStatus = userCore.getAccountStatus();
        userCore.setAccountStatus(accountStatus);
        userCoreRepository.save(userCore);
        
        // 发布用户状态变更事件
        eventPublisher.publishUserStatusChangedEvent(
                new UserStatusChangedEvent(
                        userId,
                        oldStatus,
                        accountStatus,
                        LocalDateTime.now()
                )
        );
    }

    @Transactional
    @CacheEvict(value = {"users", "usersByPhone", "usersByUsername"}, key = "#userId")
    public void deleteUser(Long userId) {
        UserCore userCore = userCoreRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        userCore.setIsDelete(1); // 1-已删
        userCoreRepository.save(userCore);
    }
}
