ALTER TABLE IF EXISTS tinystore_order.order_sub
    ADD COLUMN IF NOT EXISTS stock_pre_occupy_ids VARCHAR(2000);
