package com.github.spud.tinystore.product.application;

import com.github.spud.tinystore.product.domain.error.BusinessException;
import com.github.spud.tinystore.product.domain.error.InventoryErrorCode;
import com.github.spud.tinystore.product.interfaces.dto.AvailableQuery;
import com.github.spud.tinystore.product.interfaces.dto.AvailableResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Inventory 应用服务
 * Refactored 2025-09-14: 引入新 API (reserve/confirm/release/adjustTotal/queryAvailable/batchQueryAvailable/sync)
 * Deprecated 旧方法：deductReserve / increaseReserve / increaseTotal / decreaseTotal / batchQueryAvailable(List<String>)
 */
@Service
public class InventoryApplication {

	// ================= 新方法（占位） =================

	public String reserve(String shopId, String skuId, Integer quantity, Integer expireSeconds) {
		// TODO(inv): 参数校验 + 调用领域服务 + 缓存预留 + 生成 reservationId
		return null;
	}

	public boolean confirm(String reservationId, String correlationId) {
		// TODO(inv): 状态检查 + 扣减总库存/预留库存 + 事件占位
		return false;
	}

	public boolean release(String reservationId, String reason) {
		// TODO(inv): 状态检查 + 回滚预留
		return false;
	}

	public boolean adjustTotal(String shopId, String skuId, Integer deltaTotal, String reason) {
		// TODO(inv): 校验 available 不为负 + 更新缓存 total + 事件占位
		return false;
	}

	public Integer queryAvailable(String shopId, String skuId) {
		// TODO(inv): 从缓存 snapshot 计算 available；缓存缺失时返回 0 或触发异步同步
		return 0;
	}

	public List<AvailableResponse> batchQueryAvailable(List<AvailableQuery> queries) {
		// TODO(inv): 循环 getSnapshot / 后续批量 Lua 优化
		return Collections.emptyList();
	}

	public void sync(String shopId, String skuId) {
		// TODO(inv): DB 拉取 -> 刷新 Redis（仅占位）
	}

	// ================= 旧方法（@Deprecated forRemoval） =================

	/**
	 * @deprecated 使用 {@link #reserve(String, String, Integer, Integer)}
	 */
	@Deprecated(forRemoval = true)
	public boolean deductReserve(String shopId, String skuId, Integer quantity) {
		// 兼容：直接委派到 reserve（expireSeconds 传 null）返回 reservationId 是否非空
		String r = reserve(shopId, skuId, quantity, null);
		return r != null;
	}

	/**
	 * @deprecated 使用 {@link #reserve(String, String, Integer, Integer)} (语义不同：旧方法只是增加预留，新方法为标准预留流程)
	 */
	@Deprecated(forRemoval = true)
	public boolean increaseReserve(String shopId, String skuId, Integer quantity) {
		String r = reserve(shopId, skuId, quantity, null);
		return r != null;
	}

	/**
	 * @deprecated 使用 {@link #adjustTotal(String, String, Integer, String)}
	 */
	@Deprecated(forRemoval = true)
	public boolean increaseTotal(String shopId, String skuId, Integer quantity) {
		return adjustTotal(shopId, skuId, quantity, "compat-increase");
	}

	/**
	 * @deprecated 使用 {@link #adjustTotal(String, String, Integer, String)} (delta 为负)
	 */
	@Deprecated(forRemoval = true)
	public boolean decreaseTotal(String shopId, String skuId, Integer quantity) {
		return adjustTotal(shopId, skuId, -quantity, "compat-decrease");
	}

	/**
	 * 旧批量查询接口：List<String> 形式 shopId:skuId
	 * @deprecated 使用 {@link #batchQueryAvailable(List)} with AvailableQuery
	 */
	@Deprecated(forRemoval = true)
	public List<Integer> batchQueryAvailable(List<String> shopSkuPairs) {
		if (shopSkuPairs == null) {
			return Collections.emptyList();
		}
		List<AvailableQuery> parsed = new ArrayList<>();
		for (String pair : shopSkuPairs) {
			if (pair == null || !pair.contains(":")) {
				throw new BusinessException(InventoryErrorCode.INVALID_PARAM, "format shopId:skuId required");
			}
			int idx = pair.indexOf(':');
			String shopId = pair.substring(0, idx);
			String skuId = pair.substring(idx + 1);
			AvailableQuery q = new AvailableQuery();
			q.setShopId(shopId);
			q.setSkuId(skuId);
			parsed.add(q);
		}
		// 委派新接口，但旧接口返回 List<Integer>，此处做兼容（提取 available 字段或占位 0）
		List<AvailableResponse> responses = batchQueryAvailable(parsed);
		List<Integer> result = new ArrayList<>(responses.size());
		for (AvailableResponse r : responses) {
			result.add(r == null ? 0 : r.getAvailable());
		}
		return result;
	}
}
