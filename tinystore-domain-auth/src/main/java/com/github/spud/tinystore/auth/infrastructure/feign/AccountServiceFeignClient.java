package com.github.spud.tinystore.auth.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "tinystore-domain-account")
public interface AccountServiceFeignClient {

    @GetMapping("/api/account/users/{userId}")
    UserCoreDto getUserById(@PathVariable("userId") Long userId);

    @GetMapping("/api/account/users/phone/{phone}")
    UserCoreDto getUserByPhone(@PathVariable("phone") String phone);

    @GetMapping("/api/account/users/username/{username}")
    UserCoreDto getUserByUsername(@PathVariable("username") String username);

    // DTO类
    record UserCoreDto(
            Long userId,
            String account,
            String password,
            String nickname,
            String avatarUrl,
            Integer accountStatus,
            String extJson
    ) {}
}