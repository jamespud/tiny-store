package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
		ReflectionTestUtils.setField(service, "skuPriceResolver", new SkuPriceResolver(productClient));
		ReflectionTestUtils.setField(service, "priceAuthoritativeEnabled", true);
	}

	@Test
	@DisplayName("tampered client price should be rejected by createTrade")
	void tamperedClientPrice_createTrade_shouldReject() {
		when(idempotencyService.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
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
