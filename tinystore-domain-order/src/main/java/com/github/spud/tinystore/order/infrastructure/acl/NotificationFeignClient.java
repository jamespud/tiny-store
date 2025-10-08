package com.github.spud.tinystore.order.infrastructure.acl;

import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface NotificationFeignClient {

	void sendMultiShopOrderCreateNotice(String userId, String mainOrderNo, long amount, int size);
}
