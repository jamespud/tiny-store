-- Review P1-2: claimed_by/claimed_at identify *an* owner, not *this* claim. After a stale claim is
-- reclaimed, the previous owner can still write PUBLISHED/FAILED onto the row the new owner holds, so a
-- stalled worker can silently override a live lease. A per-claim token makes completion updates fenced:
-- UPDATE ... WHERE event_id = ? AND claim_token = ? -- zero rows means the lease is gone and the old worker
-- must not touch the state.
ALTER TABLE tinystore_order.order_outbox
    ADD COLUMN IF NOT EXISTS claim_token VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_outbox_claim_token
    ON tinystore_order.order_outbox (claim_token);
