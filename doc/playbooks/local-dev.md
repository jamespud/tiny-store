# Playbook：本地联调

- 依赖：Docker（Postgres/Kafka/Redis/Jaeger/Prometheus/Grafana）。
- 步骤：启动依赖 → 运行网关/核心服务 → 使用沙箱支付/伪物流 → 观察指标与日志。
- 快速路径：下单 → 预占 → 创建支付意图 → 模拟回调成功 → 库存提交。
