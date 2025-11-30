# CHANGELOG — Order Module Enhancements

## 2025-11-30

- Added: Idempotency replay for user cancel and merchant actions (accept, ship, delivered confirm, cancel decisions).
- Added: Unified Outbox payload schema v1 with `version/eventType/aggregateId/subOrderId/occurredAt/tenantId/operatorId/traceId/data`.
- Added: Audit layer (`AuditRecorder` + `AuditEntry`) with MDC propagation; integrated in state machine actions and application service.
- Added: Metrics (`order_idempotency_hit_total`, `order_idempotency_miss_total`, `order_state_machine_failure_total`, `order_outbox_publish_latency`, `order_http_error_total`).
- Added: Global error mapping for 403/404/409/422 with normalized `ORDER-XXXX` codes and uniform response body.
- Added: Tests covering idempotency replay, error mapping, state machine paths/illegal transitions, payload schema, audit invocation, and metrics emission.
- Changed: Centralized event-type constants and standardized naming (`order.<domain>.<action>`).
- Deprecated: Legacy payment callback controller; route consolidated to current REST endpoints.

### Risks & Compatibility
- Outbox payload schema changes require consumers to tolerate `version: v1` and envelope fields; older consumers may need adaptation.
- Expanded exception mapping can alter client-visible error codes; validate downstream assumptions.

### Migration Notes (optional)
- If persisting audits/idempotent results, create tables `order_audit_log` and `order_idempotent_result` with suggested indexes (`idx_order_audit_order_id`, `idx_order_audit_trace_id`, `idx_idemp_expires`).
