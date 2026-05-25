-- V7: Canonicalize inventory_reservation status vocabulary
-- RESERVED  -> PRE_DEDUCTED
-- COMMITTED -> CONFIRMED  (+ backfill confirmed_at)
-- RELEASED / EXPIRED remain unchanged
--
-- Safety: migration is idempotent; re-running will not corrupt already-migrated rows.

-- Step 1: Add confirmed_at column (nullable, for canonical CONFIRMED rows)
ALTER TABLE tinystore_inventory.inventory_reservation
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMPTZ;

-- Step 2: Add release_reason column if not already present
ALTER TABLE tinystore_inventory.inventory_reservation
    ADD COLUMN IF NOT EXISTS release_reason VARCHAR(255);

-- Step 3: Migrate COMMITTED -> CONFIRMED and backfill confirmed_at
UPDATE tinystore_inventory.inventory_reservation
SET status       = 'CONFIRMED',
    confirmed_at = updated_at
WHERE status = 'COMMITTED';

-- Step 4: Migrate RESERVED -> PRE_DEDUCTED
UPDATE tinystore_inventory.inventory_reservation
SET status = 'PRE_DEDUCTED'
WHERE status = 'RESERVED';

-- Step 5: Add index on (status, expire_at) for expiry scheduler query performance
CREATE INDEX IF NOT EXISTS idx_inv_reservation_status_expire_at
    ON tinystore_inventory.inventory_reservation (status, expire_at)
    WHERE status = 'PRE_DEDUCTED';
