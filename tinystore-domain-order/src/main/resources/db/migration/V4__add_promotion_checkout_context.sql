ALTER TABLE order_main
    ADD COLUMN IF NOT EXISTS promotion_quote_id VARCHAR(64);

ALTER TABLE order_main
    ADD COLUMN IF NOT EXISTS promotion_input_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_order_main_promotion_quote_id
    ON order_main (promotion_quote_id);

