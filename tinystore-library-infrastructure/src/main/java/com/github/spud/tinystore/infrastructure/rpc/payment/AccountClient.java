package com.github.spud.tinystore.infrastructure.rpc.payment;

import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient("/account")
public interface AccountClient {

}
