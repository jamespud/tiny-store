# Inventory 模块数据库结构

## 表: inventory_stock
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 内部主键 |
| shop_id | VARCHAR | 店铺ID |
| sku_id | VARCHAR | SKU ID |
| total_quantity | BIGINT | 总库存 |
| reserved_quantity | BIGINT | 已预留库存 |
| version | BIGINT | 乐观锁版本 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

唯一: uk_stock_shop_sku (shop_id, sku_id)

## 表: inventory_reservation
| 字段 | 类型 | 说明 |
|------|------|------|
| reservation_id | VARCHAR | 预留ID 主键 |
| shop_id | VARCHAR | 店铺ID |
| sku_id | VARCHAR | SKU ID |
| quantity | BIGINT | 预留数量 |
| state | VARCHAR | 状态(PENDING/CONFIRMED/RELEASED/EXPIRED/FAILED) |
| expire_at | TIMESTAMP | 过期时间 |
| operation_id | VARCHAR | 幂等操作ID(可空, 唯一) |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| version | BIGINT | 版本(状态 CAS) |
| release_reason | VARCHAR | 释放原因 |

索引: idx_resv_state_expire(state, expire_at)
唯一: uk_resv_operation(operation_id)

## 说明
- available = total_quantity - reserved_quantity 只在领域层计算
- 调整 total 时需保证 (total - reserved) 不为负
- 过期扫描通过 state=PENDING AND expire_at < now

