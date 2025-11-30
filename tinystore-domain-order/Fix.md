[MODE: PLAN]
以下为针对既有偏差的“订单模块修复与完善”技术实施计划（不含任何实现代码片段）。本计划严格列出需要增补/调整的文件、类、方法、字段、事件命名、测试用例与迁移脚本要求，使后续 EXECUTE 阶段无需再做创设性决策。末尾提供原子化可执行 CHECKLIST。

一、总体目标与范围
- 范围：tinystore-domain-order 内控制器、DTO、应用服务、状态机配置、Outbox事件构造与发布、幂等与错误体系、审计与指标、事件载荷多租户透传、文档与数据库迁移。
- 目标：补齐之前 PLAN 与 REVIEW 中识别的缺口（全局错误映射、取消与商家端幂等回放、状态机 Guard/Action 审计与事件规范、审计与指标埋点、多租户透传、测试和文档），删除过时控制器，确保一致性与可观测性。

二、差异点与修复策略概述
1. 全局错误：`GlobalExceptionHandler` 目前缺少 403/404/409/422 映射与统一错误码结构；需新增异常类与规范化错误码枚举/工具。
2. 幂等覆盖：用户取消、商家接单/发货/妥投/取消审核等尚未落地控制器级回放；提交/收货/售后已覆盖。
3. 商家控制器：未接入 `IdempotencyStorage` 结果回放；需与用户控制器策略统一。
4. 状态机：Guard/Action 未完全列出对应事件+审计+Outbox；需补齐文件内动作清单与一致性执行点。
5. 出箱事件：事件类型与载荷字段未统一版本化 schema；需明确 event_type 集合与 payload 基础字段。
6. 审计：缺失统一 `AuditRecorder`；应用服务入口未记录结构化审计。
7. 指标：需要 Micrometer 指标（幂等命中/冲突、状态机失败、事件发布延迟、HTTP错误分布）。
8. 多租户与上下文：事件 payload 中需包含 `tenantId` 与操作主体（userId/operatorId）字段；当前未全面透传。
9. 过时控制器：OrderPaymentCallbackController.java（被注释）需删除以避免混淆。
10. 测试：需新增幂等回放测试、错误码映射测试、状态机分叉测试、事件 payload 结构验证、审计与指标调用验证。
11. DB：若审计与扩展幂等需持久化（当前仅内存 + 可能已有仓储），需迁移脚本与索引。
12. 文档：README 错误码、幂等键、事件 schema、指标、租户透传、删除过时接口说明。

三、详细规格与文件级变更

A. 全局错误与异常体系
- 文件：`interfaces/error/GlobalExceptionHandler.java`
  - 新增处理方法：`handleForbidden(ForbiddenException) -> HTTP 403`、`handleNotFound(ResourceNotFoundException) -> HTTP 404`、`handleConflict(DomainConflictException) -> HTTP 409`、`handleUnprocessable(UnprocessableCommandException) -> HTTP 422`。
  - 统一返回字段：`timestamp`（ISO8601）、`errorCode`（ORDER-前缀）、`message`、`traceId`、`path`。
- 新增异常类：
  - `ForbiddenException`（默认码 ORDER-4030）
  - `ResourceNotFoundException`（ORDER-4040）
  - `DomainConflictException`（ORDER-4090 用于版本冲突/并发）
  - `UnprocessableCommandException`（ORDER-4220 用于命令状态非法）
- 新增错误码枚举：`interfaces/error/OrderErrorCodes.java`（静态常量/枚举：分段：400x 参数与校验、401x 认证、403x 权限、404x 资源、409x 并发冲突、422x 状态非法、500x 系统）。
- 日志：统一在 handler 中记录 `warn`（4xx）或 `error`（5xx）并附带 `traceId`。

B. 幂等与回放扩展
- 用户取消：
  - 幂等键：`cancel:{orderId}:{userId}:{reasonHash}`（reasonHash 使用内容+类型 md5 取前 8 位）。
  - 存储：调用成功后保存简化响应 `{status:"CANCELLED", orderId, cancelAt}`。
- 商家端：
  - 接单：`merchant_accept:{orderId}:{operatorId}`
  - 发货：`ship:{orderId}:{packageNo}`
  - 妥投确认：`delivered_confirm:{orderId}:{packageNo}`
  - 取消审批/拒绝：`merchant_cancel_decision:{orderId}:{operatorId}:{decision}`
  - 结果回放统一使用 `BasicAckVO` 序列化。
- 实现位置：
  - 控制器：`MerchantOrderController` 在每个操作入口：
    - 抽取幂等键（若无自定义头则内部派生），检查 `IdempotencyStorage.exists(key)` → 命中则返回反序列化结果。
    - 执行成功后 `saveResponse(key, serializedAck)`。
  - 用户取消：`UserOrderController.cancelApply()` 同步逻辑。

C. 控制器结构调整
- 注入方式：与用户控制器一致，使用 `ObjectProvider<IdempotencyStorage>` 以兼容测试。
- 删除文件：OrderPaymentCallbackController.java（实际物理删除，以免后续误用）。

D. 应用服务层补齐
- 文件：OrderApplicationService.java
  - 为 `cancelOrder`, `merchantAccept`, `shipOrder`, `confirmDelivered` 等方法增加审计调用与必要异常抛出（`DomainConflictException` / `UnprocessableCommandException`）。
  - 在这些方法返回后不直接负责幂等存储（控制器级），仅抛异常或返回成功对象。
  - 引入：`AuditRecorder`（接口）与 `auditRecorder.record(AuditEntry)`。

E. 状态机 Guard 与 Action 完善
- 文件：`domain/statemachine/OrderStateMachineConfig.java`
  - Guard 新增：
    - `canPay`（状态 in CREATED/UNPAID）
    - `canShip`（状态 in AWAIT_FULFILLMENT 且未 CANCELLED/COMPLETED/FROZEN）
    - `canConfirmDelivery`（状态 in SHIPPED）
    - `canReceive`（状态 in DELIVERED 且未 AFTERSALE_LINE_BLOCK）
    - `canCancel`（状态 in CREATED/UNPAID/PAID && 未 SHIPPED）
  - Action 新增（命名）：
    - `applyPaymentSucceeded`, `applyMerchantAccepted`, `applyGoodsShipped`, `applyGoodsDelivered`, `applyGoodsReceived`, `applyUserCancelled`, `applyTimeoutCancelled`, `applyAutoCompleted`.
  - 每个 Action 内规范：
    - 原子更新对应子单/主单分维状态。
    - 构造 OutboxEvent（调用统一构造器）。
    - 调用 `auditRecorder.record`.
    - 不进行外部调用，不做复杂校验（由服务层提前保证）。
- 事件映射表（文档亦需落地）：
  - PAYMENT_SUCCEEDED -> order.payment.succeeded
  - MERCHANT_ACCEPTED -> order.lifecycle.changed
  - GOODS_SHIPPED -> order.fulfillment.shipped
  - GOODS_DELIVERED -> order.fulfillment.delivered
  - GOODS_RECEIVED -> order.received
  - USER_CANCELLED / SYSTEM_CANCELLED -> order.cancelled
  - PAYMENT_TIMEOUT -> order.cancelled（或保留独立类型 order.payment.timeout 若差异化）
  - AUTO_COMPLETED -> order.lifecycle.changed
  - REFUND_SUCCEEDED -> order.refund.succeeded
  - AFTERSALE_APPLIED -> order.aftersale.applied

F. 出箱事件统一构造
- 文件：`infrastructure/event/outbox/OutboxEventService.java`
  - 新增构造辅助：`buildEvent(String eventType, String aggregateId, @Nullable String subOrderId, String tenantId, String operatorId, Object domainData)`。
  - 事件 payload 结构：
    ```
    {
      "version":"v1",
      "aggregateId":"ORDER-xxx",
      "subOrderId":"SUB-xxx" | null,
      "occurredAt":"2025-..(UTC)",
      "tenantId":"TENANT-1",
      "operatorId":"USER-1|MERCHANT-9|SYSTEM",
      "traceId":"...",
      "data":{ ... domain-specific fields ... }
    }
    ```
  - 分区键：`aggregateId`
  - 去重键：`eventId`（保持现状）+ 约束 (aggregateId + eventType + occurredAt 秒级) 说明文档化。
- 文件：`infrastructure/event/publisher/OutboxEventPublisher.java`
  - 增加指标上报：发布延迟(histogram)、失败计数(counter)。
  - 增加日志字段：`eventType`, `traceId`, `partitionKey`.

G. 审计记录
- 新文件：`application/service/AuditRecorder.java`（接口：`record(AuditEntry entry)`）
- 新文件：`application/service/AuditEntry.java`（字段：`timestamp`, `commandName`, `orderId`, `subOrderId`, `actorId`, `tenantId`, `traceId`, `status`, `errorCode`）
- 新文件：`infrastructure/audit/DefaultAuditRecorder.java`
  - 初期写入日志（INFO），预留扩展持久化方法 `persist(AuditEntry)`。
- 在每个应用服务公开方法入口：
  - 先记录开始（可选），结束时记录结果；失败捕获异常记录 errorCode。

H. 指标
- 新文件：`infrastructure/metrics/OrderMetrics.java`
  - Counter：`order_idempotency_hit_total`, `order_idempotency_miss_total`
  - Counter：`order_state_machine_failure_total`
  - Timer：`order_outbox_publish_latency`
  - Counter：`order_http_error_total`（tag: statusCode）
  - 方法：`incrementIdempotencyHit(key)`, `incrementIdempotencyMiss(key)`, `recordOutboxLatency(duration)`, `incrementHttpError(statusCode)`
- 控制器：在错误 handler 中调用 `incrementHttpError`.
- 出箱发布：调用 `recordOutboxLatency`.
- 状态机失败（捕获异常回退）：调用 `order_state_machine_failure_total`.

I. 多租户与上下文透传
- 假设已有 `TenantContextHolder` 或通过 SecurityPrincipal 提供：
  - 在控制器提取：`tenantId`, `currentUserId` / `operatorId`。
  - DTO `toCommand()` 添加：`tenantId`, `actorId`.
  - 应用服务方法参数命令类补加对应字段（若缺失）。
  - 出箱事件构造传入 `tenantId` 与 `operatorId`.
  - 审计与指标均包含 `tenantId` 维度（标签可后续扩展）。

J. 删除过时控制器
- 删除：`interfaces/rest/OrderPaymentCallbackController.java`（物理删除并在 README 说明统一入口迁移）。
- 确认无引用（grep 验证）。

K. 测试新增与调整
- 新增测试类：
  - `UserOrderCancelIdempotencyTest`：首请求成功、重复请求回放、变更 reason 导致不同 key。
  - `MerchantOrderIdempotencyTest`：接单/发货/妥投重复调用返回相同 ACK。
  - `GlobalExceptionMappingTest`：模拟不同异常抛出断言 HTTP 状态与 errorCode。
  - `OrderStateMachineForkTest`：支付->发货->取消（非法）断言 422；支付->取消 成功；发货->收货 完成。
  - `OutboxEventPayloadSchemaTest`：断言 payload 包含 version/aggregateId/tenantId/operatorId/data。
  - `AuditRecorderInvocationTest`：使用 spy/Mock 验证 record 调用次数与参数。
  - `MetricsEmissionTest`：注册简单 `MeterRegistry` 验证 Counter / Timer 变化。
- 调整现有测试：在新增幂等回放处补 assert；避免破坏已通过测试。
- Maven 仅执行该模块：`mvn -q -pl tinystore-domain-order -am test`。

L. 数据库迁移（如需要持久化审计/幂等）
- 新增表（仅当决定持久化）：
  - `order_audit_log`：`id`(PK), `order_id`, `sub_order_id`, `command`, `actor_id`, `tenant_id`, `trace_id`, `status`, `error_code`, `created_at`（索引：`idx_order_audit_order_id`, `idx_order_audit_trace_id`）。
  - `order_idempotent_result`（若现有仓储不足）：`idempotent_key`(PK), `result_json`, `created_at`, `expires_at`（索引：`idx_idemp_expires`）。
- 脚本位置：`db/main.sql` 或新增 `db/migration/Vxx__order_audit.sql` 说明；README 标注执行顺序与回滚（DROP TABLE ...）。

M. 文档更新
- README 新增章节：
  - 幂等键策略表：字段/示例
  - 错误码与 HTTP 状态映射
  - 状态机事件表（命令→事件→event_type）
  - Outbox 事件 payload schema 与示例
  - 指标列表与含义
  - 迁移说明（若新增表）
  - 删除的旧支付回调控制器说明

N. 日志与 MDC
- 在控制器入口补充：`MDC.put("tenantId", tenantId)`, `MDC.put("actorId", userId/operatorId)`.
- Action/Outbox 发布时读取 `traceId/tenantId/actorId` 注入日志。
- 退出清理：`MDC.clear()`（在过滤器或 finally 中）。

四、命令类与 DTO 必要字段补充
- 若命令缺失：确认添加 `tenantId`, `actorId`（统一使用 String）。
- 不修改对外请求 DTO 命名，仅内部 `toCommand()` 扩展参数。
- 若新增字段在命令内部需保证 builder/setter 接口存在。

五、命名与一致性要求
- 事件类型蛇形加领域分段：`order.<domain>.<action>`；生命周期泛型使用 `order.lifecycle.changed`。
- 错误码统一大写 `ORDER-XXXX` 四位数字分段；避免与其他模块冲突。
- Metrics 前缀：`order_`，无大写。
- 审计日志行统一 JSON（`AuditEntry` 转 JSON 字符串）以便后续采集。

六、执行约束
- 不改动已有已稳定的成功逻辑除非为接入审计/指标/幂等必要穿插。
- 不重写已通过测试，只最小增量补测试。
- 删除过时文件后需 grep 确认无残余引用。

七、风险与回退
- 新增异常类可能影响未捕获场景：需测试覆盖所有新增映射。
- 出箱事件 schema 变更需确认消费者兼容（保留 version 字段可渐进迁移）。
- 若暂不落库审计/幂等结果，迁移脚本项可延后（Checklist 中单列可选）。

IMPLEMENTATION CHECKLIST:
1. 新增异常类文件：`ForbiddenException.java`, `ResourceNotFoundException.java`, `DomainConflictException.java`, `UnprocessableCommandException.java` 于 `interfaces/error/`。
2. 新增错误码枚举/常量文件：`OrderErrorCodes.java`（划分各段并定义常量）。
3. 扩展 `GlobalExceptionHandler.java`：添加 403/404/409/422 映射方法，统一响应结构与日志记录。
4. 在 `UserOrderController.cancelApply()` 中实现取消幂等键生成与回放逻辑（使用 IdempotencyStorage）。
5. 在 `UserOrderController.cancelApply()` 成功路径保存取消结果 JSON（包含 status/orderId/cancelAt）。
6. 在 `MerchantOrderController` 为接单方法添加幂等键生成与回放逻辑。
7. 在 `MerchantOrderController` 为发货方法添加幂等键生成与回放逻辑。
8. 在 `MerchantOrderController` 为妥投确认方法添加幂等键生成与回放逻辑。
9. 在 `MerchantOrderController` 为取消审批与取消拒绝方法添加统一幂等键与回放逻辑。
10. 统一 `MerchantOrderController` 构造中注入 `ObjectProvider<IdempotencyStorage>` 并实现无存储降级。
11. 删除文件 `interfaces/rest/OrderPaymentCallbackController.java` 并确保无引用（grep 验证）。
12. 新增接口 `AuditRecorder.java` 与实体 `AuditEntry.java` 于 `application/service/`。
13. 新增实现 `DefaultAuditRecorder.java` 于 `infrastructure/audit/`（日志方式）。
14. 在 `OrderApplicationService` 所有对外公开命令处理方法入口与出口调用 `auditRecorder.record(...)`（成功与失败）。
15. 新增指标封装类 `OrderMetrics.java` 于 `infrastructure/metrics/`。
16. 在 `GlobalExceptionHandler` 中对 4xx/5xx 调用 `OrderMetrics.incrementHttpError(statusCode)`。
17. 在幂等回放命中路径（用户与商家控制器）调用 `OrderMetrics.incrementIdempotencyHit(key)`；未命中调用 `incrementIdempotencyMiss(key)`。
18. 新增统一 Outbox 事件构造辅助到 `OutboxEventService.buildEvent(...)`。
19. 修改所有 Action（状态机）调用新构造辅助以生成规范化 payload（version/aggregateId/...）。
20. 在 `OrderStateMachineConfig` 中添加 Guard 方法：`canPay`,`canShip`,`canConfirmDelivery`,`canReceive`,`canCancel` 并关联相应转换。
21. 在 `OrderStateMachineConfig` 中添加 Action 方法：`applyPaymentSucceeded`,`applyMerchantAccepted`,`applyGoodsShipped`,`applyGoodsDelivered`,`applyGoodsReceived`,`applyUserCancelled`,`applyTimeoutCancelled`,`applyAutoCompleted`。
22. 为 REFUND_SUCCEEDED 与 AFTERSALE_APPLIED 事件补齐出箱 Action（若缺失）保持 schema 一致。
23. 为每个 Action 添加审计调用（操作完成后 `auditRecorder.record`）。
24. 为每个 Action 添加错误捕获并在失败时记录 `OrderMetrics.order_state_machine_failure_total`。
25. 定义事件类型常量列表于 `domain/event/`（或集中在 `OrderEventTypeConstants.java`）。
26. 修改 `PaymentSuccessRequest.toCommand()` 与其他请求 DTO `toCommand()` 增加 `tenantId`、`actorId` 来源（控制器注入）。
27. 在控制器中提取 `tenantId` 与 `actorId` 并放入 MDC（`tenantId`,`actorId`）。
28. 将 `tenantId` 与 `actorId` 添加到所有命令类（若不存在）并在构造时填充。
29. 在出箱事件 payload 中注入 `tenantId` 与 `operatorId` 字段。
30. 编写测试 `UserOrderCancelIdempotencyTest` 覆盖首次与重复取消及不同 reason 变化。
31. 编写测试 `MerchantOrderIdempotencyTest` 覆盖接单、发货、妥投重复调用回放。
32. 编写测试 `GlobalExceptionMappingTest` 验证 403/404/409/422 以及 errorCode 格式。
33. 编写测试 `OrderStateMachineForkTest` 验证非法跃迁抛 422 与合法分支。
34. 编写测试 `OutboxEventPayloadSchemaTest` 验证 payload 结构完整性与 version= v1。
35. 编写测试 `AuditRecorderInvocationTest` 使用 mock/spies 验证调用次数与关键字段。
36. 编写测试 `MetricsEmissionTest` 使用临时 `SimpleMeterRegistry` 验证幂等命中与错误计数。
37. 更新或新增 README 章节：幂等键策略、错误码映射表、状态机事件表、Outbox schema、指标列表、租户透传说明、删除控制器说明。
38. 在 README 中添加一个事件 payload 示例 JSON 与幂等键示例表格。
39. 若决定持久化审计：新增迁移脚本 `db/migration/Vxx__create_order_audit_log.sql`。
40. 若扩展幂等持久层：迁移脚本 `Vxx__create_order_idempotent_result.sql`。
41. 在 `db/main.sql` 或迁移脚本中加入索引语句（审计：`idx_order_audit_order_id`,`idx_order_audit_trace_id`；幂等：`idx_idemp_expires`）。
42. grep 验证删除控制器后无残留引用（`grep -R "OrderPaymentCallbackController"`）。
43. 执行模块测试：`mvn -q -pl tinystore-domain-order -am test` 并确认所有新增测试通过。
44. 如出现测试失败（非计划内逻辑差异）记录具体错误并回到 PLAN 修正（不在 EXECUTE 自行扩展计划）。
45. 最终核对：事件类型常量、错误码常量、README 与测试断言中的命名完全一致（大小写与分隔）。
46. 确认 MDC 清理策略（在控制器 finally 或统一过滤器中调用 `MDC.clear()`）。
47. 记录在 TODO 列表中已完成与剩余项状态（更新任务管理）。
48. 生成一份简短 CHANGELOG（模块内新增/删除/修改的类与风险）并放入 `tinystore-domain-order/CHANGELOG_ORDER_ENHANCEMENTS.md`。
49. 标注已废弃命令或类（若有）在其文件头加 `@Deprecated`（仅需如仍临时保留，若无则跳过）。
50. 复核所有新增公共类命名避免与其它模块冲突（确保前缀或路径唯一）。

请选择是否批准该 PLAN；若批准请发出 “ENTER EXECUTE MODE” 指令以进入实施阶段。  - 分区键：`aggregateId`
  - 去重键：`eventId`（保持现状）+ 约束 (aggregateId + eventType + occurredAt 秒级) 说明文档化。
- 文件：`infrastructure/event/publisher/OutboxEventPublisher.java`
  - 增加指标上报：发布延迟(histogram)、失败计数(counter)。
  - 增加日志字段：`eventType`, `traceId`, `partitionKey`.

G. 审计记录
- 新文件：`application/service/AuditRecorder.java`（接口：`record(AuditEntry entry)`）
- 新文件：`application/service/AuditEntry.java`（字段：`timestamp`, `commandName`, `orderId`, `subOrderId`, `actorId`, `tenantId`, `traceId`, `status`, `errorCode`）
- 新文件：`infrastructure/audit/DefaultAuditRecorder.java`
  - 初期写入日志（INFO），预留扩展持久化方法 `persist(AuditEntry)`。
- 在每个应用服务公开方法入口：
  - 先记录开始（可选），结束时记录结果；失败捕获异常记录 errorCode。

H. 指标
- 新文件：`infrastructure/metrics/OrderMetrics.java`
  - Counter：`order_idempotency_hit_total`, `order_idempotency_miss_total`
  - Counter：`order_state_machine_failure_total`
  - Timer：`order_outbox_publish_latency`
  - Counter：`order_http_error_total`（tag: statusCode）
  - 方法：`incrementIdempotencyHit(key)`, `incrementIdempotencyMiss(key)`, `recordOutboxLatency(duration)`, `incrementHttpError(statusCode)`
- 控制器：在错误 handler 中调用 `incrementHttpError`.
- 出箱发布：调用 `recordOutboxLatency`.
- 状态机失败（捕获异常回退）：调用 `order_state_machine_failure_total`.

I. 多租户与上下文透传
- 假设已有 `TenantContextHolder` 或通过 SecurityPrincipal 提供：
  - 在控制器提取：`tenantId`, `currentUserId` / `operatorId`。
  - DTO `toCommand()` 添加：`tenantId`, `actorId`.
  - 应用服务方法参数命令类补加对应字段（若缺失）。
  - 出箱事件构造传入 `tenantId` 与 `operatorId`.
  - 审计与指标均包含 `tenantId` 维度（标签可后续扩展）。

J. 删除过时控制器
- 删除：`interfaces/rest/OrderPaymentCallbackController.java`（物理删除并在 README 说明统一入口迁移）。
- 确认无引用（grep 验证）。

K. 测试新增与调整
- 新增测试类：
  - `UserOrderCancelIdempotencyTest`：首请求成功、重复请求回放、变更 reason 导致不同 key。
  - `MerchantOrderIdempotencyTest`：接单/发货/妥投重复调用返回相同 ACK。
  - `GlobalExceptionMappingTest`：模拟不同异常抛出断言 HTTP 状态与 errorCode。
  - `OrderStateMachineForkTest`：支付->发货->取消（非法）断言 422；支付->取消 成功；发货->收货 完成。
  - `OutboxEventPayloadSchemaTest`：断言 payload 包含 version/aggregateId/tenantId/operatorId/data。
  - `AuditRecorderInvocationTest`：使用 spy/Mock 验证 record 调用次数与参数。
  - `MetricsEmissionTest`：注册简单 `MeterRegistry` 验证 Counter / Timer 变化。
- 调整现有测试：在新增幂等回放处补 assert；避免破坏已通过测试。
- Maven 仅执行该模块：`mvn -q -pl tinystore-domain-order -am test`。

L. 数据库迁移（如需要持久化审计/幂等）
- 新增表（仅当决定持久化）：
  - `order_audit_log`：`id`(PK), `order_id`, `sub_order_id`, `command`, `actor_id`, `tenant_id`, `trace_id`, `status`, `error_code`, `created_at`（索引：`idx_order_audit_order_id`, `idx_order_audit_trace_id`）。
  - `order_idempotent_result`（若现有仓储不足）：`idempotent_key`(PK), `result_json`, `created_at`, `expires_at`（索引：`idx_idemp_expires`）。
- 脚本位置：`db/main.sql` 或新增 `db/migration/Vxx__order_audit.sql` 说明；README 标注执行顺序与回滚（DROP TABLE ...）。

M. 文档更新
- README 新增章节：
  - 幂等键策略表：字段/示例
  - 错误码与 HTTP 状态映射
  - 状态机事件表（命令→事件→event_type）
  - Outbox 事件 payload schema 与示例
  - 指标列表与含义
  - 迁移说明（若新增表）
  - 删除的旧支付回调控制器说明

N. 日志与 MDC
- 在控制器入口补充：`MDC.put("tenantId", tenantId)`, `MDC.put("actorId", userId/operatorId)`.
- Action/Outbox 发布时读取 `traceId/tenantId/actorId` 注入日志。
- 退出清理：`MDC.clear()`（在过滤器或 finally 中）。

四、命令类与 DTO 必要字段补充
- 若命令缺失：确认添加 `tenantId`, `actorId`（统一使用 String）。
- 不修改对外请求 DTO 命名，仅内部 `toCommand()` 扩展参数。
- 若新增字段在命令内部需保证 builder/setter 接口存在。

五、命名与一致性要求
- 事件类型蛇形加领域分段：`order.<domain>.<action>`；生命周期泛型使用 `order.lifecycle.changed`。
- 错误码统一大写 `ORDER-XXXX` 四位数字分段；避免与其他模块冲突。
- Metrics 前缀：`order_`，无大写。
- 审计日志行统一 JSON（`AuditEntry` 转 JSON 字符串）以便后续采集。

六、执行约束
- 不改动已有已稳定的成功逻辑除非为接入审计/指标/幂等必要穿插。
- 不重写已通过测试，只最小增量补测试。
- 删除过时文件后需 grep 确认无残余引用。

七、风险与回退
- 新增异常类可能影响未捕获场景：需测试覆盖所有新增映射。
- 出箱事件 schema 变更需确认消费者兼容（保留 version 字段可渐进迁移）。
- 若暂不落库审计/幂等结果，迁移脚本项可延后（Checklist 中单列可选）。

IMPLEMENTATION CHECKLIST:
1. 新增异常类文件：`ForbiddenException.java`, `ResourceNotFoundException.java`, `DomainConflictException.java`, `UnprocessableCommandException.java` 于 `interfaces/error/`。
2. 新增错误码枚举/常量文件：`OrderErrorCodes.java`（划分各段并定义常量）。
3. 扩展 `GlobalExceptionHandler.java`：添加 403/404/409/422 映射方法，统一响应结构与日志记录。
4. 在 `UserOrderController.cancelApply()` 中实现取消幂等键生成与回放逻辑（使用 IdempotencyStorage）。
5. 在 `UserOrderController.cancelApply()` 成功路径保存取消结果 JSON（包含 status/orderId/cancelAt）。
6. 在 `MerchantOrderController` 为接单方法添加幂等键生成与回放逻辑。
7. 在 `MerchantOrderController` 为发货方法添加幂等键生成与回放逻辑。
8. 在 `MerchantOrderController` 为妥投确认方法添加幂等键生成与回放逻辑。
9. 在 `MerchantOrderController` 为取消审批与取消拒绝方法添加统一幂等键与回放逻辑。
10. 统一 `MerchantOrderController` 构造中注入 `ObjectProvider<IdempotencyStorage>` 并实现无存储降级。
11. 删除文件 `interfaces/rest/OrderPaymentCallbackController.java` 并确保无引用（grep 验证）。
12. 新增接口 `AuditRecorder.java` 与实体 `AuditEntry.java` 于 `application/service/`。
13. 新增实现 `DefaultAuditRecorder.java` 于 `infrastructure/audit/`（日志方式）。
14. 在 `OrderApplicationService` 所有对外公开命令处理方法入口与出口调用 `auditRecorder.record(...)`（成功与失败）。
15. 新增指标封装类 `OrderMetrics.java` 于 `infrastructure/metrics/`。
16. 在 `GlobalExceptionHandler` 中对 4xx/5xx 调用 `OrderMetrics.incrementHttpError(statusCode)`。
17. 在幂等回放命中路径（用户与商家控制器）调用 `OrderMetrics.incrementIdempotencyHit(key)`；未命中调用 `incrementIdempotencyMiss(key)`。
18. 新增统一 Outbox 事件构造辅助到 `OutboxEventService.buildEvent(...)`。
19. 修改所有 Action（状态机）调用新构造辅助以生成规范化 payload（version/aggregateId/...）。
20. 在 `OrderStateMachineConfig` 中添加 Guard 方法：`canPay`,`canShip`,`canConfirmDelivery`,`canReceive`,`canCancel` 并关联相应转换。
21. 在 `OrderStateMachineConfig` 中添加 Action 方法：`applyPaymentSucceeded`,`applyMerchantAccepted`,`applyGoodsShipped`,`applyGoodsDelivered`,`applyGoodsReceived`,`applyUserCancelled`,`applyTimeoutCancelled`,`applyAutoCompleted`。
22. 为 REFUND_SUCCEEDED 与 AFTERSALE_APPLIED 事件补齐出箱 Action（若缺失）保持 schema 一致。
23. 为每个 Action 添加审计调用（操作完成后 `auditRecorder.record`）。
24. 为每个 Action 添加错误捕获并在失败时记录 `OrderMetrics.order_state_machine_failure_total`。
25. 定义事件类型常量列表于 `domain/event/`（或集中在 `OrderEventTypeConstants.java`）。
26. 修改 `PaymentSuccessRequest.toCommand()` 与其他请求 DTO `toCommand()` 增加 `tenantId`、`actorId` 来源（控制器注入）。
27. 在控制器中提取 `tenantId` 与 `actorId` 并放入 MDC（`tenantId`,`actorId`）。
28. 将 `tenantId` 与 `actorId` 添加到所有命令类（若不存在）并在构造时填充。
29. 在出箱事件 payload 中注入 `tenantId` 与 `operatorId` 字段。
30. 编写测试 `UserOrderCancelIdempotencyTest` 覆盖首次与重复取消及不同 reason 变化。
31. 编写测试 `MerchantOrderIdempotencyTest` 覆盖接单、发货、妥投重复调用回放。
32. 编写测试 `GlobalExceptionMappingTest` 验证 403/404/409/422 以及 errorCode 格式。
33. 编写测试 `OrderStateMachineForkTest` 验证非法跃迁抛 422 与合法分支。
34. 编写测试 `OutboxEventPayloadSchemaTest` 验证 payload 结构完整性与 version= v1。
35. 编写测试 `AuditRecorderInvocationTest` 使用 mock/spies 验证调用次数与关键字段。
36. 编写测试 `MetricsEmissionTest` 使用临时 `SimpleMeterRegistry` 验证幂等命中与错误计数。
37. 更新或新增 README 章节：幂等键策略、错误码映射表、状态机事件表、Outbox schema、指标列表、租户透传说明、删除控制器说明。
38. 在 README 中添加一个事件 payload 示例 JSON 与幂等键示例表格。
39. 若决定持久化审计：新增迁移脚本 `db/migration/Vxx__create_order_audit_log.sql`。
40. 若扩展幂等持久层：迁移脚本 `Vxx__create_order_idempotent_result.sql`。
41. 在 `db/main.sql` 或迁移脚本中加入索引语句（审计：`idx_order_audit_order_id`,`idx_order_audit_trace_id`；幂等：`idx_idemp_expires`）。
42. grep 验证删除控制器后无残留引用（`grep -R "OrderPaymentCallbackController"`）。
43. 执行模块测试：`mvn -q -pl tinystore-domain-order -am test` 并确认所有新增测试通过。
44. 如出现测试失败（非计划内逻辑差异）记录具体错误并回到 PLAN 修正（不在 EXECUTE 自行扩展计划）。
45. 最终核对：事件类型常量、错误码常量、README 与测试断言中的命名完全一致（大小写与分隔）。
46. 确认 MDC 清理策略（在控制器 finally 或统一过滤器中调用 `MDC.clear()`）。
47. 记录在 TODO 列表中已完成与剩余项状态（更新任务管理）。
48. 生成一份简短 CHANGELOG（模块内新增/删除/修改的类与风险）并放入 `tinystore-domain-order/CHANGELOG_ORDER_ENHANCEMENTS.md`。
49. 标注已废弃命令或类（若有）在其文件头加 `@Deprecated`（仅需如仍临时保留，若无则跳过）。
50. 复核所有新增公共类命名避免与其它模块冲突（确保前缀或路径唯一）。

请选择是否批准该 PLAN；若批准请发出 “ENTER EXECUTE MODE” 指令以进入实施阶段。