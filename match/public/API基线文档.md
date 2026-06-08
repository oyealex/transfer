# API 基线文档

## 1. 冻结规则

REST API 契约视为冻结后，后续黑盒测试、实际代码和评分脚本均以本文档为准。

以下内容不得修改：

1. URL 路径。
2. HTTP Method。
3. Request Header。
4. Query 参数名称和类型。
5. Path Variable 名称和类型。
6. Request Body 字段名和类型。
7. Response Body 字段名和类型。
8. 成功 HTTP 状态码。
9. 错误响应结构。

所有业务 API 固定使用：

```text
/api/v1/
```

如果设计文档中的 API 版本描述与本文档不一致，以本文档为准。不得修改代码或测试去使用其他业务 API 前缀。

## 2. OpenAPI 基线摘要

OpenAPI 基线版本：

```yaml
openapi: 3.0.3
info:
  title: ShopHub API
  version: 1.0.0
servers:
  - url: /api/v1
```

本文档中的接口索引、状态码和错误码是 OpenAPI 基线的可读版本。黑盒用例按本文档冻结的路径、方法、认证和成功状态执行。

## 3. API 索引

### 用户

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/users/register` | 匿名 | 201 |
| POST | `/api/v1/users/activate` | 匿名 | 200 |
| POST | `/api/v1/users/login` | 匿名 | 200 |
| GET | `/api/v1/users/me` | USER | 200 |
| POST | `/api/v1/users/addresses` | USER | 201 |
| GET | `/api/v1/users/addresses` | USER | 200 |
| PUT | `/api/v1/users/addresses/{addressId}` | USER | 200 |
| DELETE | `/api/v1/users/addresses/{addressId}` | USER | 204 |
| POST | `/api/v1/admin/users/{userId}/freeze` | ADMIN | 200 |
| POST | `/api/v1/admin/users/{userId}/unfreeze` | ADMIN | 200 |

### 商品

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/admin/products/spu` | ADMIN | 201 |
| POST | `/api/v1/admin/products/sku` | ADMIN | 201 |
| POST | `/api/v1/admin/products/sku/{skuId}/on-shelf` | ADMIN | 200 |
| POST | `/api/v1/admin/products/sku/{skuId}/off-shelf` | ADMIN | 200 |
| GET | `/api/v1/products` | 匿名 | 200 |
| GET | `/api/v1/products/search` | 匿名 | 200 |
| GET | `/api/v1/products/{skuId}` | 匿名 | 200 |
| GET | `/api/v1/categories/tree` | 匿名 | 200 |

### 库存

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/admin/warehouses` | ADMIN | 201 |
| POST | `/api/v1/admin/inventory/inbound` | ADMIN | 201 |
| POST | `/api/v1/admin/inventory/outbound` | ADMIN | 201 |
| GET | `/api/v1/inventory/sku/{skuId}` | 匿名 | 200 |
| POST | `/api/v1/inventory/check` | 匿名 | 200 |
| POST | `/api/v1/admin/inventory/adjustments` | ADMIN | 201 |
| GET | `/api/v1/admin/inventory/warnings` | ADMIN | 200 |

### 购物车

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/cart/items` | USER | 201 |
| GET | `/api/v1/cart` | USER | 200 |
| PUT | `/api/v1/cart/items/{skuId}` | USER | 200 |
| DELETE | `/api/v1/cart/items/{skuId}` | USER | 204 |
| DELETE | `/api/v1/cart` | USER | 204 |
| POST | `/api/v1/cart/estimate` | USER | 200 |

### 订单

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/orders/create` | USER | 201 |
| GET | `/api/v1/orders/{orderId}` | USER | 200 |
| GET | `/api/v1/orders` | USER | 200 |
| POST | `/api/v1/orders/{orderId}/cancel` | USER | 200 |
| POST | `/api/v1/admin/orders/{orderId}/cancel-review` | ADMIN | 200 |
| POST | `/api/v1/orders/batch` | USER | 200 |
| GET | `/api/v1/orders/verify-purchase` | USER/ADMIN | 200 |
| GET | `/api/v1/admin/orders/statistics/sales` | ADMIN | 200 |

### 支付、退款、发票

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/payment/pay` | USER | 201 |
| POST | `/api/v1/payment/callback` | 签名 | 200 |
| GET | `/api/v1/payment/{paymentNo}` | USER | 200 |
| POST | `/api/v1/refunds/apply` | USER | 201 |
| POST | `/api/v1/admin/refunds/{refundId}/review` | ADMIN | 200 |
| POST | `/api/v1/admin/refunds/{refundId}/warehouse-accept` | ADMIN | 200 |
| GET | `/api/v1/refunds/{refundId}` | USER | 200 |
| POST | `/api/v1/invoices` | USER | 201 |
| GET | `/api/v1/invoices/order/{orderId}` | USER | 200 |
| POST | `/api/v1/admin/settlements/batches` | ADMIN | 201 |

### 促销、物流、积分、评价

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/admin/promotions/coupons` | ADMIN | 201 |
| POST | `/api/v1/promotions/coupons/claim` | USER | 201 |
| GET | `/api/v1/promotions/coupons/my` | USER | 200 |
| POST | `/api/v1/promotions/calculate` | USER | 200 |
| POST | `/api/v1/admin/promotions/full-reductions` | ADMIN | 201 |
| POST | `/api/v1/admin/promotions/seckill` | ADMIN | 201 |
| GET | `/api/v1/logistics/order/{orderId}` | USER | 200 |
| POST | `/api/v1/admin/logistics/shipments/{shipmentId}/pick` | ADMIN | 200 |
| POST | `/api/v1/admin/logistics/shipments/{shipmentId}/print-label` | ADMIN | 200 |
| POST | `/api/v1/admin/logistics/shipments/{shipmentId}/outbound` | ADMIN | 200 |
| POST | `/api/v1/logistics/callback` | 签名 | 200 |
| POST | `/api/v1/admin/logistics/freight-templates` | ADMIN | 201 |
| GET | `/api/v1/loyalty/points` | USER | 200 |
| POST | `/api/v1/loyalty/points/estimate-redeem` | USER | 200 |
| GET | `/api/v1/loyalty/points/history` | USER | 200 |
| GET | `/api/v1/loyalty/member-level` | USER | 200 |
| POST | `/api/v1/admin/loyalty/points/expire` | ADMIN | 200 |
| POST | `/api/v1/reviews` | USER | 201 |
| POST | `/api/v1/reviews/{reviewId}/append` | USER | 201 |
| GET | `/api/v1/reviews/product/{productId}` | 匿名 | 200 |
| GET | `/api/v1/reviews/my` | USER | 200 |
| POST | `/api/v1/admin/reviews/{reviewId}/approve` | ADMIN | 200 |
| POST | `/api/v1/admin/reviews/{reviewId}/reject` | ADMIN | 200 |

### 黑盒测试支撑管理接口

以下接口为黑盒测试支撑管理接口，均通过公开 REST 路径调用并使用 ADMIN 认证。用例级隔离由测试 harness 完成，不提供业务 reset/bootstrap API。

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| PUT | `/api/v1/admin/system/configs/{key}` | ADMIN | 200 |
| GET | `/api/v1/admin/system/configs/{key}` | ADMIN | 200 |
| POST | `/api/v1/admin/ops/fault-injections` | ADMIN | 200 |
| DELETE | `/api/v1/admin/ops/fault-injections` | ADMIN | 204 |
| GET | `/api/v1/admin/events/failures` | ADMIN | 200 |
| GET | `/api/v1/admin/notifications` | ADMIN | 200 |
| PUT | `/api/v1/admin/system/clock` | ADMIN | 200 |
| DELETE | `/api/v1/admin/system/clock` | ADMIN | 200 |
| POST | `/api/v1/admin/orders/timeout-cancel` | ADMIN | 200 |

## 4. 错误码

### 通用错误码

| 错误码 | HTTP | 说明 |
|--------|------|------|
| VALIDATION_FAILED | 400 | 请求参数校验失败 |
| RESOURCE_NOT_FOUND | 404 | 资源不存在 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| CONFLICT | 409 | 状态冲突或重复请求 |
| RATE_LIMITED | 429 | 触发限流 |
| INTERNAL_ERROR | 500 | 系统内部错误 |

### 业务错误码

| 错误码 | HTTP | 说明 |
|--------|------|------|
| USER_NOT_ACTIVE | 403 | 用户未激活 |
| USER_FROZEN | 403 | 用户被冻结 |
| PRODUCT_NOT_FOR_SALE | 400 | 商品不可销售 |
| INVENTORY_NOT_ENOUGH | 400 | 库存不足 |
| ORDER_INVALID_AMOUNT | 400 | 订单金额非法 |
| ORDER_RISK_REJECTED | 400 | 风控拒绝订单 |
| ORDER_STATUS_CONFLICT | 409 | 订单状态不允许操作 |
| PAYMENT_AMOUNT_MISMATCH | 400 | 支付金额与订单应付金额不一致 |
| COUPON_EXPIRED | 400 | 优惠券已过期 |
| REFUND_WAITING_WAREHOUSE_ACCEPT | 409 | 退款等待仓库验收 |
| REVIEW_PURCHASE_REQUIRED | 403 | 未购买商品不可评价 |
| INVOICE_AMOUNT_EXCEEDED | 400 | 开票金额超过剩余可开金额 |
