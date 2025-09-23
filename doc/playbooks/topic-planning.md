# Playbook：Topic 规划与分区键

- 列表：见 `../messaging/event-topics.md`。
- 分区数与副本：按吞吐/可用性目标与热点分布规划；为热 SKU 单独扩充分区。
- key 策略：按聚合键（orderId/skuId/paymentId）保障顺序与单写。
- 验收：消费者组无跨分区乱序，热点倾斜在可控阈值内。
