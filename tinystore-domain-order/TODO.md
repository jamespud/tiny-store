我将快速浏览订单模块的包结构，确保计划中的文件路径/命名与现有风格一致，然后输出一份精确的实施方案与分解清单。

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application/command)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application/command/user)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application/service)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/domain)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/domain/statemachine)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/dto)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/dto/request)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/dto/response)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/domain/statemachine/status)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/domain/event)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/infrastructure)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/infrastructure/event)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/infrastructure/event/outbox)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/infrastructure/event/publisher)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest/UserOrderController.java)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest/MerchantOrderController.java)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest/OrderPaymentCallbackController.java)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest/InternalOrderController.java)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application/service/OrderApplicationService.java#1-1), lines 1 to 400

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/domain/statemachine/event)

Read [](file:///d%3A/proj/tiny-store/tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/domain/statemachine/event/OrderEvent.java)

我将把执行计划拆分为可跟踪的TODO清单，便于后续逐项实施与验收。

Created 20 todos

[MODE: PLAN]
以下为“覆盖所有订单API与状态流转逻辑”的详细技术实施方案，仅含规格与改动清单（不含任何实现代码）。末尾附带完整的可执行检查清单，确保实施无需再做创设性决策。

一、范围与目标
- 范围: tinystore-domain-order 模块内的对外接口（用户/商家/内部回调）、应用服务、状态机（事件/Guard/Action）、出箱事件、幂等/并发治理、错误语义、审计与追踪、测试与文档。
- 目标: 覆盖提交/支付成功/接单/发货/妥投/收货/取消/售后全链路；确保幂等正确、状态派生一致、事件出箱规范、具备可观测与可回归。

二、API 契约与分层对齐
1) 用户侧接口 `interfaces/rest/UserOrderController`
- `POST /order/user/submit/preview`
  - 入参: `PreviewOrderRequest`
  - 调用: `OrderApplicationService.orderPreview(ConfirmOrderCommand)`
  - 幂等: 无需
  - 响应: `PreviewOrderVO`
- `POST /order/user/submit/apply`
  - 入参: `CreateOrderRequest`（内含 `idempotentKey`）
  - 调用: `OrderApplicationService.submitOrder(SubmitOrderCommand)`
  - 幂等: 必须，使用 `IdempotencyStorage` 缓存+回放
  - 响应: `CreateOrderVO`/`CreateOrderResponse`
- `POST /order/user/cancel/preview`
  - 入参: `orderId`（保持现状）
  - 调用: `OrderApplicationService.cancelPreview(String)`
  - 幂等: 无需
  - 响应: 结构化预览（保持现状占位）
- `POST /order/user/cancel/apply`
  - 入参: `CancelRequest`
  - 调用: `OrderApplicationService.cancelOrder(ApplyCancelCommand|CancelOrderCommand)`
  - 幂等: `cancelIdempotencyKey`（从请求生成：`orderId+reason+requester`）
  - 响应: 结构化ACK（保持现状）
- `POST /order/user/confirm-receipt`
  - 入参: `ConfirmReceiptRequest`
  - 调用: `OrderApplicationService.confirmReceipt(ConfirmReceiptCommand)`
  - 幂等: `receiptIdempotencyKey`（`orderId+userId`）
  - 响应: `BasicAckVO`
- `POST /order/user/after-sale/apply`
  - 入参: `AfterSaleApplyRequest`（行级/子单级：`afterSaleType,lineId(optional),reason,amount(optional)`）
  - 调用: `OrderApplicationService.applyAfterSale(AfterSaleApplyCommand)`
  - 幂等: `afterSaleKey`（`orderId+lineId+type+createTsBucket`）
  - 响应: `{"afterSaleId": "...", "status": "success"}`

2) 商家侧接口 `interfaces/rest/MerchantOrderController`
- `POST /order/merchant/order/receive`
  - 入参: `MerchantAcceptRequest`
  - 调用: `OrderApplicationService.merchantAccept(MerchantAcceptCommand)`
  - 幂等: `acceptKey`（`orderId+operatorId`）
  - 响应: `BasicAckVO`
- `POST /order/merchant/cancel/approve`
  - 入参: `CancelApproveRequest`
  - 调用: `OrderApplicationService.approveCancelRequest(ApproveCancelOrderCommand)`
  - 幂等: `cancelDecisionKey`（`orderId+approve+operatorId`）
  - 响应: `BasicAckVO`
- `POST /order/merchant/cancel/reject`
  - 入参: `CancelRejectRequest`
  - 调用: `OrderApplicationService.rejectCancelRequest(RejectCancelOrderCommand)`
  - 幂等: 同上
  - 响应: `BasicAckVO`
- `POST /order/merchant/ship`
  - 入参: `ShipOrderRequest`（可含包裹/物流信息）
  - 调用: `OrderApplicationService.shipOrder(ShipOrderCommand)`
  - 幂等: `shipKey`（`orderId+packageNo`）
  - 响应: `BasicAckVO`
- `POST /order/merchant/delivery/confirm`
  - 入参: `DeliveredRequest`
  - 调用: `OrderApplicationService.confirmDelivered(DeliveredCommand)`
  - 幂等: `deliveredKey`（`orderId+packageNo`）
  - 响应: `BasicAckVO`

3) 内部回调接口 `interfaces/rest/InternalOrderController`
- `POST /order/internal/payment/success`
  - 入参: `PaymentSuccessRequest`（外部名保留）；映射命令使用 `PaymentSucceededCommand`
  - 调用: `OrderApplicationService.onPaymentSuccess(PaymentSucceededCommand)`
  - 幂等: `IdempotencyRepository`（`tradeNo/orderNo+transactionId+amount`）
  - 签名: 验真后处理
  - 响应: `BasicAckVO`
- `POST /order/internal/logistics/delivered`
  - 入参: `DeliveredRequest`
  - 调用: `OrderApplicationService.onLogisticsDelivered(DeliveredCommand)`
  - 幂等: `logisticsEventId`
  - 响应: `BasicAckVO`
- `POST /order/internal/refund/success`
  - 入参: `RefundSuccessRequest`（外部名保留）；映射命令使用 `RefundSucceededCommand`
  - 调用: `OrderApplicationService.onRefundSuccess(RefundSucceededCommand)`
  - 幂等: `refundTransactionId`
  - 响应: `BasicAckVO`
- `POST /order/internal/timeout/unpaid-cancel`
  - 入参: `UnpaidTimeoutRequest`
  - 调用: `OrderApplicationService.timeoutCancel(UnpaidTimeoutCancelCommand)`
  - 幂等: `scheduleId`
  - 响应: `BasicAckVO`
- `POST /order/internal/auto/complete`
  - 入参: `AutoCompleteRequest`
  - 调用: `OrderApplicationService.autoComplete(AutoCompleteCommand)`
  - 幂等: `orderId+ruleId/timeBucket`
  - 响应: `BasicAckVO`
- `POST /order/internal/auto/await-fulfillment`
  - 入参: `MoveToAwaitFulfillmentRequest`
  - 调用: `OrderApplicationService.moveToAwaitFulfillment(MoveToAwaitFulfillmentCommand)`
  - 幂等: `eventId`
  - 响应: `BasicAckVO`
- 注: 注释状态的 `OrderPaymentCallbackController` 保持移除，统一走 `InternalOrderController`。

三、命令与状态机映射
- 事件枚举: `domain/statemachine/event/OrderEvent`
- 映射关系:
  - PaymentSucceededCommand → PAYMENT_SUCCEEDED
  - UnpaidTimeoutCancelCommand → PAYMENT_TIMEOUT 或 SYSTEM_CANCELLED（按现有配置择一，一致化）
  - MerchantAcceptCommand → MERCHANT_ACCEPTED
  - ShipOrderCommand → GOODS_SHIPPED
  - DeliveredCommand → GOODS_DELIVERED
  - ConfirmReceiptCommand → GOODS_RECEIVED
  - ApplyCancelCommand/CancelOrderCommand → USER_CANCELLED（或在支付后分支走退款子流程）
  - RefundSucceededCommand →（仅影响 payment/after-sale 维度，不直接驱动 core_flow，保留事件派发）
  - AfterSaleApplyCommand → 仅更新 after-sale 维度并派发事件，不改变 core_flow
- Guard 要点:
  - 仅在合法前置状态允许跃迁（如支付成功仅在 CREATED/UNPAID）。
  - 履约互斥: 已取消/已完成/售后冻结态禁止发货与收货。
  - 售后互斥: 行级售后对对应行的履约/收货做 Guard。
- Action 职责:
  - 原子化持久化分维状态（子单: core_flow/payment/fulfillment/after_sale）。
  - 生成并写入 Outbox 事件（类型、载荷、版本）。
  - 审计日志记录（命令上下文、操作者、TraceId）。
  - 不进行复杂业务校验（留在应用服务）；不做外部IO（除出箱）。

四、出箱事件与一致性
- 出箱服务: `infrastructure/event/outbox/OutboxEventService`, `infrastructure/event/publisher/OutboxEventPublisher`
- 事件规范:
  - `event_type`: 使用 `order.lifecycle.changed`、`order.payment.succeeded`、`order.fulfillment.shipped`、`order.fulfillment.delivered`、`order.received`、`order.cancelled`、`order.refund.succeeded`、`order.aftersale.applied` 等蛇形命名。
  - `event_payload`: JSON，字段含 `version`, `aggregateId(mainOrderId)`, `subOrderId(optional)`, `occurredAt`, `tenantId`, `operator`, `data{...}`。
  - `version`: 从 `v1` 开始，新增字段保持向后兼容。
  - 分区键: `mainOrderId`；去重键: `eventId` 或 `(aggregateId,version,etype)`。
- 发布流程:
  - 本地事务内写入 Outbox；独立发布器轮询发布；失败重试、死信记录与补偿任务说明文档化。
- 命名一致性:
  - 保持数据库列 `event_type`, `event_payload`（对齐现状）；文档同步此命名。

五、幂等与并发治理
- 幂等策略:
  - 用户提交/取消/收货/售后按上述幂等键定义；内部回调按第三方交易/事件ID。
  - 存储: `IdempotencyRepository` + `IdempotencyStorage` 组合，成功结果回放。
  - 清理: TTL 策略 7-30 天；批处理清理任务（后续脚本/作业）。
- 并发策略:
  - 子单级乐观锁（版本号）；冲突返回专用业务码（如 `ORDER-4090`）与 409。
  - 控制器统一错误映射，返回可重试建议。

六、错误语义与安全
- 错误码:
  - 业务错误前缀 `ORDER-xxxx`，支付/履约/售后/幂等等分段。
  - `interfaces/error` 下统一异常与 `@ControllerAdvice` 映射。
- 鉴权/租户:
  - 用户侧使用 `UserIdProvider` 校验归属；商家侧校验 `operatorId` 与商家关系。
  - 事件链路透传 `tenantId` 与 `userId` 到 `event_payload`。

七、查询与派生
- 继续使用 `OrderQueryService`/`MainOrderStatusDeriver` 实时派生主单展示态，短期不落投影表。
- 若后续引入投影，保留接口不变：在服务内切换数据源。

八、审计与可观测
- 审计:
  - 在应用服务入口写入审计记录（命令名、操作者、TraceId、订单ID、结果）。
  - 存储策略与留存周期文档说明。
- 指标:
  - Outbox滞留、发布延迟、状态机失败、幂等冲突、控制器5xx/4xx比率。
  - 日志规范：统一包含 `correlationId/traceId`。

九、命名规范/去重
- 命令命名统一为过去式：`PaymentSucceededCommand`, `RefundSucceededCommand`。
- 外部请求DTO名可保持现状（兼容），在 `toCommand()` 中映射。
- 移除重复/过时命令类，更新引用。

十、测试策略与用例
- 单元测试（application/controller）:
  - 用户提交流程（幂等回放）、取消成功/拒绝、确认收货、售后申请（行级/整单）。
  - 商家接单、发货、妥投确认。
  - 回调支付成功/退款成功、超时取消、自动完成（含幂等重放）。
- 集成测试（状态机）:
  - 主路径：支付→待履约→发货→妥投→收货→完成。
  - 分叉：未支付超时取消、支付后取消→退款、发货后退款→售后。
  - 断言分维状态与主单展示态一致性；断言Outbox写入。
- 事件测试:
  - 每个跃迁的 `event_type`/`payload` 正确性、版本与去重键。
- 只运行订单模块测试，避免受产品模块影响（Maven module 选择性执行）。

十一、文档与迁移
- README: API清单/错误码/幂等策略/状态流转与事件契约/指标。
- SQL校验: `db/main.sql` 对齐列命名与索引；若需新增表（幂等/审计），补充迁移脚本与回滚。

实施涉及文件（仅列关键位点）
- 控制器: 
  - UserOrderController.java
  - MerchantOrderController.java
  - InternalOrderController.java（保留；OrderPaymentCallbackController.java 维持移除）
- DTO:
  - `interfaces/dto/request/*.java`（确保 `toCommand()` 映射到统一命令）
  - `interfaces/dto/response/*.java`
- 应用服务:
  - OrderApplicationService.java（补齐方法、整合幂等、审计）
  - `application/service/OrderPaymentCallbackAppService.java`（若不再使用则标记废弃）
- 命令:
  - `application/command/*.java`（统一命名，移除重复）
- 状态机:
  - `domain/statemachine/OrderStateMachineConfig.java`（补齐Guard/Action；核对事件映射）
  - OrderEvent.java（保持/微调）
  - `domain/statemachine/status/*.java`（保持）
- 事件/出箱:
  - `domain/event/*.java`（对齐事件类型与载荷）
  - `infrastructure/event/outbox/OutboxEventService.java`
  - `infrastructure/event/publisher/OutboxEventPublisher.java`
- 错误与拦截:
  - `interfaces/error/*`
  - `interfaces/filter/*`（如相关）
- 审计/配置:
  - `infrastructure/audit/*`
  - `infrastructure/config/*`（如新增Metrics）

IMPLEMENTATION CHECKLIST:
1. 统一支付命令命名为 PaymentSucceededCommand，替换所有 PaymentSuccessCommand 引用与导入。
2. 统一退款命令命名为 RefundSucceededCommand，替换所有 RefundSuccessCommand 引用与导入。
3. 在 UserOrderController 明确并固化 submit/cancel/confirm-receipt/after-sale/apply 的幂等键生成与错误语义说明（无接口路径变更）。
4. 在 MerchantOrderController 明确并固化 receive/ship/cancel-approve/cancel-reject/delivery-confirm 的幂等键与错误语义。
5. 在 InternalOrderController 固化 payment/logistics/refund/timeout/auto 的幂等键策略与签名验真位置；确认移除 OrderPaymentCallbackController。
6. 为每个命令建立到 OrderEvent 的事件映射表（文档落地），并在 OrderStateMachineConfig 中核对或补齐 Guard 与 Action 钩子。
7. 为每个 Action 列出持久化字段（四维状态）与出箱事件类型，补齐审计记录写入点。
8. 统一出箱事件 `event_type` 命名与 `event_payload` Schema（含 version/aggregateId/subOrderId/tenantId/operator/occurredAt/data）。
9. 确定 Outbox 分区键（mainOrderId）与去重键（eventId 或 (aggregateId,version,etype)），在发布器中对齐实现。
10. 为用户提交/取消/收货/售后与内部回调定义幂等键算法，并接入 IdempotencyRepository/IdempotencyStorage（成功结果回放）。
11. 在 interfaces/error 增补统一错误码表与 ControllerAdvice 映射（含409冲突、400参数、401/403鉴权、404、422语义、500）。
12. 在应用服务入口写入审计记录（命令名、操作者、TraceId、订单ID、结果），补齐 MDC 透传。
13. 编写用户API单测：提交（幂等回放）、取消（两分支）、确认收货、售后申请（行级/整单）。
14. 编写商家API单测：接单、发货、妥投确认、取消审批/拒绝（含幂等重放）。
15. 编写内部回调单测：支付成功、退款成功、超时取消、自动完成（含乱序/重放）。
16. 编写状态机集成测试：主路径与关键分叉，断言分维状态与主单展示态派生一致；验证Outbox事件写入。
17. 编写出箱事件测试：校验 event_type/ payload/version、分区键、去重策略有效。
18. 校验 `db/main.sql` 是否满足字段与索引；如需幂等/审计表，补充迁移与回滚脚本。
19. 补充 README：API列表、幂等/错误语义、状态流转图、事件契约、指标与报警。
20. 校验多租户/用户上下文从控制器→应用服务→事件载荷的贯穿，缺口处补位。
21. 增加基础监控指标：Outbox滞留/延迟、状态机失败率、幂等冲突率、控制器4xx/5xx；日志字段统一（traceId/correlationId）。
22. 选择性执行订单模块测试，避免其它模块失败干扰（说明构建命令与模块选择方式）。

以上计划与仓库现状对齐：不改变已存在的URL路径与主要DTO命名，仅在命令命名内部统一（请求DTO保持兼容，通过 `toCommand()` 适配）。确认后可“ENTER EXECUTE MODE”逐项落地。
