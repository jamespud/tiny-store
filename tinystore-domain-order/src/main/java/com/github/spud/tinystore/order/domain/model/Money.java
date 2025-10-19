package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.exception.OrderDomainException;

/**
 * 金额值对象
 * <p>
 * 表示货币金额，默认币种为 CNY（人民币） 金额单位为分（cents），避免浮点精度问题
 *
 * @param amount   金额，单位分
 * @param currency 币种，ISO 4217 标准，默认 CNY
 */
public record Money(long amount, String currency) {

	/**
	 * 创建指定分数和币种的金额
	 *
	 * @param amount   金额（分）
	 * @param currency 币种代码
	 * @return Money 实例
	 */
	public static Money ofCents(long amount, String currency) {
		return new Money(amount, currency);
	}

	/**
	 * 创建零金额，默认 CNY 币种
	 *
	 * @return 零金额
	 */
	public static Money zero() {
		return new Money(0, "CNY");
	}

	/**
	 * 创建与给定金额相同币种的零金额
	 *
	 * @param m 参考金额
	 * @return 同币种零金额
	 */
	public static Money zeroLike(Money m) {
		return new Money(0, m.currency);
	}

	public static Money of(long i) {
		return zero().add(new Money(i, "CNY"));
	}

	/**
	 * 金额相加
	 *
	 * @param other 另一金额
	 * @return 相加结果
	 * @throws OrderDomainException 币种不一致时抛出
	 */
	public Money plus(Money other) {
		ensureSameCurrency(other);
		return new Money(this.amount + other.amount, this.currency);
	}

	/**
	 * 金额相减
	 *
	 * @param other 另一金额
	 * @return 相减结果
	 * @throws OrderDomainException 币种不一致时抛出
	 */
	public Money minus(Money other) {
		ensureSameCurrency(other);
		return new Money(this.amount - other.amount, this.currency);
	}

	/**
	 * 金额乘法
	 *
	 * @param multiplier 乘数
	 * @return 乘法结果
	 */
	public Money multiply(long multiplier) {
		return new Money(this.amount * multiplier, this.currency);
	}

	/**
	 * 确保币种一致，否则抛出异常
	 *
	 * @param other 另一金额
	 * @throws OrderDomainException 币种不一致时抛出
	 */
	private void ensureSameCurrency(Money other) {
		if (!this.currency.equals(other.currency)) {
			throw new OrderDomainException(
				String.format("Currency mismatch: %s vs %s", this.currency, other.currency),
				"CURRENCY_MISMATCH"
			);
		}
	}

	/**
	 * 检查金额是否非负
	 *
	 * @return true 如果金额 >= 0
	 */
	public boolean nonNegative() {
		return amount >= 0;
	}

	/**
	 * 金额相加（别名方法）
	 *
	 * @param money 另一金额
	 * @return 相加结果
	 */
	public Money add(Money money) {
		return this.plus(money);
	}

	/**
	 * 金额相减（别名方法）
	 *
	 * @param money 另一金额
	 * @return 相减结果
	 */
	public Money subtract(Money money) {
		return this.minus(money);
	}

	public String currency() {
		return currency;
	}
}


