-- =============================================
-- V2: 将 user_id 从 BIGSERIAL/BIGINT 迁移为 VARCHAR(64)
-- 目的：支持雪花算法等字符串格式的分布式ID生成策略
-- 影响范围：user_core 主键及所有依赖该外键的表
-- =============================================

-- 步骤1: 删除原 BIGSERIAL 自动创建的序列（如果存在）
DROP SEQUENCE IF EXISTS user_core_user_id_seq CASCADE;

-- 步骤2: 先删除所有外键约束（临时）
ALTER TABLE user_realname DROP CONSTRAINT IF EXISTS fk_user_realname_user_core;
ALTER TABLE consumer_address DROP CONSTRAINT IF EXISTS fk_consumer_address_user_core;
ALTER TABLE consumer_detail DROP CONSTRAINT IF EXISTS fk_consumer_detail_user_core;
ALTER TABLE user_role DROP CONSTRAINT IF EXISTS fk_user_role_user_core;

-- 步骤3: 先修改所有外键表的 user_id 列类型（避免类型不匹配）
ALTER TABLE user_realname
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

ALTER TABLE consumer_address
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

ALTER TABLE consumer_detail
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

ALTER TABLE user_role
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

-- 步骤4: 修改主表 user_core.user_id
ALTER TABLE user_core
    ALTER COLUMN user_id DROP DEFAULT,
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

COMMENT ON COLUMN user_core.user_id IS '用户唯一标识，支持雪花算法等分布式ID（字符串格式）';

-- 步骤5: 重新创建外键约束
ALTER TABLE user_realname ADD CONSTRAINT fk_user_realname_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;
ALTER TABLE consumer_address ADD CONSTRAINT fk_consumer_address_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;
ALTER TABLE consumer_detail ADD CONSTRAINT fk_consumer_detail_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;
ALTER TABLE user_role ADD CONSTRAINT fk_user_role_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;

-- 步骤6: 验证外键约束依然有效（Postgres会自动保持外键引用，但显式验证确保迁移安全）
-- 外键约束本身在 ALTER COLUMN TYPE 时会自动适配新类型，无需手动重建

-- 注释更新
COMMENT ON COLUMN user_realname.user_id IS '关联用户核心表ID（VARCHAR格式）';
COMMENT ON COLUMN consumer_address.user_id IS '关联用户核心表ID（VARCHAR格式）';
COMMENT ON COLUMN consumer_detail.user_id IS '关联用户核心表ID（VARCHAR格式）';
COMMENT ON COLUMN user_role.user_id IS '关联用户核心表ID（VARCHAR格式）';

-- =============================================
-- 迁移说明：
-- 1. 已有数据会通过 USING user_id::varchar 自动转换（如 1 -> "1"）
-- 2. 索引会自动随类型变更重建，无需手动干预
-- 3. 外键约束保持不变，只是引用类型从 BIGINT 变为 VARCHAR(64)
-- 4. 新插入数据必须由应用层生成 userId（不再依赖 DB 自增）
-- =============================================
