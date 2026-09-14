package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPrice;
import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPriceResponse;
import com.github.spud.tinystore.infrastructure.rpc.payment.ProductClient;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand.OrderLineCommand;
import com.github.spud.tinystore.order.application.price.SkuPriceResolver;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A3 接线验证：证明 createTrade 在 authoritative-pricing 开启时会调用 SkuPriceResolver，
 * 且篡改的客户端价格会以 INVALID_PRICE 被拒。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeApplicationService — authoritative pricing wiring (A3)")
class TradeApplicationServiceAuthoritativePriceTest {

	@Mock
	private ProductClient productClient;

	@Mock
	private IdempotencyService idempotencyService;

	private TradeApplicationService service;

	@BeforeEach
	void setUp() {
		service = new TradeApplicationService();
		ReflectionTestUtils.setField(service, "idempotencyService", idempotencyService);

        // P0-2: createTrade consults the durable idempotency record first (empty here -> proceed).
        com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaTradeIdempotencyRecordRepository recordRepository =
                mock(com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaTradeIdempotencyRecordRepository.class);
		org.mockito.Mockito.lenient().when(recordRepository.findById(anyString())).thenReturn(java.util.Optional.empty());
		org.mockito.Mockito.lenient().when(recordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		// 第三轮 P0：durable claim 在 saga 之前取得（真实仓储会 INSERT 并返回 1）。
		org.mockito.Mockito.lenient()
			.when(recordRepository.claimProcessing(anyString(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class)))
			.thenReturn(1);
		ReflectionTestUtils.setField(service, "tradeIdempotencyRecordRepository", recordRepository);

		lenient().when(idempotencyService.acquire(anyString(), anyString(), anyString()))
			.thenReturn(IdempotencyService.AcquireResult.ACQUIRED);
		ReflectionTestUtils.setField(service, "skuPriceResolver", new SkuPriceResolver(productClient));
		ReflectionTestUtils.setField(service, "priceAuthoritativeEnabled", true);
	}

	@Test
	@DisplayName("tampered client price should be rejected by createTrade")
	void tamperedClientPrice_createTrade_shouldReject() {
		ProductSkuPriceResponse resp = new ProductSkuPriceResponse();
		ProductSkuPrice price = new ProductSkuPrice();
		price.setSkuId("SKU_A");
		price.setAvailable(true);
		price.setUnitPrice(1000L);
		resp.setSkuMap(Map.of("SKU_A", price));
		when(productClient.batchSkuPrices("SHOP_A", "SKU_A")).thenReturn(resp);

		CreateTradeCommand command = CreateTradeCommand.builder()
			.tradeId("trade-pricing")
			.buyerId("user-1")
			.orderLines(List.of(OrderLineCommand.builder()
				.skuId("SKU_A").shopId("SHOP_A").sellerId("seller-A")
				.quantity(1).priceCents(1L).build()))
			.build();

		assertThatThrownBy(() -> service.createTrade("idem-pricing", command))
			.isInstanceOf(DomainConflictException.class)
			.extracting("errorCode")
			.isEqualTo("INVALID_PRICE");
	}
}
