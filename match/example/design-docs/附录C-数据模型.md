# 附录C 数据模型

## 1. 用户域

### users

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| email | VARCHAR | 邮箱，唯一 |
| phone | VARCHAR | 手机号，唯一 |
| password_hash | VARCHAR | 密码哈希 |
| nickname | VARCHAR | 昵称 |
| status | VARCHAR | PENDING_ACTIVATION/ACTIVE/FROZEN/CLOSED |
| roles | VARCHAR | 角色列表 |
| created_at | TIMESTAMP | 创建时间 |

### user_addresses

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| province | VARCHAR | 省 |
| city | VARCHAR | 市 |
| district | VARCHAR | 区 |
| detail | VARCHAR | 详细地址 |
| receiver_name | VARCHAR | 收货人 |
| receiver_phone | VARCHAR | 收货手机号 |
| default_address | BOOLEAN | 是否默认 |

## 2. 商品域

### product_spu

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR | 商品名称 |
| category_id | BIGINT | 类目 |
| brand_id | BIGINT | 品牌 |
| status | VARCHAR | 状态 |

### product_sku

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| spu_id | BIGINT | SPU |
| sku_code | VARCHAR | SKU 编码 |
| name | VARCHAR | SKU 名称 |
| price | DECIMAL(18,2) | 售价 |
| status | VARCHAR | DRAFT/ON_SHELF/OFF_SHELF/DELETED |
| specs_json | CLOB | 规格 JSON |

## 3. 库存域

### warehouses

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| code | VARCHAR | 仓库编码 |
| name | VARCHAR | 仓库名称 |
| province | VARCHAR | 省份 |
| priority | INT | 优先级 |

### inventory_stock

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| warehouse_id | BIGINT | 仓库 |
| sku_id | BIGINT | SKU ID |
| on_hand_stock | INT | 实物库存 |
| reserved_stock | INT | 预占库存 |
| warning_threshold | INT | 预警阈值 |

### stock_reservations

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | BIGINT | 订单 ID |
| sku_id | BIGINT | SKU |
| warehouse_id | BIGINT | 仓库 |
| quantity | INT | 数量 |
| status | VARCHAR | RESERVED/RELEASED/DEDUCTED |

## 4. 订单域

### orders

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_no | VARCHAR | 订单号，唯一 |
| external_order_no | VARCHAR | 客户端幂等号 |
| user_id | BIGINT | 用户 ID |
| status | VARCHAR | 订单状态 |
| item_total | DECIMAL(18,2) | 商品总额 |
| shipping_fee | DECIMAL(18,2) | 运费 |
| packaging_fee | DECIMAL(18,2) | 包装费 |
| discount_amount | DECIMAL(18,2) | 优惠金额 |
| points_deduction_amount | DECIMAL(18,2) | 积分抵扣金额 |
| payable_amount | DECIMAL(18,2) | 应付金额 |
| paid_amount | DECIMAL(18,2) | 实付金额 |
| created_at | TIMESTAMP | 创建时间 |

### order_items

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | BIGINT | 订单 ID |
| sku_id | BIGINT | SKU |
| product_name | VARCHAR | 商品快照名称 |
| sku_specs | CLOB | SKU 规格快照 |
| unit_price | DECIMAL(18,2) | 单价 |
| quantity | INT | 数量 |
| item_amount | DECIMAL(18,2) | 明细金额 |

## 5. 支付域

### payments

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| payment_no | VARCHAR | 支付单号 |
| order_id | BIGINT | 订单 ID |
| amount | DECIMAL(18,2) | 支付金额 |
| method | VARCHAR | 支付方式 |
| status | VARCHAR | CREATED/SUCCESS/FAILED/CLOSED |
| paid_at | TIMESTAMP | 支付时间 |

### refunds

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| refund_no | VARCHAR | 退款单号 |
| order_id | BIGINT | 订单 ID |
| paid_amount | DECIMAL(18,2) | 实付金额 |
| refund_amount | DECIMAL(18,2) | 退款金额 |
| status | VARCHAR | APPLIED/REVIEWED/ACCEPTED/REFUNDED/REJECTED |

### invoices

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | BIGINT | 订单 ID |
| invoice_no | VARCHAR | 发票号 |
| title | VARCHAR | 抬头 |
| amount | DECIMAL(18,2) | 发票金额 |
| tax_rate | DECIMAL(6,4) | 税率 |
| tax_amount | DECIMAL(18,2) | 税额 |
| status | VARCHAR | ISSUED/VOIDED |

## 6. 促销域

### coupons

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| coupon_code | VARCHAR | 优惠券编码 |
| type | VARCHAR | DISCOUNT/AMOUNT_OFF/THRESHOLD_OFF |
| discount_rate | DECIMAL(6,4) | 折扣率 |
| amount | DECIMAL(18,2) | 优惠金额 |
| threshold_amount | DECIMAL(18,2) | 使用门槛 |
| valid_from | TIMESTAMP | 生效时间 |
| valid_to | TIMESTAMP | 失效时间 |

## 7. 物流、积分、评价

### shipments

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | BIGINT | 订单 ID |
| shipment_no | VARCHAR | 发货单号 |
| status | VARCHAR | 物流状态 |
| tracking_no | VARCHAR | 运单号 |

### loyalty_points

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| points | INT | 积分数量 |
| available_points | INT | 可用积分 |
| expire_date | DATE | 过期日期 |
| source_type | VARCHAR | 来源 |

### reviews

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| order_id | BIGINT | 订单 ID |
| product_id | BIGINT | 商品 ID |
| rating | INT | 评分 1-5 |
| content | VARCHAR | 内容 |
| status | VARCHAR | 审核状态 |

