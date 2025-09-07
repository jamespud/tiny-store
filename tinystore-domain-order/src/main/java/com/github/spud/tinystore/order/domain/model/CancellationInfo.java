package com.github.spud.tinystore.order.domain.model;

import java.time.LocalDateTime;

/**
 * @author Spud
 * @date 2025/9/3
 */
public record CancellationInfo(String reason, LocalDateTime at, Buyer actorType, String actorId) {

}
