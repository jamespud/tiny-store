# Tiny Store Monorepo

欢迎来到 Tiny Store（多模块电商示例）的代码仓库。本仓库采用 Java 21 + Spring Boot 3 多模块单仓（monorepo）结构，聚焦于“领域驱动 + 事件驱动 + CQRS/ES（订单/支付）”的微服务设计与工程落地。

## 我是谁 / 我不负责什么 / 我依赖谁 / 我产出什么 / SLO 摘要

- 我是谁：一个按有界上下文拆分的电商系统示例，包含 Auth/User/Catalog/Promotions/Cart/Order(ES)/Inventory/Fulfillment/Payment(ES)/Search/Recommendation/Admin/Gateway 等服务。
- 我不负责什么：生产级网关策略、支付渠道商业接入、真实风控模型与报表系统；本仓库聚焦架构与领域落地方法。
- 我依赖谁：PostgreSQL、Kafka、Redis、OpenTelemetry/Prometheus/Grafana/Loki、Kubernetes（或本地 docker-compose）。
- 我产出什么：跨服务领域事件（Kafka），读模型/投影视图（Postgres/OpenSearch），以及运维可观测数据。
- SLO 摘要（示例）：Checkout p95 < 300ms（不含支付跳转）、商品详情 p95 < 120ms、支付回调处理 p95 < 500ms；可用性 99.9%。

## 快速开始

- 建议先阅读《架构总览》与《Checkout 编排》，再根据 Playbooks 完成本地联调：
	- 架构总览：`doc/architecture/overview.md`
	- Checkout 编排：`doc/architecture/saga-checkout.md`
	- 本地联调（docker-compose、伪支付/伪物流）：`doc/playbooks/local-dev.md`

## 文档地图（A+B 融合：导航 + 分册）

- Architecture
	- 总览：`doc/architecture/overview.md`
	- 可靠性/限流降级：`doc/architecture/reliability.md`
	- Checkout 编排（Saga）：`doc/architecture/saga-checkout.md`

- Domain（领域）
	- 订单（ES+CQRS）：`doc/domain/order-es.md`（参考：`doc/order/state-machine-design.md`、`tinystore-domain-order/README.md`）
	- 支付（ES+CQRS）：`doc/domain/payment-es.md`
	- 库存预占（TTL）：`doc/domain/inventory-reservation.md`
	- 金额模型：`doc/domain/money.md`
	- 售后与退款边界：`doc/domain/aftersale.md`
	- 状态映射（订单/库存/支付）：`doc/domain/order-payment-inventory-status-mapping.md`

- Messaging（事件与主题）
	- 事件信封：`doc/messaging/event-envelope.md`
	- 主题与分区策略：`doc/messaging/event-topics.md`
	- 消费者拓扑：`doc/messaging/consumer-topology.md`

- Read Models
	- 订单汇总视图：`doc/readmodels/order-summary.md`
	- 订单时间线：`doc/readmodels/order-timeline.md`
	- 支付状态视图：`doc/readmodels/payment-status.md`

- Operations & Observability
	- 可观测与 SLO：`doc/operations/observability.md`
	- 容量与压测：`doc/operations/capacity.md`
	- Runbooks：`doc/operations/runbooks/consumer-lag.md`、`doc/operations/runbooks/dlq-replay.md`、`doc/operations/runbooks/webhook-storm.md`

- Security & Compliance
	- 认证与授权：`doc/security/auth.md`
	- PII 与合规：`doc/security/pii.md`

- Testing 策略
	- 契约测试：`doc/testing/contract-tests.md`
	- ES 聚合单测：`doc/testing/es-aggregate-tests.md`
	- 投影重建与回放：`doc/testing/projection-rebuild-tests.md`
	- 秒杀专项压测：`doc/testing/seckill-load-tests.md`

- Playbooks（工程落地）
	- Topic 规划与分区键：`doc/playbooks/topic-planning.md`
	- ES 事件表/快照表 DDL：`doc/playbooks/es-schema-ddl.md`
	- Outbox 与投影重放：`doc/playbooks/outbox-and-projection-replay.md`
	- 本地联调：`doc/playbooks/local-dev.md`

- Reference（参考契约）
	- API 索引：`doc/reference/api-index.md`
	- 事件 schema：`doc/reference/event-schemas.md`
	- 索引/读模型 DDL：`doc/reference/index-ddl.md`
	- 观测字典：`doc/reference/observability-dictionary.md`
	- SQL（服务命名空间示例）：`doc/reference/sql/`

- ADR（架构决策记录）
	- 目录与模版：`doc/decisions/README.md`、`doc/decisions/adr-000-template.md`
	- 首批 ADR：`doc/decisions/adr-001-event-sourcing-order-payment.md` 等

- 实施清单
	- 逐项可执行 checklist（双轨式）：`doc/implementation-checklist.md`

---

提示：已有订单领域文档仍保留在 `doc/order/` 与 `tinystore-domain-order/README.md`，此文档地图在其之上提供跨域导航与工程化落地指引。

