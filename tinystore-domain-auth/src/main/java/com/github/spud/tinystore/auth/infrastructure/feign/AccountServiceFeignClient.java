package com.github.spud.tinystore.auth.infrastructure.feign;

import com.github.spud.tinystore.auth.infrastructure.feign.fallback.AccountServiceFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "tinystore-domain-account", fallbackFactory = AccountServiceFallbackFactory.class)
public interface AccountServiceFeignClient {

    @GetMapping("/api/account/users/{userId}")
    ResponseEntity<UserCoreDto> getUserById(@PathVariable("userId") Long userId);

    @GetMapping("/api/account/users/phone/{phone}")
    ResponseEntity<UserCoreDto> getUserByPhone(@PathVariable("phone") String phone);

    @GetMapping("/api/account/users/username/{username}")
    ResponseEntity<UserCoreDto> getUserByUsername(@PathVariable("username") String username);

    // DTO类
    record UserCoreDto(
            Long userId,
            String account,
            String nickname,
            String avatarUrl,
            Integer accountStatus,
            String extJson
    ) {}
}