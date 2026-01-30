-- V2__order_fulfillment.sql
-- 履约包裹表结构

-- 履约包裹表
CREATE TABLE IF NOT EXISTS tinystore_order.fulfillment_package (
    id BIGSERIAL PRIMARY KEY,
    package_id VARCHAR(64) NOT NULL UNIQUE,
    seller_id VARCHAR(64) NOT NULL,
    logistics_company_id VARCHAR(64),
    logistics_no VARCHAR(128),
    logistics_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    receiver_address_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP
);

CREATE INDEX idx_fulfillment_package_seller_id ON tinystore_order.fulfillment_package(seller_id);
CREATE INDEX idx_fulfillment_package_logistics_status ON tinystore_order.fulfillment_package(logistics_status);

-- 包裹与订单关联表（支持一个包裹关联多个订单、一个订单可拆分多个包裹）
CREATE TABLE IF NOT EXISTS tinystore_order.package_order_ref (
    id BIGSERIAL PRIMARY KEY,
    package_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(package_id, order_id)
);

CREATE INDEX idx_package_order_ref_package_id ON tinystore_order.package_order_ref(package_id);
CREATE INDEX idx_package_order_ref_order_id ON tinystore_order.package_order_ref(order_id);

COMMENT ON TABLE tinystore_order.fulfillment_package IS '履约包裹表';
COMMENT ON TABLE tinystore_order.package_order_ref IS '包裹与订单关联表';
