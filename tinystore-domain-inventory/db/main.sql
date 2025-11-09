CREATE TABLE product_category
(
    category_id   BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT      NOT NULL DEFAULT 0,
    category_name VARCHAR(64) NOT NULL,
    level         SMALLINT    NOT NULL,
    sort          INT         NOT NULL DEFAULT 0,
    icon_url      VARCHAR(255),
    is_leaf       SMALLINT    NOT NULL DEFAULT 1,      -- 1:是叶子节点,0:否
    status        SMALLINT    NOT NULL DEFAULT 1,      -- 0:禁用,1:启用
    create_time   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_level CHECK (level BETWEEN 1 AND 3) -- 最多三级分类
);
COMMENT ON TABLE product_category IS '商品多级分类表（平台统一维护）';
COMMENT ON COLUMN product_category.parent_id IS '父分类ID（0为顶级）';
COMMENT ON COLUMN product_category.level IS '分类层级（1-顶级,2-二级,3-三级）';
COMMENT ON COLUMN product_category.is_leaf IS '是否叶子节点（仅叶子节点可绑定商品）';

-- 索引
CREATE INDEX idx_product_category_parent_id ON product_category (parent_id);
CREATE INDEX idx_product_category_level_status ON product_category (level, status);

CREATE TABLE product_brand
(
    brand_id     BIGSERIAL PRIMARY KEY,
    brand_name   VARCHAR(64)  NOT NULL UNIQUE,
    logo_url     VARCHAR(255) NOT NULL,
    brand_desc   VARCHAR(512),
    first_letter CHAR(1),
    sort         INT          NOT NULL DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1 -- 0:禁用,1:启用
);
COMMENT ON TABLE product_brand IS '商品品牌表（如苹果、华为）';
COMMENT ON COLUMN product_brand.first_letter IS '品牌首字母（用于字母筛选）';

-- 索引
CREATE INDEX idx_product_brand_first_letter ON product_brand (first_letter);

CREATE TABLE product_spu
(
    spu_id        BIGSERIAL PRIMARY KEY,
    merchant_id   BIGINT       NOT NULL,
    category_id   BIGINT       NOT NULL,           -- 关联product_category.category_id
    brand_id      BIGINT       NOT NULL,           -- 关联product_brand.brand_id
    spu_name      VARCHAR(128) NOT NULL,
    spu_desc      TEXT,
    main_image    VARCHAR(255) NOT NULL,
    spec_json     JSONB,                           -- 规格模板（记录该SPU的规格组合）
    audit_status  SMALLINT     NOT NULL DEFAULT 0, -- 0:待审核,1:通过,2:驳回
    sale_status   SMALLINT     NOT NULL DEFAULT 0, -- 0:下架,1:上架
    reject_reason VARCHAR(255),
    create_time   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_spu_category FOREIGN KEY (category_id) REFERENCES product_category (category_id),
    CONSTRAINT fk_spu_brand FOREIGN KEY (brand_id) REFERENCES product_brand (brand_id)
);
COMMENT ON TABLE product_spu IS '标准化产品单元（抽象商品信息）';
COMMENT ON COLUMN product_spu.spec_json IS '规格模板JSON（如{"颜色":["黑","白"],"内存":["128G"]}）';

-- 索引
CREATE INDEX idx_product_spu_merchant_id ON product_spu (merchant_id);
CREATE INDEX idx_product_spu_category_brand ON product_spu (category_id, brand_id);
CREATE INDEX idx_product_spu_audit_sale ON product_spu (audit_status, sale_status);

CREATE TABLE product_spec
(
    spec_id   BIGSERIAL PRIMARY KEY,
    spec_name VARCHAR(32) NOT NULL UNIQUE,
    sort      INT         NOT NULL DEFAULT 0 -- 规格展示顺序
);
COMMENT ON TABLE product_spec IS '商品规格名称表（如颜色、内存、尺码）';

CREATE TABLE product_spec_value
(
    spec_value_id BIGSERIAL PRIMARY KEY,
    spec_id       BIGINT      NOT NULL,           -- 关联product_spec.spec_id
    value_name    VARCHAR(32) NOT NULL,
    value_image   VARCHAR(255),                   -- 规格值图片（如色卡图）
    sort          INT         NOT NULL DEFAULT 0, -- 同规格下的展示顺序
    CONSTRAINT fk_spec_value_spec FOREIGN KEY (spec_id) REFERENCES product_spec (spec_id) ON DELETE CASCADE
);
COMMENT ON TABLE product_spec_value IS '规格具体值表（如颜色=黑色、内存=128G）';

-- 索引
CREATE INDEX idx_product_spec_value_spec_id ON product_spec_value (spec_id);

CREATE TABLE spu_spec_relation
(
    relation_id BIGSERIAL PRIMARY KEY,
    spu_id      BIGINT NOT NULL,                    -- 关联product_spu.spu_id
    spec_id     BIGINT NOT NULL,                    -- 关联product_spec.spec_id
    sort        INT    NOT NULL DEFAULT 0,          -- 该SPU下的规格展示顺序
    CONSTRAINT fk_spu_spec_spu FOREIGN KEY (spu_id) REFERENCES product_spu (spu_id) ON DELETE CASCADE,
    CONSTRAINT fk_spu_spec_spec FOREIGN KEY (spec_id) REFERENCES product_spec (spec_id),
    CONSTRAINT uk_spu_spec UNIQUE (spu_id, spec_id) -- 避免SPU重复关联同一规格
);
COMMENT ON TABLE spu_spec_relation IS 'SPU与规格的关联表（绑定商品包含的规格）';

-- 索引
CREATE INDEX idx_spu_spec_relation_spu_id ON spu_spec_relation (spu_id);

CREATE TABLE spu_spec_relation
(
    relation_id BIGSERIAL PRIMARY KEY,
    spu_id      BIGINT NOT NULL,                    -- 关联product_spu.spu_id
    spec_id     BIGINT NOT NULL,                    -- 关联product_spec.spec_id
    sort        INT    NOT NULL DEFAULT 0,          -- 该SPU下的规格展示顺序
    CONSTRAINT fk_spu_spec_spu FOREIGN KEY (spu_id) REFERENCES product_spu (spu_id) ON DELETE CASCADE,
    CONSTRAINT fk_spu_spec_spec FOREIGN KEY (spec_id) REFERENCES product_spec (spec_id),
    CONSTRAINT uk_spu_spec UNIQUE (spu_id, spec_id) -- 避免SPU重复关联同一规格
);
COMMENT ON TABLE spu_spec_relation IS 'SPU与规格的关联表（绑定商品包含的规格）';

-- 索引
CREATE INDEX idx_spu_spec_relation_spu_id ON spu_spec_relation (spu_id);

CREATE TABLE product_sku
(
    sku_id         BIGSERIAL PRIMARY KEY,
    spu_id         BIGINT         NOT NULL,                        -- 关联product_spu.spu_id
    spec_value_ids VARCHAR(128)   NOT NULL,                        -- 规格值ID组合（逗号分隔，如"1,5"）
    sku_name       VARCHAR(128)   NOT NULL,
    price          NUMERIC(10, 2) NOT NULL,                        -- 销售价（元）
    market_price   NUMERIC(10, 2),                                 -- 市场价（划线价）
    stock          INT            NOT NULL DEFAULT 0,              -- 可售库存
    lock_stock     INT            NOT NULL DEFAULT 0,              -- 锁定库存（下单未支付）
    image_url      VARCHAR(255),                                   -- SKU图片（无则用SPU主图）
    barcode        VARCHAR(64),                                    -- 商品条形码
    status         SMALLINT       NOT NULL DEFAULT 1,              -- 0:禁用,1:正常
    create_time    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sku_spu FOREIGN KEY (spu_id) REFERENCES product_spu (spu_id) ON DELETE CASCADE,
    CONSTRAINT uk_spu_spec_values UNIQUE (spu_id, spec_value_ids), -- 规格组合唯一
    CONSTRAINT chk_price CHECK (price >= 0),
    CONSTRAINT chk_stock CHECK (stock >= 0),
    CONSTRAINT chk_lock_stock CHECK (lock_stock >= 0)
);
COMMENT ON TABLE product_sku IS '库存单元（具体规格商品，如iPhone 15 128G 黑色）';
COMMENT ON COLUMN product_sku.spec_value_ids IS '规格值ID组合（对应product_spec_value.spec_value_id）';

-- 索引
CREATE INDEX idx_product_sku_spu_id ON product_sku (spu_id);
CREATE INDEX idx_product_sku_status_stock ON product_sku (status, stock);

CREATE TABLE product_image
(
    image_id  BIGSERIAL PRIMARY KEY,
    ref_type  SMALLINT     NOT NULL,           -- 1:SPU,2:SKU
    ref_id    BIGINT       NOT NULL,           -- 关联SPU_ID或SKU_ID
    image_url VARCHAR(255) NOT NULL,
    sort      INT          NOT NULL DEFAULT 0, -- 图片展示顺序
    is_main   SMALLINT     NOT NULL DEFAULT 0  -- 1:主图（仅SPU有）
);
COMMENT ON TABLE product_image IS 'SPU/SKU的多图存储表（支持轮播）';
COMMENT ON COLUMN product_image.ref_type IS '关联类型（1=SPU,2=SKU）';
COMMENT ON COLUMN product_image.ref_id IS '关联ID（对应spu_id或sku_id）';

-- 索引
CREATE INDEX idx_product_image_ref_type_id ON product_image (ref_type, ref_id);