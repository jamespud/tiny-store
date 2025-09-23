# Playbook：ES 事件/快照表 DDL（示意）

```sql
-- events
CREATE TABLE IF NOT EXISTS events (
  event_id uuid PRIMARY KEY,
  aggregate_id varchar(64) NOT NULL,
  agg_type varchar(64) NOT NULL,
  version int NOT NULL,
  event_type varchar(128) NOT NULL,
  payload_json jsonb NOT NULL,
  metadata_json jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  UNIQUE(aggregate_id, version)
);
CREATE INDEX IF NOT EXISTS idx_events_agg ON events(aggregate_id, version);

-- snapshots
CREATE TABLE IF NOT EXISTS snapshots (
  aggregate_id varchar(64) NOT NULL,
  version int NOT NULL,
  state_json jsonb NOT NULL,
  created_at timestamptz NOT NULL,
  PRIMARY KEY(aggregate_id, version)
);
```

> 注意：实际工程应按服务命名空间化（如 order_events / order_snapshots）。
