package com.github.spud.tinystore.auth.domain.service;

import com.github.spud.tinystore.auth.domain.exception.AccountFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

  private final AccountServiceFeignClient accountServiceFeignClient;

  public MallUser getOrCreateByPhone(String phone) {
    return accountServiceFeignClient.getUserByPhone(phone)
        .map(this::convertToMallUser)
        .map(this::ensureNotFrozen)
        .orElseGet(() -> {
          log.warn("User not found for phone: {}, creating new user in auth domain", phone);
          return MallUser.register(UserId.random(), PhoneNumber.of(phone),
              "用户" + phone.substring(Math.max(phone.length() - 4, 0)), "default");
        });
  }

  public Optional<MallUser> findById(String id) {
    try {
      Long userId = Long.parseLong(id);
      return accountServiceFeignClient.getUserById(userId)
          .map(this::convertToMallUser);
    } catch (NumberFormatException e) {
      log.error("Invalid user ID format: {}", id, e);
      return Optional.empty();
    }
  }

  public long incrementRtVersion(String id) {
    // TODO: 后续需要在账号域实现refresh token版本管理
    return System.currentTimeMillis() / 1000;
  }

  private MallUser ensureNotFrozen(MallUser user) {
    if (user.isFrozen()) {
      throw new AccountFrozenException("mall user is frozen");
    }
    return user;
  }

  private MallUser convertToMallUser(AccountServiceFeignClient.UserCoreDto userCoreDto) {
    return MallUser.builder()
        .id(UserId.of(userCoreDto.userId().toString()))
        .phone(PhoneNumber.of(userCoreDto.account()))
        .username(userCoreDto.account())
        .nickname(userCoreDto.nickname())
        .avatar(userCoreDto.avatarUrl())
        .enabled(userCoreDto.accountStatus() == 1)
        .build();
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return accountServiceFeignClient.getUserByUsername(username)
        .map(userCoreDto -> org.springframework.security.core.userdetails.User
            .withUsername(userCoreDto.account())
            .password("N/A") // 密码不在认证域存储
            .authorities("ROLE_USER")
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(userCoreDto.accountStatus() != 1)
            .build())
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
  }

}
