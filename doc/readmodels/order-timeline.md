# 订单时间线（order_timeline）

- 展示订单关键状态时间轴（创建、支付、预占、发货、妥投、完成/取消）。
- 投影幂等：按 (order_id, event_type, version) upsert。
- 查询：按 order_id 精确查。
