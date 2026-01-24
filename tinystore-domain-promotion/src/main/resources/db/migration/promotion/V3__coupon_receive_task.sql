CREATE TABLE IF NOT EXISTS promotion.coupon_receive_task (
	id UUID PRIMARY KEY,
	user_id VARCHAR(64) NOT NULL,
	coupon_id UUID NOT NULL REFERENCES promotion.coupon(id),
	idempotency_key VARCHAR(128) NOT NULL,
	request_hash VARCHAR(64) NOT NULL,
	status VARCHAR(32) NOT NULL,
	last_error TEXT,
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_receive_task_idempotency
	ON promotion.coupon_receive_task (idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_receive_task_user_coupon
	ON promotion.coupon_receive_task (user_id, coupon_id);

CREATE INDEX IF NOT EXISTS idx_coupon_receive_task_status_created
	ON promotion.coupon_receive_task (status, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_coupon_user_coupon
	ON promotion.user_coupon (user_id, coupon_id);

