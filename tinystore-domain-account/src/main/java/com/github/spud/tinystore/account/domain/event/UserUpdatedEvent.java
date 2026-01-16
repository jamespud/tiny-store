package com.github.spud.tinystore.account.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@ToString
public class UserUpdatedEvent {
    private final Long userId;
    private final String nickname;
    private final String avatarUrl;
    private final String extJson;
    private final LocalDateTime updateTime;
}