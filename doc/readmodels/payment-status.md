# 支付状态视图（payment_status_view）

- 字段（示例）：(payment_id PK, order_id, channel, status, amount, updated_at)
- 用途：回调态展示、对账、重试/补偿判断。
- 索引：`(order_id)`, `(status, updated_at desc)`。
