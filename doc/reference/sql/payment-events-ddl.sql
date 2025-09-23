-- payment service ES tables (示意)
CREATE TABLE IF NOT EXISTS payment_events (
  event_id uuid PRIMARY KEY,
  aggregate_id varchar(64) NOT NULL,
  version int NOT NULL,
  event_type varchar(128) NOT NULL,
  payload_json jsonb NOT NULL,
  metadata_json jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  UNIQUE(aggregate_id, version)
);
CREATE INDEX IF NOT EXISTS idx_payment_events_agg ON payment_events(aggregate_id, version);

CREATE TABLE IF NOT EXISTS payment_snapshots (
  aggregate_id varchar(64) NOT NULL,
  version int NOT NULL,
  state_json jsonb NOT NULL,
  created_at timestamptz NOT NULL,
  PRIMARY KEY(aggregate_id, version)
);

CREATE TABLE IF NOT EXISTS payment_idempotency (
  idempotency_key varchar(128) PRIMARY KEY,
  response_hash varchar(128),
  created_at timestamptz NOT NULL
);
