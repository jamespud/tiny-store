-- V13: Canonicalize shop_order inventory_status vocabulary and backfill reservation refs JSON
-- LOCKED   -> PRE_DEDUCTED
-- DEDUCTED -> CONFIRMED
-- RELEASED remains unchanged
-- UNLOCKED remains unchanged (initial state compatibility)
--
-- Also backfills inventory_reservation_refs_json from inventory_pre_occupy_ids_json
-- for existing rows so the read-path fallback degrades gracefully.

-- Step 1: Migrate LOCKED -> PRE_DEDUCTED
UPDATE tinystore_order.shop_order
SET inventory_status = 'PRE_DEDUCTED'
WHERE inventory_status = 'LOCKED';

-- Step 2: Migrate DEDUCTED -> CONFIRMED
UPDATE tinystore_order.shop_order
SET inventory_status = 'CONFIRMED'
WHERE inventory_status = 'DEDUCTED';

-- Step 3: Backfill inventory_reservation_refs_json from inventory_pre_occupy_ids_json
--         Only for rows that have pre-occupy data and not yet have reservation refs
--         The JSON structure is compatible: OccupyPair {shopId, skuId, occupyId}
--         maps to ReservationRef {shopId, skuId, reservationId} (occupyId == reservationId)
UPDATE tinystore_order.shop_order
SET inventory_reservation_refs_json = inventory_pre_occupy_ids_json
WHERE inventory_pre_occupy_ids_json IS NOT NULL
  AND inventory_pre_occupy_ids_json <> ''
  AND inventory_pre_occupy_ids_json <> '[]'
  AND (inventory_reservation_refs_json IS NULL OR inventory_reservation_refs_json = '');
