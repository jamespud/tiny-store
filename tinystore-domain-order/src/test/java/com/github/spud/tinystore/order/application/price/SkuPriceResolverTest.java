package com.github.spud.tinystore.order.application.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand.OrderLineCommand;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPrice;
import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPriceResponse;
import com.github.spud.tinystore.infrastructure.rpc.payment.ProductClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A3 服务端权威定价：防止客户端自定价格。验证价格匹配 / 缺失 / 不匹配三类行为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkuPriceResolver — Server-side authoritative pricing (A3)")
class SkuPriceResolverTest {

	@Mock
	private ProductClient productClient;

	private SkuPriceResolver resolver;

	private OrderLineCommand line(String shop, String sku, long price) {
		return OrderLineCommand.builder()
			.shopId(shop)
			.skuId(sku)
			.quantity(1)
			.priceCents(price)
			.build();
	}

	private ProductSkuPrice price(String sku, long unitPrice) {
		ProductSkuPrice p = new ProductSkuPrice();
		p.setSkuId(sku);
		p.setAvailable(true);
		p.setUnitPrice(unitPrice);
		return p;
	}

	@Test
	@DisplayName("matching client price should accept and keep authoritative price")
	void matchingClientPrice_shouldAccept() {
		resolver = new SkuPriceResolver(productClient);
		OrderLineCommand line = line("SHOP_A", "SKU_A", 1000L);
		ProductSkuPriceResponse resp = new ProductSkuPriceResponse();
		resp.setSkuMap(Map.of("SKU_A", price("SKU_A", 1000L)));
		when(productClient.batchSkuPrices("SHOP_A", "SKU_A")).thenReturn(resp);

		Map<String, Long> result = resolver.validateAndAttach(List.of(line));

		assertThat(result).containsEntry("SHOP_A:SKU_A", 1000L);
		assertThat(line.getPriceCents()).isEqualTo(1000L);
		verify(productClient).batchSkuPrices("SHOP_A", "SKU_A");
	}

	@Test
	@DisplayName("tampered client price should be rejected as INVALID_PRICE")
	void tamperedClientPrice_shouldReject() {
		resolver = new SkuPriceResolver(productClient);
		OrderLineCommand line = line("SHOP_A", "SKU_A", 1L);
		ProductSkuPriceResponse resp = new ProductSkuPriceResponse();
		resp.setSkuMap(Map.of("SKU_A", price("SKU_A", 1000L)));
		when(productClient.batchSkuPrices("SHOP_A", "SKU_A")).thenReturn(resp);

		assertThatThrownBy(() -> resolver.validateAndAttach(List.of(line)))
			.isInstanceOf(DomainConflictException.class)
			.extracting("errorCode")
			.isEqualTo("INVALID_PRICE");
	}

	@Test
	@DisplayName("unknown SKU should be rejected as SKU_PRICE_NOT_FOUND")
	void unknownSku_shouldReject() {
		resolver = new SkuPriceResolver(productClient);
		OrderLineCommand line = line("SHOP_A", "SKU_X", 1000L);
		when(productClient.batchSkuPrices("SHOP_A", "SKU_X")).thenReturn(new ProductSkuPriceResponse());

		assertThatThrownBy(() -> resolver.validateAndAttach(List.of(line)))
			.isInstanceOf(DomainConflictException.class)
			.extracting("errorCode")
			.isEqualTo("SKU_PRICE_NOT_FOUND");
	}
}
