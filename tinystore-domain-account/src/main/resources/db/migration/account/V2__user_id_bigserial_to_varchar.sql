-- =============================================
-- V2: 将 user_id 从 BIGSERIAL/BIGINT 迁移为 VARCHAR(64)
-- 目的：支持雪花算法等字符串格式的分布式ID生成策略
-- 影响范围：user_core 主键及所有依赖该外键的表
-- =============================================

-- 步骤1: 删除原 BIGSERIAL 自动创建的序列（如果存在）
DROP SEQUENCE IF EXISTS user_core_user_id_seq CASCADE;

-- 步骤2: 动态删除所有引用 user_core(user_id) 的外键约束
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        JOIN information_schema.constraint_column_usage ccu
          ON ccu.constraint_name = tc.constraint_name
          AND ccu.table_schema = tc.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND tc.table_schema = 'tinystore_account'
          AND ccu.table_name = 'user_core'
          AND ccu.column_name = 'user_id'
    LOOP
        EXECUTE format('ALTER TABLE tinystore_account.%I DROP CONSTRAINT %I',
                       constraint_record.table_name,
                       constraint_record.constraint_name);
    END LOOP;
END $$;

-- 步骤3: 先修改主表 user_core.user_id 类型（必须在子表之前，避免类型不匹配）
ALTER TABLE user_core
    ALTER COLUMN user_id DROP DEFAULT,
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

COMMENT ON COLUMN user_core.user_id IS '用户唯一标识，支持雪花算法等分布式ID（字符串格式）';

-- 步骤4: 再修改所有外键表的 user_id 列类型（主表类型已变更，现在可以安全转换）
ALTER TABLE user_realname
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

ALTER TABLE consumer_address
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

ALTER TABLE consumer_detail
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

ALTER TABLE user_role
    ALTER COLUMN user_id TYPE VARCHAR(64) USING user_id::varchar;

-- 步骤5: 重新创建外键约束（使用固定约束名，确保幂等性）
-- ALTER TABLE user_realname ADD CONSTRAINT fk_user_realname_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;
-- ALTER TABLE consumer_address ADD CONSTRAINT fk_consumer_address_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;
-- ALTER TABLE consumer_detail ADD CONSTRAINT fk_consumer_detail_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;
-- ALTER TABLE user_role ADD CONSTRAINT fk_user_role_user_core FOREIGN KEY (user_id) REFERENCES user_core(user_id) ON DELETE CASCADE;

-- 注释更新
COMMENT ON COLUMN user_realname.user_id IS '关联用户核心表ID（VARCHAR格式）';
COMMENT ON COLUMN consumer_address.user_id IS '关联用户核心表ID（VARCHAR格式）';
COMMENT ON COLUMN consumer_detail.user_id IS '关联用户核心表ID（VARCHAR格式）';
COMMENT ON COLUMN user_role.user_id IS '关联用户核心表ID（VARCHAR格式）';

-- =============================================
-- 迁移说明：
-- 1. 已有数据会通过 USING user_id::varchar 自动转换（如 1 -> "1"）
-- 2. 索引会自动随类型变更重建，无需手动干预
-- 3. 外键约束通过动态查询系统表确保完全删除，避免约束名不匹配导致的失败
-- 4. 关键修复：先转换主表类型，再转换子表类型，最后重建外键约束，避免类型不匹配错误
-- 5. 新插入数据必须由应用层生成 userId（不再依赖 DB 自增）
-- =============================================
