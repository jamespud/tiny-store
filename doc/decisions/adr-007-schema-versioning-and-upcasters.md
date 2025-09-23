# ADR-007: 事件 Schema 版本与 Upcaster

- Status: Accepted
- Context: 事件模式需要随业务演进保持兼容与可回放。
- Decision: 引入 `schemaVersion` 元数据；维护 Upcaster；变更经 ADR 审核与灰度验证。
- Consequences: 增加演进治理成本；保证历史事件长期可用。
- Alternatives: 不做版本化；面临回放失败与消费者崩溃风险。