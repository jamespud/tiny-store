# 订单汇总视图（order_summary）

## 用途
- 买家端订单列表、状态筛选、分页与排序。

## 字段与索引（示例）
- (order_id PK, user_id, status, total_amount, payment_status, created_at, updated_at)
- 索引：`(user_id, created_at desc)`, `(status, created_at desc)`, `(payment_status)`

## 投影与幂等
- `projection_offset` 表，幂等 upsert；重建从最早 offset 或快照起。
