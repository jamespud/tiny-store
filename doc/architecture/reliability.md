# 可靠性与降级（Reliability）

定义限流/熔断/降级矩阵与热键隔离策略，面向大促与异常场景。

## 限流/熔断/降级矩阵（示例）
- API 网关：基于用户/IP/端点的速率限制；秒杀接口独立限流桶。
- 服务间调用：超时、重试（幂等前提）、熔断与回退。
- 消费者：批量大小、重试次数、DLQ 分流阈值。

## 热键与热点 SKU
- Redis 预库存键：`prestock:sku:{id}` 原子递减，发放购买令牌。
- Kafka 单写：`inventory-commands` 按 `skuId` 分区，单消费者组保障顺序。
- 失败补偿：令牌回滚、库存纠偏事件、审计轨迹。

## 观测与告警
- 指标：`consumer_lag`, `projection_lag`, `dlq_depth`, `webhook_idempotent_hit`。
- 报警：阈值 + 窗口；联动降级策略（如禁止使用优惠券、关闭推荐）。
