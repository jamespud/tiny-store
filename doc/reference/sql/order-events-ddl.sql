-- order service ES tables (示意)
CREATE TABLE IF NOT EXISTS order_events (
  event_id uuid PRIMARY KEY,
  aggregate_id varchar(64) NOT NULL,
  version int NOT NULL,
  event_type varchar(128) NOT NULL,
  payload_json jsonb NOT NULL,
  metadata_json jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  UNIQUE(aggregate_id, version)
);
CREATE INDEX IF NOT EXISTS idx_order_events_agg ON order_events(aggregate_id, version);

CREATE TABLE IF NOT EXISTS order_snapshots (
  aggregate_id varchar(64) NOT NULL,
  version int NOT NULL,
  state_json jsonb NOT NULL,
  created_at timestamptz NOT NULL,
  PRIMARY KEY(aggregate_id, version)
);

CREATE TABLE IF NOT EXISTS order_projection_offset (
  projection_name varchar(128) NOT NULL,
  topic varchar(128) NOT NULL,
  partition int NOT NULL,
  offset bigint NOT NULL,
  PRIMARY KEY(projection_name, topic, partition)
);
