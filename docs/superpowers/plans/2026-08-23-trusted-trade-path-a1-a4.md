# 可信交易路径 A1–A4 实现计划

> **目标：** 让核心交易路径在下单/支付/履约三处都不再可被伪造——无鉴权、伪造身份、客户端自定价格、伪造支付回调全部失效。据此把 A1–A4 转成可执行、可验证的 TDD 任务。
>
> **架构：** 沿用项目既有模式——网关强制 JWT 鉴权并用 `X-Tinystore-Sub` 向服务透传身份；订单接口改从认证主体取 buyer/seller 并做所有权校验；下单前向商品域 (ProductClient) 拉取服务端权威价并在不匹配时拒绝；支付/退款回调增加渠道签名验证。所有校验用 feature-flag 包裹，默认在生产开启（Spring 环境 true），裸测 `new` 默认 false 以保持既有单测兼容。
>
> **技术栈：** Java 17 / Spring Boot 3.5 / Spring Security OAuth2 Resource Server / OpenFeign / Mockito / MockMvc。

---

## 文件结构

- 网关：`tinystore-domain-gateway/.../security/SecurityConfig.java`、`.../application.yml`
- 订单业务：`tinystore-domain-order/.../application/service/TradeApplicationService.java`
- 订单身份：新增 `.../interfaces/security/AuthPrincipal.java`（或等价工具）
- 订单定价：`.../application/price/SkuPriceResolver.java`（✅ 已完成）
- 订单 ACL：`tinystore-library-infrastructure/.../infrastructure/rpc/payment/ProductClient.java`（✅ 已完成）
- 订单支付：`.../interfaces/rest/TradeController.java`、新增 `.../application/security/PaymentSignatureVerifier.java`
- 商品：`.../product/.../internal/rest/InternalSkuController.java`、`.../internal/.../InternalSkuQueryService.java`、`.../filter/ShopContextFilter.java`（✅ 已完成）

---

## ✅ A3 已完成：服务端权威定价

防止“客户端自定价格”。`SkuPriceResolver` 按 (shopId, skuId) 从商品域取权威单价，不匹配抛 `INVALID_PRICE`、缺失抛 `SKU_PRICE_NOT_FOUND`。

- 共享 `ProductClient` 新增 `batchSkuPrices(shopId, skuIdsCsv)`。
- 商品内部端点接受显式 `shopId` + CSV，`ShopContextFilter` 放行 `/internal/`。
- `TradeApplicationService.createTrade` 在报价前调用 `SkuPriceResolver.validateAndAttach`，由 `order.pricing.authoritative-enabled`（默认 true）控制。
- 验证：`tinystore-domain-order` 122/122、`tinystore-domain-product` 29/29，`SkuPriceResolverTest` 红-绿回证通过。

---

## ✅ A4 已完成：支付/退款回调验签

防止“任意 POST 伪造已付”。回调必须携带 `signature`（HMAC-SHA256，密钥 `order.payment.callback-secret`），载荷为 `tradeId|paymentId|amount|timestamp`。

- `PaymentSignatureVerifier`（HMAC-SHA256）+ `CallbackSignatureException`；校验失败在 `TradeController` 返回 **401**。
- `PaymentCallbackRequest`/`RefundCallbackRequest` 新增 `signature`/`timestamp`；`paymentCallback`/`refundCallback` 入口先验签。
- 验证：`PaymentSignatureVerifierTest` 4/4、`TradeControllerTest` 新增无效签名→401 用例；订单模块 127/127。

> ⚠️ **E2E 契约变化**：硬编码的安全加固会让现有无签名/无鉴权 E2E 直接 401。因此：
> - `docker/docker-compose-test.yml` 给 order 加 `ORDER_PAYMENT_CALLBACK_SECRET`（或等价 property）。
> - `tests/api/MallE2EIT` 的 `pay/callback` 需按同一载荷计算 HMAC 并携带 `signature`+`timestamp`。

---

## ✅ A1 已完成：网关强制 JWT 鉴权（默认开启）

**Files:**
- Modify: `tinystore-domain-gateway/src/main/java/com/github/spud/tinystore/gateway/security/SecurityConfig.java`
- Modify: `tinystore-domain-gateway/src/main/resources/application.yml`
- Test: `tinystore-domain-gateway/src/test/java/com/github/spud/tinystore/gateway/security/SecurityConfigTest.java`

- ✅ 修改：`@EnableWebFluxSecurity` 已启用；`@ConditionalOnProperty` 改 `matchIfMissing=true`（默认开启）；网关 `application.yml` `resource-server.enabled: true`。
- ✅ 测试：`SecurityConfigTest`（@WebFluxTest + mock `ReactiveJwtDecoder` + 真实 `SimpleMeterRegistry`）断言未认证 `/api/protected` → 401。
- ✅ 验证：网关模块 4/4。

> ⚠️ E2E 契约：日常 `mvnw` 单测已验证；`tests/api` 的 hermetic E2E 在 compose-test 中仍以 `TINYSTORE_SECURITY_RESOURCESERVER_ENABLED=false` 跑（网关鉴权关），生产默认开。

---

## ✅ A2 已完成：订单接口绑定认证主体 + 所有权校验

**Files:**
- Modify: `tinystore-domain-order/.../interfaces/rest/TradeController.java`
- Modify: `tinystore-domain-order/.../interfaces/rest/AfterSaleController.java`、`.../rest/MerchantOrderController.java`
- Add: `tinystore-domain-order/.../interfaces/security/AuthPrincipalSupport.java`
- Test: `TradeControllerTest`、`MerchantOrderControllerTest` 增删用例

- ✅ `createTrade` 以 `X-Tinystore-Sub` 为权威 buyerId，body 不匹配 → 403；未认证 → 401。
- ✅ `cancelTrade`/`confirmReceipt` 做所有权校验（非买家 → 403）。
- ✅ 新增用例：认证匹配 200 / 不匹配 403 / 未认证 401 / 非所有者取消 403。
- ✅ 验证：订单模块 131/131。

> 测试栈经 `ORDER_AUTHZ_REQUIRE_AUTHENTICATED_BUYER=false` 显式关闭（hermetic E2E）；生产默认 `true`。

---

## ✅ A4 已完成：支付/退款回调验签

- `PaymentSignatureVerifier`（HMAC-SHA256，载荷 `tradeId|paymentId|amount|timestamp`）+ `CallbackSignatureException`；失败返回 401。
- 回调 DTO 加 `signature`/`timestamp`；`paymentCallback`/`refundCallback` 入口验签；开关 `order.payment.callback-verify-enabled`（默认 true）。
- ✅ 验证：`PaymentSignatureVerifierTest` 4/4 + 控制器新增无效签名→401；订单模块 131/131。

> 测试栈经 `ORDER_PAYMENT_CALLBACK_VERIFY_ENABLED=false` 显式关闭；生产默认 `true`。真实渠道接入时替换 verifier 实现并配置 `order.payment.callback-secret`。

---

## 验证（本 worktree 实测）

- 订单 `tinystore-domain-order -am test`：✅ **131/131**。
- 商品 `tinystore-domain-product -am test`：✅ **29/29**。
- 网关 `tinystore-domain-gateway -am test`：✅ **4/4**。
- 全模块 `./mvnw -o compile`：✅ BUILD SUCCESS；全仓库仅一个 `product-service` Feign 客户端。
- 完整 E2E（compose）：后续需真实 auth 发 token / 签名回调（测试栈已用 flag 保持 hermetic）。
