-- read models (示意)
CREATE TABLE IF NOT EXISTS order_summary_view (
  order_id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  total_amount bigint NOT NULL,
  payment_status varchar(32),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_order_summary_user ON order_summary_view(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_summary_status ON order_summary_view(status, created_at DESC);

CREATE TABLE IF NOT EXISTS order_timeline_view (
  order_id varchar(64) NOT NULL,
  event_type varchar(64) NOT NULL,
  version int NOT NULL,
  occurred_at timestamptz NOT NULL,
  PRIMARY KEY(order_id, event_type, version)
);

CREATE TABLE IF NOT EXISTS payment_status_view (
  payment_id varchar(64) PRIMARY KEY,
  order_id varchar(64) NOT NULL,
  channel varchar(32) NOT NULL,
  status varchar(32) NOT NULL,
  amount bigint NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payment_status_order ON payment_status_view(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_status_state ON payment_status_view(status, updated_at DESC);
