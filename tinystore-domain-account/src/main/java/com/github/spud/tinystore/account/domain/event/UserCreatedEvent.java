package com.github.spud.tinystore.account.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@ToString
public class UserCreatedEvent {
    private final Long userId;
    private final String phone;
    private final String nickname;
    private final LocalDateTime createTime;
}