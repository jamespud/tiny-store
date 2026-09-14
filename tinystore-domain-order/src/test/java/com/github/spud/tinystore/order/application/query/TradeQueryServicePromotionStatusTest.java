package com.github.spud.tinystore.order.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.AfterSaleCaseRepository;
import com.github.spud.tinystore.order.domain.repository.FulfillmentPackageRepository;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.TradeDetailData;

/**
 * C13: the trade detail response is where a client observes the asynchronous promotion verdict, so it
 * must carry the state instead of leaving "order created" to be read as "discount confirmed".
 */
@DisplayName("TradeQueryService — promotion commit status projection")
class TradeQueryServicePromotionStatusTest {

    @Test
    @DisplayName("the detail response exposes PENDING while the coupon is still being arbitrated")
    void exposesPendingStatus() {
        assertThat(detailFor(trade("PENDING")).getPromotionCommitStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("and the final verdict once it lands")
    void exposesFinalStatus() {
        assertThat(detailFor(trade("COMMITTED")).getPromotionCommitStatus()).isEqualTo("COMMITTED");
        assertThat(detailFor(trade("FAILED")).getPromotionCommitStatus()).isEqualTo("FAILED");
    }

    private static TradeDetailData detailFor(Trade trade) {
        TradeQueryService service = new TradeQueryService();
        TradeRepository tradeRepository = mock(TradeRepository.class);
        when(tradeRepository.findByTradeId(anyString())).thenReturn(Optional.of(trade));
        ReflectionTestUtils.setField(service, "tradeRepository", tradeRepository);

        ShopOrderRepository shopOrderRepository = mock(ShopOrderRepository.class);
        when(shopOrderRepository.findByTradeId(anyString())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "shopOrderRepository", shopOrderRepository);
        ReflectionTestUtils.setField(service, "fulfillmentPackageRepository",
                mock(FulfillmentPackageRepository.class));
        ReflectionTestUtils.setField(service, "afterSaleCaseRepository", mock(AfterSaleCaseRepository.class));

        return service.getTradeDetail("trade-1");
    }

    private static Trade trade(String promotionCommitStatus) {
        return Trade.builder()
                .tradeId("trade-1")
                .buyerId("buyer-1")
                .buyerNick("nick")
                .payStatus(PayStatus.UNPAID)
                .totalAmountCents(1000L)
                .discountAmountCents(0L)
                .payableAmountCents(1000L)
                .promotionCommitStatus(promotionCommitStatus)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
