package com.github.spud.tinystore.order.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderEventTypeTest {

    @Test
    void shouldDefinePromotionCommitEventTypes() {
        assertThat(OrderEventType.valueOf("PROMOTION_COMMIT").getCode()).isEqualTo("PROMOTION_COMMIT");
        assertThat(OrderEventType.valueOf("PROMOTION_COMMITTED").getCode()).isEqualTo("PROMOTION_COMMITTED");
        assertThat(OrderEventType.valueOf("PROMOTION_COMMIT_FAILED").getCode()).isEqualTo("PROMOTION_COMMIT_FAILED");
    }
}
