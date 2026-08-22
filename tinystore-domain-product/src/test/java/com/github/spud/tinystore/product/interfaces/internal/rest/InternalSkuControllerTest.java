package com.github.spud.tinystore.product.interfaces.internal.rest;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.spud.tinystore.product.application.service.InternalSkuQueryService;
import com.github.spud.tinystore.product.interfaces.internal.dto.InternalSkuBatchQueryResponse;
import com.github.spud.tinystore.product.interfaces.internal.dto.InternalSkuDTO;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalSkuController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InternalSkuController Unit Tests")
class InternalSkuControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InternalSkuQueryService internalSkuQueryService;

	@Test
	@DisplayName("GET /internal/restful/sku/batch returns skuMap")
	void batchGetSkuInfo_ReturnsSkuMap() throws Exception {
		InternalSkuDTO dto = new InternalSkuDTO();
		dto.setSkuId("sku1");
		dto.setAvailable(true);
		dto.setUnitPrice(123);

		InternalSkuBatchQueryResponse resp = new InternalSkuBatchQueryResponse();
		resp.setSkuMap(Map.of("sku1", dto));

		when(internalSkuQueryService.batchGetSkuInfo(isNull(), eq(Set.of("sku1")))).thenReturn(resp);

		mockMvc.perform(get("/internal/restful/sku/batch").queryParam("skuIds", "sku1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.skuMap.sku1.skuId").value("sku1"))
			.andExpect(jsonPath("$.skuMap.sku1.unitPrice").value(123));
	}

	@Test
	@DisplayName("GET .../batch with explicit shopId + comma CSV should pass shop-scoped skuId set")
	void batchGetSkuInfo_withExplicitShopIdAndCsv() throws Exception {
		InternalSkuDTO dto = new InternalSkuDTO();
		dto.setSkuId("sku2");
		dto.setAvailable(true);
		dto.setUnitPrice(456);

		InternalSkuBatchQueryResponse resp = new InternalSkuBatchQueryResponse();
		resp.setSkuMap(Map.of("sku2", dto));

		when(internalSkuQueryService.batchGetSkuInfo(eq("SHOP_A"), eq(Set.of("sku2", "sku3")))).thenReturn(resp);

		mockMvc.perform(get("/internal/restful/sku/batch")
				.queryParam("shopId", "SHOP_A")
				.queryParam("skuIds", "sku2,sku3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.skuMap.sku2.skuId").value("sku2"))
			.andExpect(jsonPath("$.skuMap.sku2.unitPrice").value(456));
	}
}
