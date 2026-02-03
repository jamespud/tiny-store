package com.github.spud.tinystore.infrastructure.rpc.payment;

import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient(name = "tinystore-domain-account", path = "/account")
public interface AccountClient {

}
