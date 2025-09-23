# ADR-004: 幂等键与 TTL 策略

- Status: Accepted
- Context: 用户请求、命令与支付回调均可能重复投递。
- Decision: 对外 `X-Idempotency-Key`；内部 `commandId`；回调以 `deliveryId/channel`；持久化去重并设定 TTL。
- Consequences: 存储与清理成本；副作用可控、重试安全。
- Alternatives: 仅靠版本冲突检测；覆盖面不足（回调场景）。