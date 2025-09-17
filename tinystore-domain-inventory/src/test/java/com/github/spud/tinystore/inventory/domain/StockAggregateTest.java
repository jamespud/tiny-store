package com.github.spud.tinystore.inventory.domain;

import com.github.spud.tinystore.inventory.domain.exception.InventoryBusinessException;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.StockAggregate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StockAggregateTest {

    @Test
    void reserveAndConfirmFlow() {
        StockAggregate stock = new StockAggregate("s1", "sku1", 100, 0, 0);
        Reservation r = stock.reserve(10, 60, null);
        Assertions.assertEquals(90, stock.getAvailable());
        stock.confirm(r);
        Assertions.assertEquals(90, stock.getTotalQuantity());
        Assertions.assertEquals(0, stock.getReservedQuantity());
        Assertions.assertEquals(90, stock.getAvailable());
    }

    @Test
    void reserveNotEnough() {
        StockAggregate stock = new StockAggregate("s1", "sku1", 5, 0, 0);
        Assertions.assertThrows(InventoryBusinessException.class, () -> stock.reserve(6, 60, null));
    }

    @Test
    void adjustNegativeBeyondAvailableFails() {
        StockAggregate stock = new StockAggregate("s1", "sku1", 5, 0, 0);
        stock.reserve(3, 60, null); // available=2
        Assertions.assertThrows(InventoryBusinessException.class, () -> stock.adjustTotal(-4, "reduce"));
    }
}

