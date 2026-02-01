-- V1__init_product_tables.sql
-- Initial product domain tables with multi-tenancy support

-- Product table: Core product information
CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    name VARCHAR(500) NOT NULL,
    status VARCHAR(50) NOT NULL,
    category_id VARCHAR(100),
    description TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for product table
CREATE INDEX idx_product_tenant_category_status ON product(tenant_id, category_id, status);
CREATE INDEX idx_product_tenant_status ON product(tenant_id, status);

-- SKU table: Stock Keeping Unit with spec combinations
CREATE TABLE sku (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    spec_combination VARCHAR(1000) NOT NULL,
    price DECIMAL(19, 4),
    stock INTEGER DEFAULT 0,
    bar_code VARCHAR(200),
    status VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_spec UNIQUE (product_id, spec_combination)
);

-- Indexes for sku table
CREATE INDEX idx_sku_product_spec ON sku(product_id, spec_combination);
CREATE INDEX idx_sku_tenant_product ON sku(tenant_id, product_id);

-- Category association table (optional, for many-to-many if needed)
CREATE TABLE product_category (
    product_id VARCHAR(100) NOT NULL,
    category_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (product_id, category_id)
);

CREATE INDEX idx_product_category_tenant ON product_category(tenant_id);

-- Tag association table
CREATE TABLE product_tag (
    product_id VARCHAR(100) NOT NULL,
    tag VARCHAR(200) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (product_id, tag)
);

CREATE INDEX idx_product_tag_tenant ON product_tag(tenant_id);

-- Comments documenting tenant isolation requirement
COMMENT ON COLUMN product.tenant_id IS 'Tenant identifier - must be included in all WHERE clauses';
COMMENT ON COLUMN sku.tenant_id IS 'Tenant identifier - must be included in all WHERE clauses';
COMMENT ON CONSTRAINT uk_product_spec ON sku IS 'Ensures unique spec combination per product';
