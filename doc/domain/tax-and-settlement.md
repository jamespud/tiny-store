# 税费与结算领域设计

## 概述
税费与结算领域负责订单税费计算、发票管理、商户结算、以及相关合规处理。该领域与订单、支付、商品、账户等领域紧密协作，确保税费合规、结算准确及时。

## 核心概念

### 税费类型
- 增值税（VAT）
- 销售税（Sales Tax）
- 消费税（Excise Tax）
- 进口税（Import Duty）
- 地方附加税

### 结算类型
- 商户结算（Merchant Settlement）
- 平台佣金结算（Commission Settlement）
- 退款结算（Refund Settlement）
- 促销补贴结算（Promotion Subsidy Settlement）

## 聚合设计

### TaxPolicy 聚合
```java
public class TaxPolicy {
    private TaxPolicyId id;
    private TenantId tenantId;
    private List<TaxRule> rules;
    private ValidityPeriod period;
    public TaxAmount calculate(Order order) { /* ... */ }
}
```

### Settlement 聚合
```java
public class Settlement {
    private SettlementId id;
    private MerchantId merchantId;
    private List<OrderId> orders;
    private Money totalAmount;
    private SettlementStatus status;
    public void settle() { /* ... */ }
}
```

## 事件设计

### 发出事件
- TaxPolicyCreated
- TaxPolicyUpdated
- SettlementCreated
- SettlementCompleted
- InvoiceIssued

### 消费事件
- OrderConfirmed → 触发税费计算
- PaymentCompleted → 触发结算
- RefundProcessed → 触发结算调整

## 数据模型

### 税费策略表
```sql
CREATE TABLE tax_policies (
    policy_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    rules JSON NOT NULL,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP
);
```

### 结算表
```sql
CREATE TABLE settlements (
    settlement_id VARCHAR(64) PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status ENUM('pending','completed','failed') NOT NULL,
    created_at TIMESTAMP,
    completed_at TIMESTAMP
);
```

### 发票表
```sql
CREATE TABLE invoices (
    invoice_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    tax_amount DECIMAL(10,2),
    issued_at TIMESTAMP
);
```

## API 契约
- GET /api/v1/tax/calculate
- POST /api/v1/settlement/create
- GET /api/v1/invoice/{orderId}

## 监控与合规
- 税费计算准确率
- 结算及时率
- 发票开具合规性
- 退款结算异常告警

## 参考文档
- [架构总览](../architecture/overview.md)
- [术语表](../reference/glossary.md)
