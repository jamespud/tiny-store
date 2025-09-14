package com.github.spud.tinystore.cart.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Spud
 * @date 2025/9/13
 */
public class Cart {

    private String userId;

    private List<CartLine> lines;

    public Cart(String userId) {
        this.userId = userId;
        this.lines = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public List<CartLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void addLine(CartLine line) {
        lines.add(line);
    }

    public boolean removeLine(Product product) {
        return lines.removeIf(l -> l.product().equals(product));
    }

    public boolean updateLine(Product product, int quantity) {
        Optional<CartLine> opt = lines.stream().filter(l -> l.product().equals(product)).findFirst();
        if (opt.isPresent()) {
            CartLine old = opt.get();
            lines.remove(old);
            lines.add(new CartLine(product, quantity, old.addedAt(), old.selected(), old.valid()));
            return true;
        }
        return false;
    }
}

