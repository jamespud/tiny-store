# ADR-001: 订单/支付采用 Event Sourcing + CQRS

- Status: Accepted
- Context: 订单/支付需要审计、回放、强不变式与跨域异步一致性。
- Decision: Order/Payment 使用 ES + CQRS；其余服务采用 Outbox 最终一致。
- Consequences: 带来投影运维成本与 Upcaster 治理；但获得可回放与强边界。
- Alternatives: 纯 CRUD + Outbox；CDC。放弃原因：审计/回放能力不足。
