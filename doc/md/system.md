
## 微服务划分

① API Gateway（边界入口）

统一路由、鉴权、流量限制、灰度发布、TLS 终端。

做速率限制、IP 黑白名单、请求聚合（把多个后端请求合并成一个对外接口）。

② Auth / Identity Service（鉴权与用户认证 / OIDC Provider）

基于 Spring Authorization Server 自建 OIDC Provider：

- 能力：OIDC Discovery、Authorization Code + PKCE、Client Credentials、Refresh Token（轮换）、UserInfo、JWKS；
- Token：JWT（RS256），短效 Access Token + 轮换 Refresh Token，`issuer` 统一对外；
- 客户端：机密/公共客户端注册与管理，严格限制重定向 URI；
- 授权：scope/role 最小化原则，下游服务基于 Resource Server 校验与授权；
- 分工：与用户服务分离，仅负责认证与令牌发放/校验；用户资料、权限模型来源可由用户服务/目录系统提供。

集成边界：

- API Gateway 与各域服务统一作为 Resource Server，配置 `issuer-uri` 自动发现 JWKS 并离线验签；
- 默认保护 `/api/**`，健康检查与静态/开放端点放行；
- 安全基线：TLS、CORS/回调白名单、登录/授权/刷新限流、审计与指标。

③ User Service（用户资料）

用户档案、地址簿、积分/会员等级（可选）、隐私偏好。

负责用户数据的 CRUD 和对外读写权限。

④ Product Catalog（商品目录）

商品元数据（标题、描述、类目、属性）、上下架、SKU 映射。

负责将数据同步到 Search Service / Cache / CDN（图片）并发布 ProductUpdated 事件。

⑤ Search Service（基于 ES 或类似）

支持全文检索、过滤、分面（facets）、排序。

从 Product Catalog 拉取索引或通过事件驱动更新索引。

⑥ Inventory Service（库存）

精确可用库存查询、预留（reserve）、释放（release）、库存调整。

对库存操作要求强一致或采用乐观锁/行级锁；热销商品用 Redis + CAS 快速响应并异步落库。

⑦ Pricing & Promotion Service（价格与促销）

价格决策（基础价、阶梯价、活动价、优惠码校验）、价格历史记录与缓存。

计算最终付款金额并返回明细（便于审计）。

⑧ Cart Service（购物车）

存储未下单商品（Guest cart & User cart 合并），高并发读写优先使用 Redis，支持 TTL、并发合并策略。

⑨ Order Service（订单）

生成订单、状态机管理（PENDING → PAID → SHIPPED → COMPLETED / CANCELED）、持久化订单主数据。

作为 Saga 协调者或发起事件（OrderCreated / OrderCancelled / OrderPaid）。强事务逻辑在此处管理（使用 Saga 模式）。

⑩ Payment Service（支付）

调用第三方支付网关、处理回调（异步通知）、记录交易流水、token 化敏感信息（不存储卡号以降低 PCI 范围）。

提供幂等处理与回调验证。

⑪ Shipping / Logistics Service（物流）

生成运单、计算运费、对接快递公司、跟踪状态并更新订单状态。

⑫ Notification Service（通知）

基于事件发送邮件 / 短信 / Push，支持模板与队列（异步、可重试）。

### Optional

Recommendation Service（推荐/个性化）：基于行为事件做在线/离线推荐。

Review & Rating Service（评论）：评论写入、审核、反垃圾。

Coupon/Promotion Engine（复杂促销引擎）：规则引擎、上下文评估、并发抢券策略。

Merchant / Seller Service（商家管理，若多店铺）：商家入驻、结算、权限。

Analytics / BI / Data Warehouse：事件采集（Kafka）→ ETL → DWH，用于报表和离线 ML。

Fraud Detection（风控）：交易风控、异常行为检测。

Admin / Ops Panel：运维、订单人工干预、商品管理后台。