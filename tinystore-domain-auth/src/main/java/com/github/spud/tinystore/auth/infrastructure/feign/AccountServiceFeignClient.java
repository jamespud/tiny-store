package com.github.spud.tinystore.auth.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "tinystore-domain-account")
public interface AccountServiceFeignClient {

  @GetMapping("/api/account/users/{userId}")
  UserCoreDto getUserById(@PathVariable("userId") Long userId);

  @GetMapping("/api/account/users/phone/{phone}")
  UserCoreDto getUserByPhone(@PathVariable("phone") String phone);

  @GetMapping("/api/account/users/username/{username}")
  UserCoreDto getUserByUsername(@PathVariable("username") String username);

  @PostMapping("/internal/account/credentials/verify")
  CredentialVerifyResponse verifyCredentials(
    @RequestHeader("X-Tinystore-Internal-Token") String internalToken,
    @RequestBody CredentialVerifyRequest request);

  // DTO类
  record UserCoreDto(
    Long userId,
    String account,
    String nickname,
    String avatarUrl,
    Integer accountStatus,
    String extJson,
    Long credentialVersion
  ) {

  }

  record CredentialVerifyRequest(String phone, String password) {

  }

  record CredentialVerifyResponse(
    Long userId,
    String phone,
    String nickname,
    String avatarUrl,
    Integer accountStatus,
    Long credentialVersion
  ) {

  }
}
