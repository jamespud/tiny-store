-- Review round 3 (P0): the durable idempotency row must exist as a *claim* from the start of create-trade,
-- not only as a success record written at the end.
--
-- Before this, the row was inserted last, inside the business transaction, so during execution the mutual
-- exclusion for one Idempotency-Key lived only in Redis. If Redis lost the key mid-flight (restart / flush)
-- two replicas could both run the saga -- both calling promotion and BOTH pre-deducting inventory -- and the
-- primary key on this table only guaranteed that one of the two *success records* survived.
--
-- Now createTrade inserts the row as state=PROCESSING before any external effect, inside the same
-- transaction (INSERT ... ON CONFLICT (idempotency_key) DO NOTHING). PostgreSQL makes a concurrent same-key
-- insert wait for the open transaction, so only one saga runs; the winner rewrites the row to state=COMMITTED
-- plus the response in the same transaction. trade_id/response_json are therefore not known when the claim is
-- written and must be nullable.
ALTER TABLE tinystore_order.trade_idempotency_record
    ALTER COLUMN trade_id DROP NOT NULL,
    ALTER COLUMN response_json DROP NOT NULL;
