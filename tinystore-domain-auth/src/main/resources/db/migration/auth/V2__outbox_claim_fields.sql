-- Outbox claim 协议（auth 侧，镜像 order 域）：多副本下先原子认领再发布。
--
-- 原先 fetchUnpublished/fetchConsentChangedUnpublished 只做 SELECT，
-- 两个副本会取到同一批未发布行并各自发布一次；claimed_by / claimed_at 让认领成为一次原子写。

ALTER TABLE auth_outbox
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_auth_outbox_unpublished_occurred_at
    ON auth_outbox (occurred_at)
    WHERE published = false;
