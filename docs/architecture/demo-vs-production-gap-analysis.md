# Tiny Store 与淘宝/京东的差距分析：什么决定了它还是个 Demo

> 分析日期：2026-08-23
> 范围：`/home/spud/.codex/worktrees/286a/tiny-store`（当前 worktree，纯后端 DDD 微服务）
> 方法：源码证据 + 数据库迁移（Flyway）逐表核对 + 配置文件核查 + 测试深度量化。全程只读，未改动任何业务代码。

## 0. 结论（一句话）

**Tiny Store 是一台“生产级交易发动机，装在 demo 的驾驶舱里”。**

它的交易一致性内核（事务性 Outbox、支付 Saga、库存预扣状态机、版本 CAS 对账、分布式幂等、补偿调度、四层测试与压测方法论）已接近教科书级；但**发动机外围的每一层——资金真实性与验签、信任边界、生产配置、商家供给、物流/评价/结算数据、运维可观测性——要么为空、要么是模拟、要么是硬编码的开发默认值**。只要这些边界还是开着的，它就是一个演示引擎。

真正让它还是 demo 的，**不是交易链路写得不够严谨，而是交易链路四周的“真实世界”尚未接入**。

---

## 1. 现状能力盘点（已具备）

按对外 REST 端点统计：

| 域 | 接口数 | 已有能力 |
|---|---|---|
| 商品 (product) | 10 | 商品增删改、发布/归档、标签、SKU 管理、规格属性 |
| 订单 (order) | 17 | 交易+店铺子单拆分、取消、支付/退款回调、确认收货、商家接单/发货/妥投、售后（仅退款/退货退款/换货）、Outbox 管理 |
| 库存 (inventory) | 6 | 预扣生命周期、库存调整、对账 |
| 促销 (promotion) | 5 | 结算 quote/commit/release、领券、券列表 |
| 账户 (account) | 8 | 注册、资料、密码、状态、删除、按 ID/手机/用户名查询 |
| 认证 (auth) | — | OTP/密码登录、OAuth2/OIDC、令牌吊销、授权同意 |
| 支付 (payment) | 3 | 收银台、查单、关单 |
| 网关 (gateway) | — | 路由、JWT、IP 限流、请求幂等 |

### 值得肯定的强项

- **分布式一致性是真工程**：`outbox_event` 同事务落库 → 调度器轮询发布 → DLT 兜底；订单/促销/库存通过 Kafka 领域事件异步驱动。
- **多店铺拆单**已覆盖淘宝最核心的复杂度：`Trade -> List<ShopOrder>`（每个子单含 `shopId/sellerId`）。
- **库存状态机**严谨：`PRE_DEDUCTED -> CONFIRMED / RELEASED / EXPIRED`，版本 CAS + Redis 单实例锁 + 双字段超卖修复、分页对账扫描。
- **服务/数据边界干净**：生产代码**无任何跨 schema 直连**（跨库查询只出现在压测工具 `PostgresClient` 作为断言），每域一个 schema 是刻意的 DDD 边界选择，不是演示式共享库。
- **失败路径做了真实补偿**：`PendingCommitTimeoutScheduler`、`ReservationExpiryTask`、`InventoryReconcileJob`、`OrderTimeoutScheduler`、outbox DLT/重试、`NotificationRetryScheduler`。
- **构件清晰可扩展**：六边形分层统一，新增域（如 search/cart/review）可直接平铺新 module，不破坏既有一致性设计。

---

## 2. 决定性差距（按“是否导致 demo”排序）

### 差距一：资金确认未经第三方验签，可无鉴权伪造（最强的 demo 判据）

`POST /order/trades/{tradeId}/pay/callback` 的处理逻辑直接读请求体：

```java
PaymentSucceededCommand.builder()
    .paymentId(request.getPaymentIntentId())   // 客户端传
    .paidAmountCents(request.getAmountCents()) // 客户端传
    .build();
tradeApplicationService.onPaymentSucceeded(...);
```

- 参考资料：[TradeController.paymentCallback](tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest/TradeController.java:151)
- **无渠道签名验签、无 nonce、无“真实支付单已成功”的回执校验**。
- 收银台返回 `"https://mock-cashier.example.com/pay/..."`，注释“短期返回模拟收银台链接，长期应对接具体渠道生成真实参数”。[PaymentApplicationService.java:319](tinystore-domain-payment/src/main/java/com/github/spud/tinystore/payment/application/PaymentApplicationService.java:319)
- 退款直接 `simulateRefundSuccessAndNotifyOrder`，注释“短期：直接模拟退款成功并通知订单域 / 长期：此处调用第三方退款接口”。[PaymentApplicationService.java:130](tinystore-domain-payment/src/main/java/com/github/spud/tinystore/payment/application/PaymentApplicationService.java:130)
- 支付渠道默认 `DEFAULT`，无微信/支付宝/银联对接。

> 后果：配合网关默认不鉴权（见差距二），**任何人对着任意 tradeId POST 一个自己填写的 amountCents，就能把订单标记为已支付**——一分钱没付也完成交易。真实的“钱”从未离开系统。

---

### 差距二：信任边界（Trust Boundary）不存在

1. **网关默认不鉴权**：整个安全配置用 `@ConditionalOnProperty(... enabled, matchIfMissing=false)` 包裹，`anyExchange().authenticated()` 仅在开关打开时生效。默认配置下系统对外开放、无需令牌。
   - [SecurityConfig.java:14](tinystore-domain-gateway/src/main/java/com/github/spud/tinystore/gateway/security/SecurityConfig.java:14)
2. **买家身份来自请求体**：`TradeController.java:53` `.buyerId(request.getBuyerId())`，无 `@PreAuthorize`、无所有权校验、甚至未读 `Principal`。任何能连上的人都能用任意 buyerId 下单/取消/确认收货别人的订单。
   - [TradeController.java:53](tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/interfaces/rest/TradeController.java:53)
3. **价格客户端可自定**：订单域 ACL 只有 `InventoryClient` / `PromotionClient`，**从不调用商品目录服务**。下单金额来自 `baseUnitPriceCents(line.getPriceCents())`。`ProductClient` 虽存在于 `infrastructure/rpc/payment`，订单流程一次都未用它校验目录价。
   - [TradeApplicationService.buildPromotionQuoteRequest:1130](tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application/service/TradeApplicationService.java:1130)
   - 优惠 quote 只校验 `inputHash`（基于同样的客户端输入计算），只能发现“报价前后输入变了”，**无法发现“价格比官方价低”**。攻击者可 1 分钱下单。

> 三条合起来：无鉴权 + 身份可伪造 + 价格可自定 = 交易路径不存在可信性。

---

### 差距三：数据模型缺了“真实平台”的半壁江山

把全部 Flyway 迁移的建表按 schema 整理，对比真实电商平台应有的表：

**已存在（交易内核相关）**

- order：`trade, shop_order, order_line, fulfillment_package, package_order_ref, payment_intent, after_sale_case, order_outbox, consumer_event_log`
- inventory：`inventory_stock, inventory_reservation, inventory_adjustment, inventory_deduct_record, inventory_reconcile_log, consumer_event_log`
- promotion：`coupon, user_coupon, checkout_quote, coupon_receive_task, idempotency_record, campaign_full_reduction, seckill_price_rule, shipping_rule, consumer_event_log`
- payment：`payment_order, refund_record`
- product：`product, sku, product_category, product_tag`
- account：`user_core, user_realname, user_role, role_dict, role_permission, permission_dict, merchant_core, merchant_shop, consumer_detail, consumer_address, outbox`

**真实平台有、这里完全没有的表**

| 缺失表 | 对应能力 |
|---|---|
| `logistics_tracking` / `shipment_waybill` | 物流轨迹、快递单号（只有静态 `fulfillment_package`） |
| `review` / `review_score` / `shop_dsr` | 评价、评分、店铺 DSR（商品无信任背书） |
| `cart` / `cart_item` | 购物车（完全不存在） |
| `settlement` / `split_amount` | 平台-商家清分结算（平台无法结算给商家） |
| `invoice_request` | 发票 |
| `member` / `member_level` / `points_log` / `growth_value` | 会员/等级/积分/成长值（无留存） |
| `seckill_activity` / `seckill_session` / `seckill_inventory` | 秒杀活动/场次/独立库存（只有结算内的价格规则表） |
| `warehouse` / `stock_batch` / `batch_expiry` | 多仓/批次/效期（单逻辑仓） |
| `risk` / `user_blacklist` / `device_fingerprint` | 风控/黑名单/设备指纹 |
| `price_history` / `item_cost` / `tax` | 价格历史/成本/税 |
| `after_sale_return`（退货物流/验收） | 退货退款只有 `after_sale_case`，无“寄回→验收”两段式 |
| `recommend_user_behavior` / `shop_follow` | 推荐、店铺关注、内容营销 |

> 数据模型才是“demo 与否”的根：没有购物车表就没有“加购”，没有物流表就没有“轨迹”，没有评价表就没有“信任”，没有结算表就没有“平台商业模式”。

---

### 差距四：面向商品模型的不完整

- **无 SPU/Item 概念**：`ProductType` 是一个 5 行空类 `public class ProductType {}`。[ProductType.java](tinystore-domain-product/src/main/java/com/github/spud/tinystore/product/domain/model/valueobject/ProductType.java)
- 只有 `product + sku`，无“同款多规格聚合（item/SPU）”，详情页无法做“同款多 SKU”聚合。淘宝的核心商品模型是 `SPU -> SKU` 两层。

---

### 差距五：生产就绪度（运行配置即 demo）

1. **无生产 profile**：全仓库搜不到 `application-prod*.yml`，只有 `application-dev.yml`（auth）、`application-local.yml`（product/order）、`application-test.yml`。所有默认配置都指向 `localhost`。
2. **密码/密钥硬编码**：几乎每个服务 `password: postgres`（order 甚至硬编码 `localhost:5432/tinystore`，无环境变量回退）；auth 还有 `password: tinystore`、`client-secret: openid-connect`。
   - [account application.yml:40](tinystore-domain-account/src/main/resources/application.yml:40)、[order application.yml:12](tinystore-domain-order/src/main/resources/application.yml:12)、[auth application-dev.yml:92](tinystore-domain-auth/src/main/resources/application-dev.yml:92)
3. **认证 issuer 指向开发域名**：`https://auth.dev.tinystore.com/.well-known/jwks.json`、issuer `https://auth.dev.tinystore.com`、allowed-origins `https://*.dev.tinystore.com`。
   - [auth application-dev.yml:84](tinystore-domain-auth/src/main/resources/application-dev.yml:84)
4. **E2E 种子数据写入 Flyway 迁移**：`inventory/V5__e2e_seed_stock.sql`、`product/V7__e2e_seed_skus.sql`。测试夹具混进随任何环境一起跑的迁移链，真实系统应外置。
5. **网关为不存在的功能配限流**：`gateway-routes.yml:58` 为 `/api/promotion/seckill/**` 配 `capacity:5/refillRate:5`，但项目没有 seckill controller。配置在给未实现功能背书。

---

### 差距六：运维可观测性 / 三方集成缺失

- **无分布式链路追踪**：8 个服务只有 product 引 `opentelemetry-api`（[pom.xml:92](tinystore-domain-product/pom.xml:92)），无 `micrometer-tracing`/`zipkin`/`sleuth`/`otel-exporter`。`traceId` 只是请求头透传，从未接入后端。跨 8 服务排查一次失败只能手工翻日志。
- **零真实三方 SDK**：pom.xml 搜不到 alipay/wechat/stripe（支付）、物流（快递鸟等）、搜索（ES/OpenSearch/Meilisearch）。支付/物流/搜索全部是“自有实现/模拟”。

---

### 差距七：商家侧 / 经营闭环为空

- **商家入驻是空壳**：`MerchantAccountApplicationService` 只有一个空类，无任何方法；账户域对外 8 个接口全是 C 端用户接口，**没有店铺创建/入驻/店铺管理 API**。`sellerId` 只是订单行上的字符串。
  - [MerchantAccountApplicationService.java](tinystore-domain-account/src/main/java/com/github/spud/tinystore/account/application/MerchantAccountApplicationService.java)
- **商家无经营视图**：只有单笔订单 get/accept/ship/delivered，无订单列表、无售后工作台、无经营数据、无批量发货。淘宝/京东的繁荣靠供给端，这里 B 端完全是占位符。

---

### 差距八：发现与信任层（货架/信任）缺失

- **无搜索**（无 ES、无 search 接口、无 search 路由）。
- **无评价/评分**，商品无信任背书；**无买家订单列表**（只有单一详情查询，买家/商家都没有分页筛选）；**无地址 CRUD**（地址实体存在但无接口，下单却引用 `addressId`）。
- **无会员/积分/内容/客服/推荐**——复购与增长飞轮不存在。

---

### 差距九：规模骨架仍是单机演示形态

- **单逻辑仓**：库存域无 `warehouse` 维度。
- **单库单 Redis**：每域一个 schema 共用一个 PG 实例；Redis 业务默认单点（cluster 仅测试编排）。
- **读路径无规模化设计**：无读模型/无搜索索引/无 CDN/无热点分层缓存。淘宝级是“读多写极少、分库分表、多机房、搜索/推荐独立集群”，这里规模上限就是单 PG。

---

### 差距十：文档与代码不符（体系化 overclaim 信号）

- README/CLAUDE 声称“基于 Spring State Machine 编排的支付 Saga”——全仓库**无任何 `StateMachine` 依赖或使用**，实际是命令式服务编排 + Kafka 事件。
- 旧版 CLAUDE(已 stash 分支) 声称“Order/Payment 域使用 CQRS/Event Sourcing”——实际是 JPA 实体 + 关系表，无事件溯源、无 projection、无读模型。
- 这类 overclaim 说明文档描述的是“规划中的架构”，不是现状。

---

### 差距十一（内核自我校验）：支付后库存确认的一致性窗口

> 这一条是对“交易一致性内核 = 生产级”这一结论的**自我修正**：内核很强，但存在一个真实的一致性窗口。

- **确认已改为异步 + 乐观投影**：`onPaymentSucceeded` 不再同步 Feign 确认库存，而是写入 `INVENTORY_CONFIRM` 到 outbox，**并在同一事务里将 `shopOrder.inventoryStatus` 乐观置为 `CONFIRMED`**。
  - [TradeApplicationService.onPaymentSucceeded](tinystore-domain-order/src/main/java/com/github/spud/tinystore/order/application/service/TradeApplicationService.java:798)
- **无再驱动补发**：
  - `PendingCommitTimeoutScheduler` 只管促销承诺 PENDING 超时自动取消；
  - `OrderTimeoutScheduler` 只管支付超时关闭与自动收货；
  - `InventoryReconcileJob` **完全不碰预约状态**（无 `CONFIRM`/`PRE_DEDUCTED` 处理）。
- **预约过期与支付状态脱钩**：`ReservationExpiryTask` 每分钟把 `expire_at` 已过的 `PRE_DEDUCTED` 直接过期释放，不检查该预约是否属于已支付订单。
  - [ReservationExpiryTask.java:53](tinystore-domain-inventory/src/main/java/com/github/spud/tinystore/inventory/infrastructure/scheduler/ReservationExpiryTask.java:53)
- **confirm 对“逻辑过期”预约直接判冲突**：若 `expireAt` 已过，confirm 走冲突分支将该预约排除在确认之外。
  - [InventoryReservationDomainService.java:252](tinystore-domain-inventory/src/main/java/com/github/spud/tinystore/inventory/domain/service/InventoryReservationDomainService.java:252)

**组合后果**：若 `INVENTORY_CONFIRM` 因 outbox 延迟/消费 lag/在 TTL 最后一秒到达而晚于 `expire_at`，确认命中“逻辑过期”冲突 → 预约随后被过期调度器释放；但订单域已标记 PAID、读模型已显示 `CONFIRMED`，且无机制把库存拉回确认态。于是出现“**订单已付、库存却被释放**”，卖家无法履约，单仓场景下同批库存可能被再次售出。

**定性**：正常时序下（outbox+consumer 工作、支付在 TTL 内）它是好的；但缺少针对“已付但库存确认丢失/迟到/被拒”的补偿或再驱动，`confirm` 与 `expireAt` 之间存在秒级竞态（源码注释亦承认“5s 调度窗口”）。**demo 在理想时序下跑得通；生产必然遭遇 outbox 积压、消费重试与高峰延迟。**

**默认开关说明（feature flag 精确核对）**：
- `order.promotion.commit-async-enabled: false`（[order application.yml:84](tinystore-domain-order/src/main/resources/application.yml:84)）——**异步促销承诺门控默认关闭**，默认走同步 commit；`PendingCommitTimeoutScheduler`（兜底自动取消）因此默认不生效。
- `order.inventory.use-canonical-reservation-api: ${...:false}`（[order application.yml:102](tinystore-domain-order/src/main/resources/application.yml:102)）——变量默认 false，但**当前 `onPaymentSucceeded` / `createTrade` 实际代码已在规范（canonical）模式下工作，且异步 `INVENTORY_CONFIRM` 恒写 outbox、不受该 flag 控制**。该 flag 更接近迁移期遗留，已不再严格门控当前路径。
- 因此**差距十一所述的“已付→库存确认”一致性窗口存在于当前实际代码路径（canonical），并非仅存在于被 flag 关闭的备选路径**。

---

## 3. Demo vs 生产 判定表（最终）

| # | 维度 | 判定 | 决定性证据 |
|---|---|---|---|
| 1 | 资金确认真实性与验签 | ❌ demo | pay/callback 读客户端 amountCents；收银台 mock；退款模拟 |
| 2 | 信任边界（鉴权/身份/定价） | ❌ demo | SecurityConfig:14 默认关；TradeController:53 身份来自 body；价格客户端自定 |
| 3 | 生产配置/密钥管理 | ❌ demo | 无 prod profile；`password: postgres`；`.dev.tinystore.com` |
| 4 | 数据模型完整性 | ❌ demo | 无物流/购物车/评价/结算/会员/风控/秒杀活动/多仓表 |
| 5 | 商家供给端 | ❌ demo | MerchantAccountAppService 空壳；无店铺 API |
| 6 | 发现与信任（搜索/SPU/评价/订单列表/地址） | ❌ demo | 无搜索；ProductType 空类；无评价；无地址接口 |
| 7 | E2E 覆盖广度 | ❌ demo | 仅 1 条 happy path + 2 负向；种子数据入迁移 |
| 8 | 运维可观测性 | ❌ demo | 仅 opentelemetry-api，无 exporter；traceId 未接后端 |
| 9 | 文档一致性 | ⚠️ overclaim | 宣称 StateMachine/CQRS，实际未实现 |
| 10 | 规模骨架（多仓/分库/读模型） | ❌ demo | 单逻辑仓、单 PG、无读模型 |
| 11 | **交易一致性内核** | ⚠️ **生产级但有一致性窗口** | 支付后库存 confirm 异步+乐观投影；无再驱动；预约过期与支付脱钩（见差距十一） |
| 12 | **服务/数据边界隔离** | ✅ **生产级** | 生产代码无跨 schema 直连 |
| 13 | **工程可扩展性（六边形/模块化/测试方法论）** | ✅ **生产级** | 结构干净，新增域容易 |

---

## 4. 测试深度画像

| 模块 | main:test | 覆盖率信号 |
|---|---|---|
| order | 124:32 | ~26%（重点域，合理） |
| inventory | 66:19 | ~29%（重点域，合理） |
| promotion | 70:11 | ~16% |
| payment | 10:2 | ~20% |
| product | 75:8 | ~11% |
| account | 39:5 | ~13% |
| auth | 126:7 | **~6%** |
| gateway | 27:4 | ~15% |

端到端黑盒：`tests/api` 共 11 个文件，核心 E2E 只有一个真正跑通全链路的 `strictClosureE2E_createTradeToConfirmReceipt` + 2 个负向 + 端点覆盖契约测试。

> 结论：测试**深度**集中在交易一致性，但**广度**只到单条 happy path。这是“工程内核很强、产品范围很窄”的数字画像。

---

## 5. 优先改造路线（按“修一条就接近生产”排序）

1. **交易路径可信化**：支付回调加渠道验签&改真实渠道回调；网关强制鉴权；订单接口绑定认证主体并做所有权校验；下单改为服务端从商品/价格服务取权威价（引入 ProductClient 校验）。
2. **补资金与信任**：落地真实支付网关（签名+回调验签+原路退款）、搜索（可先用 PostgreSQL 全文检索过渡）、SPU 详情聚合、评价、订单/售后列表、地址 CRUD。
3. **补供给端与平台**：商家入驻、店铺、商家订单/售后工作台、结算清分、发票。
4. **补治理与规模**：风控（设备指纹+黑名单+频控）、多仓供应链、读模型/搜索、分库分表、可观测性（分布式追踪）。

> 心态提示：demo 到可上线的顺序不是“再加秒杀/拼团”，而是**先修信任与资金边界，再补交易前后的数据闭环，最后才谈规模**。

---

## 6. 证据索引

| 证据 | 位置 |
|---|---|
| 网关安全默认关闭 | `tinystore-domain-gateway/.../security/SecurityConfig.java:14` |
| 订单身份来自请求体 | `tinystore-domain-order/.../interfaces/rest/TradeController.java:53` |
| 客户端自定价格 | `tinystore-domain-order/.../application/service/TradeApplicationService.java:1130` |
| 收银台 mock URL | `tinystore-domain-payment/.../application/PaymentApplicationService.java:319` |
| 退款模拟 | `tinystore-domain-payment/.../application/PaymentApplicationService.java:130` |
| 商家应用空壳 | `tinystore-domain-account/.../application/MerchantAccountApplicationService.java` |
| 空产品类型（无 SPU） | `tinystore-domain-product/.../valueobject/ProductType.java` |
| dev 域名/硬编码密码 | `tinystore-domain-auth/.../application-dev.yml:84,92` |
| 网关为不存在功能配限流 | `tinystore-domain-gateway/.../gateway-routes.yml:58` |
| E2E 种子入迁移 | `tinystore-domain-inventory/.../V5__e2e_seed_stock.sql`、`tinystore-domain-product/.../V7__e2e_seed_skus.sql` |
| 仅 opentelemetry-api 依赖 | `tinystore-domain-product/pom.xml:92` |
