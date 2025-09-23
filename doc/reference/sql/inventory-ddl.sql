-- inventory tables (示意)
CREATE TABLE IF NOT EXISTS inventory_item (
  sku_id varchar(64) NOT NULL,
  warehouse_id varchar(64) NOT NULL,
  on_hand bigint NOT NULL,
  available bigint NOT NULL,
  reserved bigint NOT NULL,
  committed bigint NOT NULL,
  PRIMARY KEY (sku_id, warehouse_id)
);
CREATE INDEX IF NOT EXISTS idx_inventory_item_sku ON inventory_item(sku_id);

CREATE TABLE IF NOT EXISTS reservation (
  reservation_id varchar(64) PRIMARY KEY,
  sku_id varchar(64) NOT NULL,
  warehouse_id varchar(64) NOT NULL,
  qty bigint NOT NULL,
  state varchar(16) NOT NULL,
  expires_at timestamptz NOT NULL,
  order_id varchar(64)
);
CREATE INDEX IF NOT EXISTS idx_reservation_sku_exp ON reservation(sku_id, expires_at);

CREATE TABLE IF NOT EXISTS stock_adjustment (
  adjustment_id varchar(64) PRIMARY KEY,
  sku_id varchar(64) NOT NULL,
  qty bigint NOT NULL,
  reason varchar(64) NOT NULL,
  created_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stock_adj_sku ON stock_adjustment(sku_id, created_at);
