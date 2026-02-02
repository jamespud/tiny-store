-- V5: Add scope_type to distinguish PLATFORM vs STORE coupons
-- Purpose: Enable scope-based validation while preserving existing coupon_type (discount calculation type)

ALTER TABLE promotion.coupon
    ADD COLUMN scope_type VARCHAR(32);

-- Backfill based on shop_id: STORE if shop_id exists, PLATFORM otherwise
UPDATE promotion.coupon
    SET scope_type = 'STORE'
    WHERE shop_id IS NOT NULL AND shop_id <> '';

UPDATE promotion.coupon
    SET scope_type = 'PLATFORM'
    WHERE scope_type IS NULL;

-- Enforce NOT NULL constraint after backfill
ALTER TABLE promotion.coupon
    ALTER COLUMN scope_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_coupon_scope_status
    ON promotion.coupon (scope_type, status);

COMMENT ON COLUMN promotion.coupon.scope_type IS 'Coupon scope: PLATFORM (cross-shop) or STORE (shop-specific)';
