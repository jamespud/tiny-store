package com.github.spud.tinystore.order.domain.status;

/**
 * 售后范围枚举
 * <p>
 * 定义售后申请的作用范围级别
 */
public enum AfterSaleScope {
	/**
	 * 行级售后：针对特定订单行的部分或全部数量
	 * 支持按行、按部分数量进行精细化售后处理
	 */
	LINE,

	/**
	 * 子订单级售后：针对整个子订单
	 * 预留字段，用于未来可能的子订单级售后需求
	 */
	SUB_ORDER,

	/**
	 * 订单级售后：针对整个订单
	 * 预留字段，用于未来可能的全订单级售后需求
	 */
	ORDER
}