-- V2__pricing_rule.sql
-- Pricing rule table with JSONB content storage

CREATE TABLE pricing_rule (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    rule_code VARCHAR(200) NOT NULL,
    priority INTEGER NOT NULL,
    exclusive_group VARCHAR(100),
    product_id VARCHAR(100),
    category_id VARCHAR(100),
    user_tags JSONB,
    content JSONB NOT NULL,
    effective_time TIMESTAMP,
    expire_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_rule_code UNIQUE (tenant_id, rule_code)
);

-- Composite index for efficient rule queries
CREATE INDEX idx_pricing_rule_tenant_product ON pricing_rule(tenant_id, product_id, status, effective_time, expire_time);
CREATE INDEX idx_pricing_rule_tenant_category ON pricing_rule(tenant_id, category_id, status, effective_time, expire_time);
CREATE INDEX idx_pricing_rule_tenant_status_time ON pricing_rule(tenant_id, status, effective_time, expire_time);
CREATE INDEX idx_pricing_rule_priority ON pricing_rule(tenant_id, priority DESC);

-- GIN index for JSONB user_tags queries
CREATE INDEX idx_pricing_rule_user_tags ON pricing_rule USING GIN (user_tags);

-- Comments
COMMENT ON TABLE pricing_rule IS 'Pricing rules with polymorphic content stored as JSONB';
COMMENT ON COLUMN pricing_rule.tenant_id IS 'Tenant identifier - must be included in all WHERE clauses';
COMMENT ON COLUMN pricing_rule.content IS 'Rule-specific data serialized as JSONB (discount amount, percentage, etc.)';
COMMENT ON COLUMN pricing_rule.user_tags IS 'User tag filters for rule applicability (e.g., VIP, new_customer)';
COMMENT ON COLUMN pricing_rule.exclusive_group IS 'Rules in same group conflict - only highest priority applies';
COMMENT ON CONSTRAINT uk_tenant_rule_code ON pricing_rule IS 'Ensures unique rule code per tenant';
