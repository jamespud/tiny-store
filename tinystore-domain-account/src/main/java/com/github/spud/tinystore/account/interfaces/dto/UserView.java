package com.github.spud.tinystore.account.interfaces.dto;

import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;

public record UserView(String userId, String account, String nickname, String avatarUrl,
                       Integer accountStatus, Integer isDelete, String extJson, Long credentialVersion) {

    public static UserView from(UserCore user) {
        if (user == null) {
            return null;
        }
        return new UserView(
                user.getUserId(),
                user.getAccount(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getAccountStatus(),
                user.getIsDelete(),
                user.getExtJson(),
                user.getCredentialVersion()
        );
    }
}

