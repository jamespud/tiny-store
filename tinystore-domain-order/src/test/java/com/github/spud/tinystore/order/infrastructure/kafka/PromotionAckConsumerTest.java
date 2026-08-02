package com.github.spud.tinystore.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionAckConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TradeApplicationService tradeApplicationService = mock(TradeApplicationService.class);
    private final JpaConsumerEventLogRepository repository = mock(JpaConsumerEventLogRepository.class);
    private final PromotionAckConsumer consumer =
            new PromotionAckConsumer(mapper, tradeApplicationService, repository);

    @Test
    void shouldApplyCommittedOnPromotionCommittedEvent() throws Exception {
        when(repository.existsByEventIdAndConsumerName(anyString(), anyString())).thenReturn(false);
        String msg = mapper.writeValueAsString(java.util.Map.of(
                "eventId", "evt-1", "eventType", "PROMOTION_COMMITTED",
                "tradeId", "trade-1", "quoteId", "quote-1", "traceId", "trace-1"));

        consumer.handlePromotionAck(msg);

        verify(tradeApplicationService).applyPromotionCommitResult("trade-1", true, null);
        verify(repository).save(any());
    }

    @Test
    void shouldApplyFailedOnPromotionCommitFailedEvent() throws Exception {
        when(repository.existsByEventIdAndConsumerName(anyString(), anyString())).thenReturn(false);
        String msg = mapper.writeValueAsString(java.util.Map.of(
                "eventId", "evt-2", "eventType", "PROMOTION_COMMIT_FAILED",
                "tradeId", "trade-2", "quoteId", "quote-2", "reason", "COUPON_UNAVAILABLE"));

        consumer.handlePromotionAck(msg);

        verify(tradeApplicationService).applyPromotionCommitResult("trade-2", false, "COUPON_UNAVAILABLE");
    }

    @Test
    void shouldSkipAlreadyProcessedEvent() throws Exception {
        when(repository.existsByEventIdAndConsumerName(anyString(), anyString())).thenReturn(true);
        String msg = mapper.writeValueAsString(java.util.Map.of(
                "eventId", "evt-1", "eventType", "PROMOTION_COMMITTED", "tradeId", "trade-1"));

        consumer.handlePromotionAck(msg);

        verifyNoInteractions(tradeApplicationService);
    }
}
