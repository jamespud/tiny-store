package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface PaymentFeignClient {

	CreateMergePayResponse createMergePayment(CreateMergePayRequest build);

	@Builder
	class CreateMergePayRequest{
		private String mainOrderNo;
		private List<String> subOrderNos;
		private String userId;
		private long totalPayAmount;
		private String subject;	
	}
	
	@Data
	class CreateMergePayResponse{
		private String mergePayUrl;
	}

}
