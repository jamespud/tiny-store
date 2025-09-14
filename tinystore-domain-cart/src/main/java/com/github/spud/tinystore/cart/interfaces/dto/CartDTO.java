package com.github.spud.tinystore.cart.interfaces.dto;

import java.util.List;

public class CartDTO {
    private String userId;
    private List<CartLineDTO> lines;

    public CartDTO(String userId, List<CartLineDTO> lines) {
        this.userId = userId;
        this.lines = lines;
    }

    public String getUserId() {
        return userId;
    }

    public List<CartLineDTO> getLines() {
        return lines;
    }
}

