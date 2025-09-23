# 实施清单（双轨式）

> 说明：每项含“产出/验收/引用”。可拆分为看板任务。

## 轨道 A：文档与规范
1. 建立文档框架与导航（本次完成）。
   - 产出：根 README + docs 目录。
   - 验收：链接可达；Mermaid 渲染正常。
2. 事件信封与主题规范定稿。
   - 产出：`doc/messaging/event-envelope.md`, `doc/messaging/event-topics.md`。
   - 验收：示例与现有服务事件对齐；分区键覆盖聚合键。
3. 领域分册（Order/Payment/Inventory）补全细节与图谱。
   - 产出：`doc/domain/*` 更新（已补全：Order/Payment/Inventory/价格与促销/税费与结算/商品与搜索）。
   - 验收：事件/状态/快照策略一致；与现有 `doc/order/*` 术语对齐；价格/促销/税费/结算/商品/搜索领域文档齐全。
4. 可观测与 SLO 词典化。
   - 产出：`doc/operations/observability.md`, `doc/reference/observability-dictionary.md`。
   - 验收：指标/标签被各服务采集。
5. ADR 流程落地。
   - 产出：`doc/decisions/*`。
   - 验收：新增重大变更前需 ADR；旧 ADR 可被 supersede。

## 轨道 B：工程与落地
1. 事件存储/快照表 DDL 初始版本
   - 产出：`doc/playbooks/es-schema-ddl.md`（DDL 示例）
   - 验收：本地环境可建表；事件追加/快照写入正常。
2. Outbox 与投影 offset 表契约
   - 产出：`doc/reference/index-ddl.md`；`doc/playbooks/outbox-and-projection-replay.md`
   - 验收：消费者可从 offset 重启且幂等。
3. Topic 规划与集群初始化
   - 产出：`doc/playbooks/topic-planning.md`
   - 验收：主题/分区/副本按规划创建；键策略生效。
4. Checkout Orchestrator 雏形（文档→代码）
   - 产出：按 `doc/architecture/saga-checkout.md` 实现命令/事件处理骨架。
   - 验收：成功/失败/超时三路径可在本地联调复现。
5. 支付回调幂等与对账视图
   - 产出：`payment_status_view` 投影；回调 idempotency store。
   - 验收：重复回调不致副作用；对账视图可查询。
6. 压测与降级
   - 产出：`doc/operations/capacity.md` 脚本/目标；秒杀专题测试。
   - 验收：达成 p95 目标或给出降级策略。

## 参考锚点
- 架构总览：`doc/architecture/overview.md`
- Saga：`doc/architecture/saga-checkout.md`
- 领域：`doc/domain/*`
- 消息：`doc/messaging/*`
- Runbooks：`doc/operations/runbooks/*`
