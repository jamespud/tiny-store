package com.github.spud.tinystore.product.domain.model.value;

import lombok.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money implements Comparable<Money> {
    private final BigDecimal amount; // currency assumed CNY for now

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(@NonNull BigDecimal amount) {
        return new Money(amount);
    }

    public static Money zero() { return new Money(BigDecimal.ZERO); }

    public BigDecimal amount() { return amount; }

    public Money add(Money other) { return new Money(this.amount.add(other.amount)); }
    public Money subtract(Money other) { return new Money(this.amount.subtract(other.amount)); }
    public Money multiply(BigDecimal factor) { return new Money(this.amount.multiply(factor)); }

    public Money min(Money other) { return this.compareTo(other) <= 0 ? this : other; }
    public Money max(Money other) { return this.compareTo(other) >= 0 ? this : other; }

    @Override public int compareTo(Money o) { return this.amount.compareTo(o.amount); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0;
    }

    @Override public int hashCode() { return Objects.hash(amount); }

    @Override public String toString() { return amount.toPlainString(); }
}
