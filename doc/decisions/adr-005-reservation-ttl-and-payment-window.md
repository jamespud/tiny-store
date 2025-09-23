# ADR-005: 预占 TTL 与支付时窗对齐

- Status: Accepted
- Context: 预占库存的有效期需与支付允许时间一致，避免悬挂订单。
- Decision: 统一配置 TTL=支付时窗（默认 15 分钟），由 Orchestrator 计时。
- Consequences: 超时路径一致；需关注渠道二维码有效期差异。
- Alternatives: 不对齐；将导致更多补偿与用户体验不一致。