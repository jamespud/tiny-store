package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;

/**
 * P0: the orphan sweep's correctness rests on "orphan-check-delay > max reservation TTL" (with the consumer
 * refusing expired events). That relationship is enforced at startup instead of being a comment, because a
 * silent violation releases legitimate pre-deductions -- the oversell direction.
 */
@DisplayName("inventory uncommit cleanup — configuration invariants")
class InventoryUncommitCleanupConfigTest {

    @Test
    @DisplayName("orphan-check-delay must exceed the max reservation TTL")
    void orphanDelayMustExceedTtl() {
        assertThatThrownBy(() -> task(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(30))
            .validateConfigInvariants())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be greater than")
            .hasMessageContaining("oversell");
    }

    @Test
    @DisplayName("orphan-check-delay must stay below the fallback timeout")
    void orphanDelayMustBeBelowTimeout() {
        assertThatThrownBy(() -> task(Duration.ofMinutes(40), Duration.ofMinutes(15), Duration.ofMinutes(30))
            .validateConfigInvariants())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be greater than inventory.uncommit.orphan-check-delay");
    }

    @Test
    @DisplayName("the shipped defaults satisfy both invariants")
    void defaultsAreValid() {
        assertThatCode(() -> task(Duration.ofMinutes(20), Duration.ofMinutes(15), Duration.ofMinutes(30))
            .validateConfigInvariants()).doesNotThrowAnyException();
    }

    private static InventoryUncommitV2CleanupTask task(Duration orphanDelay, Duration ttl, Duration timeout) {
        InventoryUncommitV2CleanupTask task = new InventoryUncommitV2CleanupTask(
            org.mockito.Mockito.mock(StringRedisTemplate.class),
            org.mockito.Mockito.mock(InventoryRedisManager.class),
            org.mockito.Mockito.mock(JpaInventoryReservationRepository.class));
        ReflectionTestUtils.setField(task, "orphanCheckDelay", orphanDelay);
        ReflectionTestUtils.setField(task, "maxReservationTtl", ttl);
        ReflectionTestUtils.setField(task, "uncommitTimeout", timeout);
        return task;
    }
}
