package com.github.spud.tinystore.cart.application;

import com.github.spud.tinystore.cart.domain.model.Cart;
import com.github.spud.tinystore.cart.domain.model.CartLine;
import com.github.spud.tinystore.cart.domain.model.Product;
import java.time.LocalDateTime;
import java.util.List;

public class CartService {
    // 生产环境应持久化，当前仅内存实现，后续可扩展
    private Cart cart;

    public CartService(String userId) {
        this.cart = new Cart(userId);
    }

    public void addProduct(Product product, int quantity) {
        cart.addLine(new CartLine(product, quantity, LocalDateTime.now(), true, true));
    }

    public boolean removeProduct(Product product) {
        return cart.removeLine(product);
    }

    public boolean updateProduct(Product product, int quantity) {
        return cart.updateLine(product, quantity);
    }

    public List<CartLine> getCartLines() {
        return cart.getLines();
    }

    public Cart getCart() {
        return cart;
    }
}

