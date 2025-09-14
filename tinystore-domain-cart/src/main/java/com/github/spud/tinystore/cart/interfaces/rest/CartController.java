package com.github.spud.tinystore.cart.interfaces.rest;

import com.github.spud.tinystore.cart.application.CartService;
import com.github.spud.tinystore.cart.domain.model.Product;
import com.github.spud.tinystore.cart.interfaces.dto.CartDTO;
import com.github.spud.tinystore.cart.interfaces.dto.CartLineDTO;
import com.github.spud.tinystore.cart.interfaces.error.CartException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Spud
 * @date 2025/9/13
 */
@RestController
@RequestMapping("/cart")
public class CartController {
    private final CartService cartService = new CartService("demoUser"); // 实际应从会话获取

    @GetMapping
    public CartDTO getCart() {
        List<CartLineDTO> lineDTOs = cartService.getCartLines().stream()
            .map(line -> new CartLineDTO(
                line.product().shopId(),
                line.product().spuId(),
                line.product().skuId(),
                line.quantity(),
                line.selected(),
                line.valid()))
            .collect(Collectors.toList());
        return new CartDTO(cartService.getCart().getUserId(), lineDTOs);
    }

    @PostMapping("/add")
    public void addToCart(@RequestParam String shopId, @RequestParam String spuId, @RequestParam String skuId, @RequestParam Integer quantity) {
        if (quantity == null || quantity <= 0) throw new CartException("数量必须大于0");
        cartService.addProduct(new Product(shopId, spuId, skuId), quantity);
    }

    @PostMapping("/remove")
    public void removeFromCart(@RequestParam String shopId, @RequestParam String spuId, @RequestParam String skuId) {
        boolean removed = cartService.removeProduct(new Product(shopId, spuId, skuId));
        if (!removed) throw new CartException("商品不存在于购物车");
    }

    @PostMapping("/update")
    public void updateCartItem(@RequestParam String shopId, @RequestParam String spuId, @RequestParam String skuId, @RequestParam Integer quantity) {
        if (quantity == null || quantity <= 0) throw new CartException("数量必须大于0");
        boolean updated = cartService.updateProduct(new Product(shopId, spuId, skuId), quantity);
        if (!updated) throw new CartException("商品不存在于购物车");
    }
}
