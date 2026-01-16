package com.github.spud.tinystore.account.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@ToString
public class UserStatusChangedEvent {
    private final Long userId;
    private final Integer oldStatus;
    private final Integer newStatus;
    private final LocalDateTime changeTime;
}