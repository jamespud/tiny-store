[MODE: PLAN]
以下为基于审阅结论的“详细修复计划”，只包含规格与改动清单，不含任何实现代码或样例。所有路径均以 tinystore-domain-order 模块为根，保证实施无需创设性决策。

一、目标与范围
- 目标: 补齐最初 TODO 中未完成项，涵盖控制层幂等/签名、统一错误语义、提交结果回放存储、状态机 Action 原子化持久化与出箱、审计与指标、售后/取消审批用例、文档与 SQL 校验。
- 范围: 
  - 控制器与 DTO 层（User/Merchant/Internal）
  - 应用服务（OrderApplicationService 及命令转换）
  - 状态机 Guard/Action 增强
  - 出箱发布与灰度开关配置化（已具备，完善文档与测试）
  - 幂等存储抽象与 TTL
  - 全局异常映射
  - 审计与指标打点
  - 测试与文档、SQL 迁移脚本

二、控制层幂等与签名验真固化
- 文件:
  - `src/main/java/.../interfaces/rest/UserOrderController.java`
  - `src/main/java/.../interfaces/rest/MerchantOrderController.java`
  - `src/main/java/.../interfaces/rest/InternalOrderController.java`
  - `src/main/java/.../interfaces/util/IdempotencyHelper.java`（若已存在则扩展）
  - 新增接口与默认实现:
    - `src/main/java/.../infrastructure/acl/SignatureVerifier.java`（接口）
    - `src/main/java/.../infrastructure/acl/NoopSignatureVerifier.java`（默认空实现，可配置替换）
- 约定:
  - 从请求头读取幂等键 `X-Idempotency-Key`；不存在且为“必须幂等”的接口时返回 400。
  - Internal 回调接口强制验签：读取 `X-Signature` 与 `X-Timestamp`；若 `SignatureVerifier` 返回失败，则 401。
  - 幂等键算法（无头场景本地生成，若计划要求）:
    - 用户提交: `submit:{userId}:{requestDigest}`（requestDigest=规范化请求体哈希）
    - 用户取消: `user-cancel:{orderId}:{userId}:{reason}`
    - 用户收货: `user-receive:{orderId}:{userId}`
    - 售后申请: `aftersale:{orderId}:{lineId-or-ALL}:{type}:{tsBucket}`
    - 商家接单: `merchant-accept:{orderId}:{operatorId}`
    - 商家发货: `ship:{orderId}:{packageNo}`
    - 商家妥投确认: `delivered:{orderId}:{packageNo-or-deliveredAt}`
    - 支付成功: `pay:{orderNo-or-tradeNo}:{transactionId}:{amount}`
    - 退款成功: `refund:{orderId}:{refundTransactionId}`
    - 未支付超时: `unpaid-timeout:{orderId}:{scheduleId}`
    - 自动完成: `auto-complete:{orderId}:{ruleId-or-graceDays}:{tsBucket}`
    - Await-Fulfillment: `await-fulfillment:{orderId}:{eventId-or-tsBucket}`
- 控制器改动点:
  - 校验/提取幂等键与签名缺省处理；调用应用服务前统一通过 `IdempotencyHelper`/`IdempotencyStorage`（见下一节）进行“命中回放”短路。
  - 统一日志字段: `actorType/actorId/event/orderId/traceId/idempotent(isReplayed)`。

三、提交成功结果回放存储（IdempotencyStorage）
- 新增:
  - `src/main/java/.../infrastructure/idempotency/IdempotencyStorage.java`（接口: putSuccess/getSuccess/exists/evict）
  - `src/main/java/.../infrastructure/idempotency/RedisIdempotencyStorage.java`（生产实现，TTL 默认 7 天，可通过 `order.idempotency.ttl-days` 配置）
  - `src/main/java/.../infrastructure/idempotency/InMemoryIdempotencyStorage.java`（测试用）
- 适配:
  - `OrderApplicationService.submitOrder(...)`、取消/收货/售后等路径在成功后写入回放结果；重复提交命中则直接返回历史结果。
  - `InternalOrderController` 侧为“回调”类操作仅记录幂等命中，不回放业务结果体（返回一致的 ACK 即可）。

四、全局异常与 HTTP 语义映射细化
- 文件:
  - GlobalExceptionHandler.java（在现有基础上扩展）
  - 新增（如需）:
    - `src/main/java/.../domain/exception/OrderErrorCode.java`（业务错误码枚举/常量，不写实现代码）
- 映射策略:
  - 400: 参数错误、幂等键缺失/格式错误、签名缺失/格式错误、业务校验失败（如价格变更/库存不足/优惠券无效）
  - 401: 验签失败/未认证
  - 403: 鉴权失败（非订单属主/非商家操作人）
  - 404: 资源不存在（订单/子单等）
  - 409: 并发冲突（乐观锁冲突/重复状态跃迁）
  - 422: 语义错误（在当前状态不允许的操作）
  - 500: 未捕获异常
- 错误码规范: `ORDER-4xxx/401x/403x/404x/409x/422x/500x` 分段；将现有 `OrderDomainException` 的 `errorCode` 规范化为 `ORDER-xxxx`。

五、状态机 Action 原子持久化与出箱
- 文件:
  - `src/main/java/.../domain/statemachine/action/*`（已存在类增强）
  - `src/main/java/.../domain/model/*` 与 `.../infrastructure/persistence/repository/*`（保持现仓库结构）
- 要求:
  - 在以下 Action 内完成“幂等检查 → 最小必要字段更新 → 状态审计 → 写出箱事件（标准名）”的原子化操作：
    - `OnPaymentSucceededAction`: 更新支付维度，出箱 `order.payment.succeeded`
    - `OnMerchantAcceptedAction`: 更新接单维度，出箱 `order.lifecycle.changed`
    - `OnFulfillmentStartedAction`: 更新履约维度（发货），出箱 `order.fulfillment.shipped`
    - `OnGoodsReceivedAction`: 更新核心流转为 COMPLETED，出箱 `order.received`/`order.completed`（按既有定义）
    - `OnAutoReceiveTimeoutAction`: 同上但标注来源为 SYSTEM，出箱 `order.received`
    - `OnCancelledAction`: 更新核心流转为 CANCELLED，出箱 `order.cancelled`
  - 持久化前校验 Guard 否决状态，避免重复写入；写入前后记录 `OrderStatusAuditRepository`。
  - 出箱载荷使用现有 `OutboxEventService.buildEnvelopePayload` 生成；事件类型经 `OutboxEventPublisher` 标准化映射。

六、审计与指标
- 文件:
  - `src/main/java/.../infrastructure/audit/*`（新增 `AuditRecorder` 接口与默认实现）
  - `src/main/java/.../infrastructure/metrics/OutboxMetrics.java`（已存在引用，若文件缺失则新增接口与默认无操作实现）
- 要求:
  - 应用服务入口调用 `AuditRecorder.record(cmd, actor, traceId, orderId, idempotent)`。
  - 指标补充：
    - 幂等命中计数/比率（按操作分类）
    - 状态机拒绝计数
    - 控制器 4xx/5xx 计数
    - 出箱滞留（pending 数）与发布延迟（createdAt→发送时间差）

七、鉴权/租户校验
- 文件:
  - `src/main/java/.../infrastructure/acl/UserIdProvider.java`（如已存在则复用）
  - `src/main/java/.../infrastructure/acl/AuthorizationService.java`（接口+默认实现）
- 要求:
  - 用户接口校验订单归属（userId==订单用户）失败 403。
  - 商家接口校验操作人归属商家并允许操作；失败 403。
  - `TenantContext` 的 `tenantId/userId` 贯穿入出箱信封 `operator/tenantId`。

八、配置与灰度
- 配置键:
  - `order.outbox.map-event-type`（已实现，默认 true；文档化）
  - `order.idempotency.ttl-days`（默认 7）
  - `order.signature.enabled`（默认 false）
- 文档: README 中给出上述开关语义与回滚策略（关闭事件名映射时的 Topic 与 `eventType` 回退说明）。

九、测试补齐
- 新增/扩展测试文件:
  - 用户侧: `src/test/java/.../interfaces/rest/UserOrderControllerTest.java`
    - `submit_replay_returns_previous_result`
    - `cancel_conflict_409_when_illegal_state`
    - `confirm_receipt_idempotent_replay_ok`
    - `aftersale_apply_idempotent_ok`
    - `authz_enforced_for_foreign_user_403`
  - 商家侧: `MerchantOrderControllerTest.java`
    - `accept_idempotent_ok`
    - `ship_idempotent_ok`
    - `cancel_approve_reject_idempotent_ok`
    - `authz_enforced_for_foreign_operator_403`
  - 内部回调: InternalOrderControllerTest.java
    - `payment_success_signature_required_401`
    - `refund_success_signature_required_401`
    - `timeout_unpaid_idempotent_ok`
    - `auto_complete_idempotent_ok`
  - 状态机集成: `domain/statemachine/OrderStateMachineConfigTest.java`
    - `after_paid_user_cancel_refund_branch`
    - `after_shipped_refund_branch_guarded`
  - 出箱契约: 现有 Outbox 测试基础上增加
    - `publisher_eventType_gray_switch_off_fallback`
    - `envelope_contains_tenant_operator_trace`
- 选择性运行命令（README 已含）：模块级与按测试名过滤。

十、文档
- README.md 增补:
  - 幂等键策略表、签名/鉴权、错误语义（HTTP→错误码）、状态流转与事件契约、指标面板建议。
- 新增（如缺失）:
  - `docs/outbox-event-schema.md`：事件字段含义与样例（文本说明即可，实施时不写代码）。

十一、数据库迁移与索引
- `src/main/resources/db/migration/`（新增/校验）
  - 为 `order_outbox_event` 增加索引: `(status, created_at)`, `(event_type)`, `(order_no)`。
  - 新增 `order_idempotency` 表（如仓库无）：`key(pk), value(json), created_at, expire_at`；或确认复用现有 `OrderIdempotencyRepository` 的实体列对齐。
  - 审计表（如选用 DB 落库）：`order_audit`（order_no, actor_type, actor_id, action, trace_id, created_at, details）可后续追加；本批仅对齐说明与可选迁移。

实施限制与兼容性
- 不修改已对外暴露的 API 路径与 DTO 字段；新增行仅为内部增强（幂等/签名/鉴权）。
- 默认配置保持当前行为（签名关闭、事件名映射开启）；灰度可控。

IMPLEMENTATION CHECKLIST:
1. 在 `interfaces/util/IdempotencyHelper` 新增“必须幂等”校验与键提取方法；为空时抛业务异常（400）。
2. 在 `infrastructure/idempotency` 新增 `IdempotencyStorage` 接口、`RedisIdempotencyStorage` 与 `InMemoryIdempotencyStorage`；读取 `order.idempotency.ttl-days`。
3. 在 `interfaces/rest/InternalOrderController` 引入 `SignatureVerifier`（从 `infrastructure/acl` 注入）；读取 `X-Signature`、`X-Timestamp`；`order.signature.enabled=true` 时验签失败返回 401。
4. 在 `interfaces/rest/UserOrderController` 为 submit/cancel/confirm-receipt/after-sale/apply 接口接入幂等键生成与 `IdempotencyStorage` 命中回放；缺头时使用定义的本地键算法。
5. 在 `interfaces/rest/MerchantOrderController` 为 receive/ship/delivery-confirm/cancel-approve/reject 接入幂等键与 `IdempotencyStorage` 命中回放。
6. 在 `OrderApplicationService.submitOrder/confirmOrder/confirmDelivered/timeoutCancel/autoComplete/...` 完成成功结果写入 `IdempotencyStorage`；重入命中直接回放。
7. 在 `interfaces/error/GlobalExceptionHandler` 扩展 HTTP→错误码映射：400/401/403/404/409/422/500，并将 `OrderDomainException.errorCode` 规范化为 `ORDER-xxxx`。
8. 在 `domain/statemachine/action/*` 的六个 Action 中补齐“幂等检查→最小字段更新→审计→出箱事件”原子逻辑；引用 `OrderStatusAuditRepository`。
9. 在 `infrastructure/audit` 新增 `AuditRecorder` 接口与默认实现；在应用服务入口调用记录。
10. 在 `infrastructure/metrics` 新增或完善 `OutboxMetrics` 接口/实现，补齐幂等命中/拒绝/4xx/5xx/出箱延迟等指标上报点。
11. 在 `infrastructure/acl` 新增 `AuthorizationService` 并在 User/Merchant 控制器调用校验归属；失败返回 403。
12. 在 `OutboxEventPublisher` 已有的 `order.outbox.map-event-type` 基础上，补充 README 配置说明与测试覆盖灰度关闭回退情形（已有一条，增加一条覆盖非标准名 rawType）。
13. 在 `interfaces/rest/UserOrderControllerTest` 新增提交回放、取消 409、收货重放、售后申请重放、403 鉴权用例。
14. 在 `interfaces/rest/MerchantOrderControllerTest` 新增接单/发货/取消审批幂等重放与 403 鉴权用例。
15. 在 `interfaces/rest/InternalOrderControllerTest` 新增支付/退款 401 验签用例、超时/自动完成重放用例。
16. 在 `domain/statemachine/OrderStateMachineConfigTest` 新增“支付后取消→退款分支”“发货后退款分支”集成用例，断言 Guard/Action 与出箱。
17. 在 `infrastructure/event/publisher/OutboxEventPublisherTest` 增加灰度开关关闭时的 rawType 与 `tinystore.order.general` Topic 断言。
18. 更新 README.md：幂等键策略、签名/鉴权、错误语义映射、状态图与事件契约、指标建议、配置开关说明。
19. 在 `src/main/resources/db/migration` 新增索引/表迁移脚本（`order_outbox_event` 索引、`order_idempotency` 表，如无）；校验现有表结构一致性。
20. 运行仅订单模块测试并修复：`mvn -q -pl tinystore-domain-order -am test`。
21. 按需补充针对新增 Advice/Storage/ACL 的单元测试，保证分支覆盖率（>80% 针对新增类）。
22. 提交变更并记录变更说明（灰度开关默认开启、签名默认关闭、可通过配置启用验签）。
23. 可选：新增定时任务清理 `IdempotencyStorage` 过期键（若后端存储不自动过期），并加入指标。

说明:
- 本计划在不改变对外 API 契约的前提下增强内部稳健性；如需对外变更（例如强制验签），通过环境配置逐步灰度开启。