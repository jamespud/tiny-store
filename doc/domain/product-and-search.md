# 商品与搜索领域设计

## 概述
商品与搜索领域负责商品信息建模、类目管理、属性扩展、以及高性能商品检索。该领域为定价、促销、库存、订单等业务提供商品基础数据和搜索能力。

## 核心概念

### 商品模型
- SKU（库存单位）
- SPU（标准产品单元）
- 类目（Category）
- 属性（Attribute）
- 标签（Tag）
- 上下架状态（Listing Status）

### 搜索能力
- 关键词检索
- 类目筛选
- 属性过滤
- 排序（价格、销量、上新、评分）
- 分页与高并发查询

## 聚合设计

### Product 聚合
```java
public class Product {
    private SkuId skuId;
    private SpuId spuId;
    private TenantId tenantId;
    private String name;
    private CategoryId categoryId;
    private Map<String, String> attributes;
    private List<String> tags;
    private ListingStatus status;
    // ...
}
```

### SearchIndex 聚合
```java
public class SearchIndex {
    private IndexId id;
    private TenantId tenantId;
    private List<SkuId> skuIds;
    private Map<String, Object> indexFields;
    public List<SkuId> search(SearchQuery query) { /* ... */ }
}
```

## 事件设计

### 发出事件
- ProductCreated
- ProductUpdated
- ProductListed
- ProductDelisted
- SearchIndexUpdated

### 消费事件
- InventoryChanged → 更新可售状态
- PricePolicyUpdated → 更新索引价格
- PromotionActivated → 更新促销标签

## 数据模型

### 商品表
```sql
CREATE TABLE products (
    sku_id VARCHAR(64) PRIMARY KEY,
    spu_id VARCHAR(64),
    tenant_id VARCHAR(32),
    name VARCHAR(255),
    category_id VARCHAR(64),
    attributes JSON,
    tags JSON,
    status ENUM('listed','delisted'),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 搜索索引表（简化示例）
```sql
CREATE TABLE search_index (
    index_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(32),
    sku_id VARCHAR(64),
    index_fields JSON,
    updated_at TIMESTAMP
);
```

## API 契约
- GET /api/v1/products/{skuId}
- GET /api/v1/products/search?keyword=&categoryId=&attributes=&sort=&page=
- POST /api/v1/products/batch

## 性能与扩展
- 支持分布式索引（如 Elasticsearch）
- 热门商品缓存
- 属性扩展与动态标签
- 搜索结果高并发分页

## 监控指标
- 商品索引同步延迟
- 搜索QPS与响应时间
- 热门关键词统计
- 商品上下架成功率

## 参考文档
- [架构总览](../architecture/overview.md)
- [术语表](../reference/glossary.md)
