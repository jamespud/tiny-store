package com.github.spud.tinystore.order.infrastructure.acl;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author Spud
 * @date 2025/8/12
 */
@FeignClient(name = "cart-service")
public interface CartClient {

    // 这里可以定义与购物车服务交互的方法，例如获取购物车内容、添加商品到购物车等
    // 例如：
    @GetMapping("/cart/{userId}")
    Object getCartByUserId(@PathVariable("userId") Long userId);

    @PostMapping("/cart/add")
    void addToCart(@RequestBody Object cartItem);
}
