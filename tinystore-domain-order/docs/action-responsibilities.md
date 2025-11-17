# Action 侧效职责清单（按事件）

本清单定义状态机跃迁成功后各 Action 的最小职责集，覆盖持久化字段、出箱事件、审计与并发语义。聚合内维度包括：`core_flow`、`payment`、`fulfillment`、`after_sale`。

通用约束：
- 并发：持久化使用版本号乐观锁，冲突返回 409/`ORDER-4090`（建议映射）
- 幂等：命令/回调入参含幂等键，成功结果可回放
- 出箱：本地事务内保存 Outbox（见出箱事件规范），发布器异步投递
- 审计：记录命令名、操作者/来源、traceId、orderId、事件/结果、时间

---

## PAYMENT_SUCCEEDED（支付成功）
- 持久化：
  - `core_flow`: `PENDING_PAYMENT` → `PAID`
  - `payment.status`: `PAID`；记录 `paymentId/amount/currency/isDeposit/isFinalPayment`
- 出箱：`event_type=order.payment.succeeded`（v1）
  - data：`orderNo,paymentId,amount,currency,userId`
- 审计：`cmdId,eventId,operator=system/gateway`
- 并发/幂等：`paymentId+orderId+amount` 去重；版本号校验

## PAYMENT_TIMEOUT（支付超时取消）
- 持久化：
  - `core_flow`: → `CANCELLED`
  - 释放库存/优惠券锁（如果仍持有）
- 出箱：`order.cancelled`（v1）
  - data：`orderNo,reason=PAYMENT_TIMEOUT`
- 审计：`scheduleId`
- 并发/幂等：`scheduleId` 去重

## USER_CANCELLED（用户取消，待支付）
- 持久化：`core_flow`: → `CANCELLED`
- 出箱：`order.cancelled`（v1, reason=USER_CANCELLED）
- 审计：`userId,reason`
- 并发/幂等：`orderId+userId+reason` 去重

## MERCHANT_CANCELLED（商家同意取消，已支付后）
- 持久化：
  - `core_flow`: → `CANCELLED`
  - `payment/after_sale`: 记录退款申请（若未启动），或进入退款中
- 出箱：`order.cancelled`（v1, reason=MERCHANT_CANCELLED）
- 审计：`operatorId`
- 并发/幂等：`orderId+decision+operatorId`

## MERCHANT_ACCEPTED（商家接单 / 保障接单）
- 持久化：`core_flow`: `PAID` → `ACCEPTED`
- 出箱：`order.accepted`（v1）
- 审计：`operatorId`

## FULFILLMENT_STARTED（开始履约/出库）
- 持久化：`core_flow`: `ACCEPTED` → `FULFILLING`；`fulfillment.status=PROCESSING`
- 出箱：`order.shipped`（v1）当含首个包裹发出时
- 审计：`operatorId,packageInfo`

## GOODS_SHIPPED（已发货，内部事件）
- 持久化：`fulfillment.status=SHIPPED`；记录 `trackingNo,company`
- 出箱：`order.shipped`（v1）
- 审计：`operatorId`

## GOODS_DELIVERED（妥投，内部事件）
- 持久化：`fulfillment.status=DELIVERED`；打开售后观察期开关（可按业务）
- 出箱：`order.delivered`（v1）
- 审计：`source=logistics,eventId`

## GOODS_RECEIVED（用户确认收货）
- 持久化：`core_flow`: `FULFILLING` → `COMPLETED`；`fulfillment.status=RECEIVED`
- 出箱：`order.completed`（v1）
- 审计：`userId`

## AUTO_RECEIVE_TIMEOUT（自动收货）
- 持久化：同 `GOODS_RECEIVED`；`operator=system`
- 出箱：`order.completed`（v1）
- 审计：`ruleId`

## RefundSucceededCommand（退款成功，维度事件）
- 持久化：`after_sale.status` 或 `payment.refundStatus` 更新为 `COMPLETED`
- 出箱：`order.refund.completed`（v1）
- 审计：`refundId,source`

## RejectCancelOrderCommand（商家拒绝取消）
- 持久化：记录取消申请被拒，维度字段更新；不改 `core_flow`
- 出箱：可选 `order.cancel.request.rejected`
- 审计：`operatorId,reason`

---

备注：
- 维度事件（如退款成功、行级售后）不应直接改变 `core_flow`，但需要与履约/收货等事件在 Guard 上协调（互斥/冻结策略）。
- 事件类型命名与载荷结构请与《outbox-event-schema.md》保持一致。
