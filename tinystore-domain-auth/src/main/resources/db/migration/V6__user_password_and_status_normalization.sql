-- Add password_hash column for mall_user and normalize status values to ACTIVE/FROZEN
ALTER TABLE IF EXISTS mall_user
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Normalize existing status values
UPDATE mall_user SET status = 'ACTIVE' WHERE LOWER(status) = 'normal';
UPDATE mall_user SET status = 'FROZEN' WHERE LOWER(status) = 'frozen';

-- Optional: ensure rt_version has a minimum of 1
UPDATE mall_user SET rt_version = 1 WHERE rt_version IS NULL OR rt_version < 1;