# ADR-003: Outbox vs CDC 取舍

- Status: Accepted
- Context: 非 ES 服务的事件发布需要事务一致性；可选 Outbox 或数据库 CDC。
- Decision: 统一采用应用层 Outbox + 后台 Relay；消费端幂等去重。
- Consequences: 引入 Relay 运维；但更好地跨库/多 DB 适配与发布控制。
- Alternatives: CDC；对异构 DB/Schema 权限与延迟可控性较差。