package com.github.spud.tinystore.order.infrastructure.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;

/**
 * OutboxEventService 批量标记测试：
 * markAsPublishedBatch 应一次调用 repository 批量更新全部 eventId（替代逐条 SELECT+UPDATE）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;

    private OutboxEventService service;

    @BeforeEach
    void setUp() {
        service = new OutboxEventService();
        ReflectionTestUtils.setField(service, "outboxEventJpaRepository", outboxEventJpaRepository);
    }

    @Test
    void markAsPublishedBatch_shouldDelegateBatchUpdate() {
        // Given
        when(outboxEventJpaRepository.markAsPublishedBatch(eq(List.of("e1", "e2", "e3")), any(LocalDateTime.class)))
            .thenReturn(3);

        // When
        service.markAsPublishedBatch(List.of("e1", "e2", "e3"));

        // Then: 一次批量更新（3 条）
        verify(outboxEventJpaRepository).markAsPublishedBatch(eq(List.of("e1", "e2", "e3")), any(LocalDateTime.class));
    }

    @Test
    void markAsPublishedBatch_withEmptyList_shouldDoNothing() {
        // When
        service.markAsPublishedBatch(List.of());

        // Then: 不调用 repository
        verify(outboxEventJpaRepository, never()).markAsPublishedBatch(any(), any());
    }
}
