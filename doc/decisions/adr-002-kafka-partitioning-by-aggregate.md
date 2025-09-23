# ADR-002: Kafka 按聚合键分区

- Status: Accepted
- Context: 需要保证同一聚合事件顺序与单写者语义，降低并发冲突与回放复杂度。
- Decision: 主题按聚合键（orderId/skuId/paymentId）做分区；单消费者组承载单分区串行处理。
- Consequences: 热点分区可能倾斜；需热键治理与限流；提高顺序保障与幂等实现简化。
- Alternatives: 随机分区/轮询；放弃顺序 → 无法满足 ES 与库存单写要求。