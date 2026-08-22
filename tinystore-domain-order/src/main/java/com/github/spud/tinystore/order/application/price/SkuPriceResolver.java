package com.github.spud.tinystore.order.application.price;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand.OrderLineCommand;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPrice;
import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPriceResponse;
import com.github.spud.tinystore.infrastructure.rpc.payment.ProductClient;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * **服务端权威定价解析器（A3）。**
 *
 * <p>防止“客户端自定价格”这一 demo 级缺陷：真实平台的价格必须来自商品目录，绝不能信任请求体。
 * 下单前据此向商品域拉取每个 (shopId, skuId) 的权威单价；若客户端传入价与权威单价不一致，或 SKU
 * 不可售/不存在，则拒绝。最小改动、可独立单测。
 */
@Component
@RequiredArgsConstructor
public class SkuPriceResolver {

	private final ProductClient productClient;

	/**
	 * 校验并校准订单行价格：以 (shopId, skuId) 为键返回权威单价（分）。
	 * 若某 SKU 找不到、不可售，或客户端价与权威单价不一致，抛出 {@link DomainConflictException}。
	 *
	 * @param lines 订单行（会被原地校准 priceCents 为权威单价）
	 * @return (shopId:skuId) -> 权威单价（分）
	 */
	public Map<String, Long> validateAndAttach(List<OrderLineCommand> lines) {
		Map<String, Set<String>> skuIdsByShop = organizeByShop(lines);
		Map<String, Long> authoritative = new LinkedHashMap<>();

		for (Map.Entry<String, Set<String>> entry : skuIdsByShop.entrySet()) {
			String shopId = entry.getKey();
			ProductSkuPriceResponse resp = productClient.batchSkuPrices(
				shopId, String.join(",", entry.getValue()));
			Map<String, ProductSkuPrice> priceMap =
				resp != null && resp.getSkuMap() != null ? resp.getSkuMap() : Map.of();
			for (String skuId : entry.getValue()) {
				ProductSkuPrice price = priceMap.get(skuId);
				String key = compositeKey(shopId, skuId);
				authoritative.put(key, price != null ? price.getUnitPrice() : null);
			}
		}

		for (OrderLineCommand line : lines) {
			String key = compositeKey(line.getShopId(), line.getSkuId());
			Long expected = authoritative.get(key);
			if (expected == null) {
				throw new DomainConflictException("SKU_PRICE_NOT_FOUND",
					"No authoritative price for skuId=" + line.getSkuId() + ", shopId=" + line.getShopId());
			}
			if (line.getPriceCents() == null || line.getPriceCents().longValue() != expected.longValue()) {
				throw new DomainConflictException("INVALID_PRICE",
					"Client price mismatch for skuId=" + line.getSkuId() + ": expected " + expected
						+ ", got " + line.getPriceCents());
			}
			// 防御性校准：以服务端权威价为准
			line.setPriceCents(expected);
		}
		return authoritative;
	}

	private Map<String, Set<String>> organizeByShop(List<OrderLineCommand> lines) {
		return lines.stream().collect(Collectors.groupingBy(
			OrderLineCommand::getShopId,
			LinkedHashMap::new,
			Collectors.mapping(OrderLineCommand::getSkuId, Collectors.toCollection(LinkedHashSet::new))));
	}

	private String compositeKey(String shopId, String skuId) {
		return shopId + ":" + skuId;
	}
}
