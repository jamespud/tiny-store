# Playbook：Outbox 与投影重放

## Outbox
- 业务写入与 outbox 插入同事务；后台 relay 发布至 Kafka。
- 消费者端以 `eventId` 去重；不可变事件。

## 投影重放
- 清理/隔离目标读模型；从最早 offset 或快照重放。
- 幂等 upsert；限速；观测 `projection_lag` 与业务一致性。

## 验收
- 重放后读模型与事件源一致；期间无越权副作用。
