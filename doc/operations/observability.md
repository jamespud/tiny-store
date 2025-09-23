# 可观测与 SLO（Observability）

## 指标规范（样例）
- 领域 SLIs：`reservation_latency_ms`, `payment_callback_latency_ms`, `projection_lag`。
- 基础：`consumer_lag`, `dlq_depth`, `http_server_requests_seconds`。

## Tracing
- 统一 tags：`aggType`, `aggregateId`, `orderId`, `skuId`, `channel`, `projectionName`。

## Logging
- 结构化 JSON，关联 `traceId`/`correlationId`，敏感字段脱敏。

## SLO 计算口径
- 指定采样与窗口；明确例外（支付跳转不计入 checkout p95）。
