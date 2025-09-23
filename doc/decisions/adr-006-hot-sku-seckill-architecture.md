# ADR-006: 热门 SKU 秒杀架构

- Status: Accepted
- Context: 热点高并发下需要反超卖与削峰填谷。
- Decision: Redis 预库存 + 令牌通道；Kafka 排队；单分区单写校正；失败补偿与对账。
- Consequences: 引入 Redis 运维与一致性补偿；显著提升峰值承载。
- Alternatives: 仅依赖数据库行锁；吞吐与可用性不足。