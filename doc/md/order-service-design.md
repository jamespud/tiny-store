# 订单服务设计说明（微服务版，简化价计算 + 固定运费）

更新时间：2025-09-13

本说明聚焦订单域，暂不实现支付/库存等外部系统，仅以 FeignClient 占位；不考虑优惠、满减等复杂定价，仅做最简单的金额计算：商品小计之和 + 固定运费。

---

## 1. 目标与范围
- 支持用户创建订单的基本流程：
  - 订单预览（校验、计算价、缓存预览结果，可选）
  - 提交订单（持久化、生成子订单、记录事件）
  - 取消订单（依据状态校验，记录事件）
- 仅考虑订单域的职责：聚合、状态、金额计算、幂等、事件。
- 外部依赖（商品价格、库存锁定、支付）均以 FeignClient 占位，稍后实现。
- 金额计算规则：$total = \sum(price_i \times qty_i) + shippingFee$，币种固定 CNY。

## 2. 总体架构（按本仓库实践）
- application：编排与用例服务，例如 `OrderApplicationService`。
- domain：聚合根、实体、值对象、领域服务（如 `OrderPriceCalculationService`、`OrderCancelDomainService`）。
- infrastructure：持久化（Repository 实现）、对外适配/ACL（Feign 占位 ）。
- interfaces：REST 层（`UserOrderController`），DTO/VO 映射。
- 事件：Outbox 方式记录订单创建/取消等事件，稍后由异步 sender 投递到 MQ（或保留占位）。

## 3. 领域模型（聚合与状态）
- 聚合根：`Order`
  - 属性：buyer、lines(List<OrderLine>)、products(Map<Product,Integer>)、pricingSummary、address、deviceId、version、calculated 等。
  - 规约：
    - 同一提交请求必须具备幂等性（同一 `idempotentKey` 重复调用不可重复落单）。
    - `calculate()` 仅在未计算状态执行；计算后标记 `calculated=true`。
- 子聚合：`OrderLine`（按店铺拆单，当前实现已经支持 `shopId` 维度）
  - 属性：shopId、products、行总额、行附加费（含运费）等。
- 状态：采用现有 `OrderStatus`，与 `OrderCancelDomainService` 结合：
  - 创建 -> 待支付（或支付处理中） -> 已支付 -> 配送 -> 完成/关闭。
  - 简化取消策略：CREATED/PAYMENT_PROCESSING 可直接取消；已发货及终态不允许。

## 4. 用例与流程
### 4.1 订单预览（可选）
- 输入：用户、收货地址、商品清单（spuId/skuId/qty）、deviceId。
- 步骤：
  1) 校验用户与商品（下架校验以 `ProductValidatorService.validateProducts()` 占位）。
  2) 查询商品价格（`ProductService`/`ProductClient` 占位）。
  3) 计算金额：小计之和 + 固定运费（按行/按订单，见第 5 节）。
  4) 将预览结果缓存（`previewTTL`，Redis 可用 `OrderRedisOperatorService` 扩展），Key 可为 `preview:{user}:{hash(items+addr)}`。
- 输出：预览价明细（`PreviewOrderResult/VO`）。

### 4.2 提交订单
- 输入：`CreateOrderCommand`（含 `idempotentKey`，`deviceId`）。
- 步骤：
  1) 幂等校验：
     - 方案 A：数据库 `idempotency` 表，(userId, idempotentKey) 唯一；落库时回填结果引用。
     - 方案 B：Redis SETNX，TTL 10-30s；失败则直接返回前次结果或提示重试。
  2) 校验用户、商品、地址有效；根据 skuId 拉取价格信息。
  3) 金额计算（与预览相同，独立重算，拒绝前端传入价格）。
  4) 拆单生成 `OrderLine`；落库（主订单/子订单/明细项）。
  5) 记录 Outbox 事件（OrderCreated）。
  6) 返回下单结果（含订单号、行订单号）。
- 输出：`CreateOrderResult/VO`。

### 4.3 取消订单
- 输入：`CancelOrderCommand`。
- 步骤：
  1) 读取订单/子订单状态，调用 `OrderCancelDomainService.decide()`。
  2) 若允许简单取消：直接将目标订单行置为 CANCELLED，记录 Outbox 事件。
  3) 若需退款后取消：记录取消申请事件，稍后待支付服务回调成功再更新为取消（此处仅留占位）。
- 输出：取消提交结果。

## 5. 简化金额计算（固定运费）
- 公式：
  - 商品小计：$subtotal = \sum_i price(sku_i) \times qty_i$
  - 固定运费：`shippingFee = 1000`（单位分，示例 10.00 CNY），可配置于 `application.yml`：
    ```yaml
    order:
      pricing:
        currency: CNY
        shipping-fee-cents: 1000
        shipping-scope: per-order # 可选 per-order/per-line
    ```
  - 应付：`payable = subtotal + shippingFee`
- 作用域（建议默认 per-order）：
  - per-order：整单只加一次固定运费，简单且符合多数场景。
  - per-line：每个店铺一笔运费（多店铺时与 `OrderLine` 对齐）。
- 对应实现点：
  - `OrderPriceCalculationService.calculatePrice(Order)`：去掉优惠券相关逻辑，分摊逻辑留空；按配置决定是否按行加入运费。
  - `calculateLineChargeItem(...)`：返回包含固定运费（和可选的 0 元税费占位）。

## 6. 接口设计（REST）
结合现有 `UserOrderController`：
- POST `/order/user/submit/preview`
  - Request：`PreviewOrderRequest`（已有），包含 items、addressId、deviceId。
  - Response：`PreviewOrderVO`，包含 subtotal、shipping、payable、lines 明细。
- POST `/order/user/submit/apply`
  - Request：`CreateOrderRequest`（已有），携带 `idempotentKey`。
  - Response：`CreateOrderVO`，返回订单号、行单号列表、金额与状态。
- POST `/order/user/cancel/apply`
  - Request：`CancelRequest`（已有），携带原因与 `idempotencyKey`。
  - Response：{ status: "待确认" | "已取消" }。
- GET `/order/user/{orderId}`（建议补充）
  - Response：订单详情（聚合 `Order` + `OrderLine`）。

DTO 设计与转换：
- Request -> Command：保持在 interfaces 层转换，沿用已有 `toCommand(userId)`。
- Result -> VO：由 application 层返回 Result，再在 interfaces 层转为 VO。

## 7. 数据模型与持久化
建议最小表结构（按模块已有 Repository 命名）：
- `order`（主订单）
  - id(PK)、user_id、total_cents、shipping_cents、payable_cents、currency、status、address、device_id、version、created_at、updated_at
- `order_line`（子订单，按店铺）
  - id(PK)、order_id、shop_id、total_cents、shipping_cents、payable_cents、status、created_at、updated_at
- `order_item`（行内商品）
  - id(PK)、order_line_id、spu_id、sku_id、title、price_cents、qty、total_cents
- `outbox`（事件表）
  - id、aggregate_id、aggregate_type、event_type、payload、occurred_at、status、retry_count
- `idempotency`（幂等表，可选）
  - id、user_id、key、request_hash、order_id、status、created_at、expired_at；(user_id,key) 唯一。

注：若已有 schema，可增量对齐上述列。读写通过 `OrderRepository` / `SubOrderRepository` 等实现。

## 8. 事务、一致性与并发
- 创建订单：采用本地事务将（主订单/子订单/明细 + outbox）一起提交。
- 幂等：
  - 数据库唯一约束 vs Redis SETNX 均可；线上建议两者结合（快速失败 + 最终一致）。
- 并发：
  - 同用户同 `idempotentKey` 不可并发成功；可用 Redis 分布式锁 `lock:order:apply:{user}:{key}`
  - 库存锁定因暂未实现，这里只保留占位与时序说明（先尝试锁定，再落单；失败回滚）。

## 9. 对外集成占位（Feign）
- `InventoryClient`（占位）：
  - `reserve(Map<skuId, qty>, requestId)` -> boolean
  - `release(Map<skuId, qty>, requestId)` -> void
- `PaymentClient`（占位）：
  - `createPayment(orderId, amount)` -> paymentId
  - 回调 webhook：支付成功/失败，驱动状态迁移
- `ProductClient`（已存在）：
  - `getSkuPrice(List<skuId>)` -> Map<skuId, price>

上述接口定义在 `infrastructure/acl`，当前实现可返回固定/空值。

## 10. 事件与 Outbox
- 事件类型：`OrderCreated`, `OrderCancelled`, `OrderCancelRequested` 等。
- 落单成功后写入 `outbox`，由后台任务/消息发送器 `OutboxEventSender` 扫描并投递（占位）。
- 消费方（库存/支付）可在后续真正接入。

## 11. 配置与开关
- `order.pricing.currency`：默认 CNY
- `order.pricing.shipping-fee-cents`：固定运费（默认 1000）
- `order.pricing.shipping-scope`：per-order | per-line
- `order.preview.ttl`：预览缓存 TTL（默认 60s）

## 12. 最小实现改造建议（与当前代码对齐）
- `OrderPriceCalculationService`
  - 去除优惠券与折扣，保留 Map<Product,Integer> 的小计累加。
  - 从配置读取 `shipping-fee-cents`；若 scope=per-line，则在 `calculateLineChargeItem` 中生成一条运费项，否则仅在整单生成一次并分摊到某一行或专门的合计字段。
- `Money.minus()` 当前实现为加法，建议修正为减法。
- `CreateOrderCommand`
  - 实现 `getProductIds()` 与 `getProductMap()`，从 `products` 拆出 skuId 与数量，避免应用层再汇总。
- `OrderApplicationService`
  - 预览：从 `ProductService` 拉价格，调用 `OrderFactory` 组单，`OrderPriceCalculationService` 计算价，缓存 `PreviewOrderResult`。
  - 提交：增加幂等校验（可先 Redis），落单 + Outbox。

## 13. 渐进式实现计划（里程碑）
1) 金额计算简化版落地：修复 `Money.minus`、实现固定运费；提交/预览跑通。
2) 幂等与预览缓存：增加 Redis 键与 TTL；`idempotency` 表可后置。
3) Outbox 事件记录与最小 sender：打印日志占位，接口约定。
4) Feign 占位：`InventoryClient`、`PaymentClient` 接口定义与 stub。
5) 查询接口：补齐 GET 详情/列表。

## 14. 未来扩展
- 优惠券/活动引擎、分摊策略、运费模板（按重量/距离/店铺）。
- 多维状态（Payment/Fulfillment/AfterSale）与状态机引擎。
- Saga/TCC 对接库存与支付。
- 审计/风控/限流与防刷（结合 `deviceId`）。
- 可观测性（traceId、指标、告警）。

---

如需，我可以按本设计直接提交：
- 修复 `Money.minus`，
- 在 `OrderPriceCalculationService` 实现固定运费（支持 per-order/per-line 配置），
- 补全 `CreateOrderCommand#getProductIds/#getProductMap`，
- 草拟 `InventoryClient`/`PaymentClient` Feign 占位。
