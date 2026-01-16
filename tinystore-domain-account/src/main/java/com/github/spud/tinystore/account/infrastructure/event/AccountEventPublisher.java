package com.github.spud.tinystore.account.infrastructure.event;

import com.github.spud.tinystore.account.domain.event.UserCreatedEvent;
import com.github.spud.tinystore.account.domain.event.UserStatusChangedEvent;
import com.github.spud.tinystore.account.domain.event.UserUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private final StreamBridge streamBridge;

    public void publishUserCreatedEvent(UserCreatedEvent event) {
        try {
            streamBridge.send("userCreated-out-0", event);
            log.info("Published UserCreatedEvent: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish UserCreatedEvent: {}", event, e);
        }
    }

    public void publishUserUpdatedEvent(UserUpdatedEvent event) {
        try {
            streamBridge.send("userUpdated-out-0", event);
            log.info("Published UserUpdatedEvent: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish UserUpdatedEvent: {}", event, e);
        }
    }

    public void publishUserStatusChangedEvent(UserStatusChangedEvent event) {
        try {
            streamBridge.send("userStatusChanged-out-0", event);
            log.info("Published UserStatusChangedEvent: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish UserStatusChangedEvent: {}", event, e);
        }
    }
}