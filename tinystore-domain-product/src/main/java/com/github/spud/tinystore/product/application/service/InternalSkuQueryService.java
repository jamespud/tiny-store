package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.SkuEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaSkuRepository;
import com.github.spud.tinystore.product.interfaces.internal.dto.InternalSkuBatchQueryResponse;
import com.github.spud.tinystore.product.interfaces.internal.dto.InternalSkuDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InternalSkuQueryService {

	private final JpaSkuRepository jpaSkuRepository;
	private final TenantRepositoryConfig.TenantContext tenantContext;

	public InternalSkuBatchQueryResponse batchGetSkuInfo(Set<String> skuIds) {
		InternalSkuBatchQueryResponse resp = new InternalSkuBatchQueryResponse();
		Map<String, InternalSkuDTO> skuMap = new HashMap<>();
		resp.setSkuMap(skuMap);
		if (skuIds == null || skuIds.isEmpty()) {
			return resp;
		}
		String tenantId = tenantContext.getTenantId();
		List<SkuEntity> entities = jpaSkuRepository.findByTenantIdAndSkuIdIn(tenantId, skuIds);
		for (SkuEntity e : entities) {
			if (e == null || e.getSkuId() == null) {
				continue;
			}
			InternalSkuDTO dto = new InternalSkuDTO();
			dto.setSkuId(e.getSkuId());
			dto.setMerchantId(e.getMerchantId());
			dto.setAvailable("AVAILABLE".equals(e.getStatus()));
			dto.setUnitPrice(e.getUnitPriceCents() != null ? e.getUnitPriceCents() : 0L);
			dto.setPromotePrice(e.getPromotePriceCents() != null ? e.getPromotePriceCents() : 0L);
			dto.setWeight(e.getWeightGrams() != null ? (e.getWeightGrams() / 1000.0) : 0.0);
			dto.setSkuName(e.getSkuName());
			dto.setSpecJson(e.getSpecJson());
			skuMap.put(e.getSkuId(), dto);
		}
		return resp;
	}
}

