package com.github.spud.tinystore.account.interfaces.dto.internal;

public record CredentialVerifyResponse(String userId, String phone, String nickname, String avatarUrl,
                                       Integer accountStatus, Long credentialVersion) {}

