-- V8: Extend inventory_adjustment unique constraint to allow per-SKU rows
-- for a single (reason, reference_id). Multi-SKU refunds previously were
-- forced into a single audit row (bug); this enables one row per SKU.
--
-- Safety: the prior constraint uq_adjust_reason_ref guaranteed (reason, reference_id)
-- uniqueness, so existing rows are necessarily distinct under the longer key. No backfill needed.

ALTER TABLE tinystore_inventory.inventory_adjustment
    DROP CONSTRAINT IF EXISTS uq_adjust_reason_ref;

ALTER TABLE tinystore_inventory.inventory_adjustment
    ADD CONSTRAINT uq_adjust_reason_ref_shop_sku
    UNIQUE (reason, reference_id, shop_id, sku_id);
