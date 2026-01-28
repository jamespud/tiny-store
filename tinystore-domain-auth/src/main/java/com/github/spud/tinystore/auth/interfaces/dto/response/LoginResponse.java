package com.github.spud.tinystore.auth.interfaces.dto.response;

public record LoginResponse(
  String userId,      // 用户ID（成功时返回）
  String token) {

}
