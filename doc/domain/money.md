# 金额模型（Money）

- 单位：分（cents），默认 CNY。
- 移除浮点构造（避免精度丢失），统一使用整数或十进制精度类型。
- 货币一致性强校验：跨币种运算直接抛错。
- 金额守恒：
  - 行：linePayable = lineTotal - lineDiscount ≥ 0
  - 子单：subOrderPayable = Σ linePayable + charges - discounts ≥ 0
  - 订单：orderPayable = Σ subOrderPayable
