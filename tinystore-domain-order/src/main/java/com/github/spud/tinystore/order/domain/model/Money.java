package com.github.spud.tinystore.order.domain.model;

/**
 * 金额
 *
 * @param amount   金额，单位分
 * @param currency 币种，ISO 4217
 */
public record Money(long amount, String currency) {
	
	public static Money ofCents(long amount, String currency) {
		return new Money(amount, currency);
	}

	public static Money ofDollars(double amount, String currency) {
		return new Money((long) (amount * 100), currency);
	}

	public static Money zero() {
		return new Money(0, "CNY");
	}

	public Money plus(Money other) {
		assert ensureSameCurrency(other);
		return new Money(this.amount + other.amount, this.currency);
	}

	public Money minus(Money other) {
		assert ensureSameCurrency(other);
		return new Money(this.amount - other.amount, this.currency);
	}

	public Money multiply(long other) {
		return new Money(this.amount * other, this.currency);
	}

	boolean ensureSameCurrency(Money other) {
		return this.currency.equals(other.currency);
	}

	boolean nonNegative() {
		return amount >= 0;
	}

	public Money add(Money money) {
		return this.plus(money);
	}
	
	public Money subtract(Money money) {
		return this.minus(money);
	}
}


