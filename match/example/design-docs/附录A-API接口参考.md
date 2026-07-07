# 附录A API 接口参考

## 1. 通用约定

所有业务接口统一使用 `/api/v1/` 前缀。

认证方式：

```text
Authorization: Bearer <jwt>
```

管理接口需要 `ADMIN` 角色。

错误响应：

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "traceId": "trace-id",
  "details": {}
}
```

## 2. 用户接口

### 2.1 注册用户

```text
POST /api/v1/users/register
```

Request:

```json
{
  "email": "user@example.com",
  "phone": "13800000000",
  "password": "Password123!",
  "nickname": "Alice"
}
```

Response 201:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "status": "PENDING_ACTIVATION"
}
```

### 2.2 激活邮箱

```text
POST /api/v1/users/activate
```

Request:

```json
{
  "token": "activation-token"
}
```

Response 200:

```json
{
  "userId": 1,
  "status": "ACTIVE"
}
```

### 2.3 登录

```text
POST /api/v1/users/login
```

Response 200:

```json
{
  "token": "jwt",
  "userId": 1,
  "roles": ["USER"]
}
```

### 2.4 用户和地址

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| GET | `/api/v1/users/me` | USER | 当前用户 |
| POST | `/api/v1/users/addresses` | USER | 新增地址 |
| GET | `/api/v1/users/addresses` | USER | 地址列表 |
| PUT | `/api/v1/users/addresses/{addressId}` | USER | 修改地址 |
| DELETE | `/api/v1/users/addresses/{addressId}` | USER | 删除地址 |
| POST | `/api/v1/admin/users/{userId}/freeze` | ADMIN | 冻结用户 |
| POST | `/api/v1/admin/users/{userId}/unfreeze` | ADMIN | 解冻用户 |

## 3. 商品接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/admin/products/spu` | ADMIN | 创建 SPU |
| POST | `/api/v1/admin/products/sku` | ADMIN | 创建 SKU |
| POST | `/api/v1/admin/products/sku/{skuId}/on-shelf` | ADMIN | SKU 上架 |
| POST | `/api/v1/admin/products/sku/{skuId}/off-shelf` | ADMIN | SKU 下架 |
| GET | `/api/v1/products` | 匿名 | 商品列表 |
| GET | `/api/v1/products/search` | 匿名 | 商品搜索 |
| GET | `/api/v1/products/{skuId}` | 匿名 | 商品详情 |
| GET | `/api/v1/categories/tree` | 匿名 | 类目树 |

创建 SKU Request:

```json
{
  "spuId": 1,
  "skuCode": "SKU-001",
  "name": "商品名称",
  "price": 199.00,
  "specs": {"color": "black", "size": "M"}
}
```

商品详情 Response:

```json
{
  "skuId": 1,
  "spuId": 1,
  "name": "商品名称",
  "price": 199.00,
  "status": "ON_SHELF",
  "stockSummary": {
    "availableStock": 100,
    "reservedStock": 0
  }
}
```

## 4. 库存接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/admin/warehouses` | ADMIN | 创建仓库 |
| POST | `/api/v1/admin/inventory/inbound` | ADMIN | 入库 |
| POST | `/api/v1/admin/inventory/outbound` | ADMIN | 手工出库 |
| GET | `/api/v1/inventory/sku/{skuId}` | 匿名 | 查询 SKU 库存 |
| POST | `/api/v1/inventory/check` | 匿名 | 库存可用性校验 |
| POST | `/api/v1/admin/inventory/adjustments` | ADMIN | 盘点调整 |
| GET | `/api/v1/admin/inventory/warnings` | ADMIN | 低库存预警 |

库存校验 Request:

```json
{
  "skuId": 1,
  "quantity": 5
}
```

Response:

```json
{
  "skuId": 1,
  "available": true,
  "availableStock": 5
}
```

## 5. 购物车接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/cart/items` | USER | 添加购物车 |
| GET | `/api/v1/cart` | USER | 查询购物车 |
| PUT | `/api/v1/cart/items/{skuId}` | USER | 修改数量 |
| DELETE | `/api/v1/cart/items/{skuId}` | USER | 删除商品 |
| DELETE | `/api/v1/cart` | USER | 清空购物车 |
| POST | `/api/v1/cart/estimate` | USER | 价格预估 |

## 6. 订单接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/orders/create` | USER | 创建订单，成功返回 201 |
| GET | `/api/v1/orders/{orderId}` | USER | 订单详情 |
| GET | `/api/v1/orders` | USER | 我的订单列表 |
| POST | `/api/v1/orders/{orderId}/cancel` | USER | 取消订单 |
| POST | `/api/v1/admin/orders/{orderId}/cancel-review` | ADMIN | 取消审核 |
| POST | `/api/v1/orders/batch` | USER | 批量创建订单 |
| GET | `/api/v1/orders/verify-purchase` | USER/ADMIN | 验证购买记录 |
| GET | `/api/v1/admin/orders/statistics/sales` | ADMIN | 销售统计 |

创建订单 Request:

```json
{
  "addressId": 1,
  "items": [
    {"skuId": 1, "quantity": 2}
  ],
  "couponIds": [10],
  "redeemPoints": 1000,
  "externalOrderNo": "ORDER-CLIENT-001"
}
```

Response 201:

```json
{
  "orderId": 1,
  "orderNo": "SO202606070001",
  "status": "CREATED",
  "itemTotal": 398.00,
  "shippingFee": 0.00,
  "packagingFee": 2.00,
  "discountAmount": 30.00,
  "pointsDeductionAmount": 10.00,
  "payableAmount": 360.00
}
```

## 7. 支付、退款、发票接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/payment/pay` | USER | 发起支付 |
| POST | `/api/v1/payment/callback` | 签名 | 支付回调 |
| GET | `/api/v1/payment/{paymentNo}` | USER | 查询支付单 |
| POST | `/api/v1/refunds/apply` | USER | 申请退款 |
| POST | `/api/v1/admin/refunds/{refundId}/review` | ADMIN | 商家审核 |
| POST | `/api/v1/admin/refunds/{refundId}/warehouse-accept` | ADMIN | 仓库验收 |
| GET | `/api/v1/refunds/{refundId}` | USER | 查询退款 |
| POST | `/api/v1/invoices` | USER | 申请发票 |
| GET | `/api/v1/invoices/order/{orderId}` | USER | 查询订单发票 |
| POST | `/api/v1/admin/settlements/batches` | ADMIN | 生成结算批次 |

支付 Request:

```json
{
  "orderId": 1,
  "amount": 360.00,
  "method": "BALANCE",
  "clientPaymentNo": "PAY-CLIENT-001"
}
```

## 8. 促销接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/admin/promotions/coupons` | ADMIN | 创建优惠券 |
| POST | `/api/v1/promotions/coupons/claim` | USER | 领取优惠券 |
| GET | `/api/v1/promotions/coupons/my` | USER | 我的优惠券 |
| POST | `/api/v1/promotions/calculate` | USER | 计算优惠 |
| POST | `/api/v1/admin/promotions/full-reductions` | ADMIN | 创建满减 |
| POST | `/api/v1/admin/promotions/seckill` | ADMIN | 创建秒杀 |

## 9. 物流接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| GET | `/api/v1/logistics/order/{orderId}` | USER | 查询订单物流 |
| POST | `/api/v1/admin/logistics/shipments/{shipmentId}/pick` | ADMIN | 生成拣货单 |
| POST | `/api/v1/admin/logistics/shipments/{shipmentId}/print-label` | ADMIN | 打印面单 |
| POST | `/api/v1/admin/logistics/shipments/{shipmentId}/outbound` | ADMIN | 扫码出库 |
| POST | `/api/v1/logistics/callback` | 签名 | 物流回调 |
| POST | `/api/v1/admin/logistics/freight-templates` | ADMIN | 创建运费模板 |

## 10. 积分接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| GET | `/api/v1/loyalty/points` | USER | 查询积分 |
| POST | `/api/v1/loyalty/points/estimate-redeem` | USER | 估算抵扣 |
| GET | `/api/v1/loyalty/points/history` | USER | 积分流水 |
| GET | `/api/v1/loyalty/member-level` | USER | 会员等级 |
| POST | `/api/v1/admin/loyalty/points/expire` | ADMIN | 处理过期积分 |

## 11. 评价接口

| Method | URL | 认证 | 说明 |
|--------|-----|------|------|
| POST | `/api/v1/reviews` | USER | 发布评价 |
| POST | `/api/v1/reviews/{reviewId}/append` | USER | 追评 |
| GET | `/api/v1/reviews/product/{productId}` | 匿名 | 商品评价列表 |
| GET | `/api/v1/reviews/my` | USER | 我的评价 |
| POST | `/api/v1/admin/reviews/{reviewId}/approve` | ADMIN | 审核通过 |
| POST | `/api/v1/admin/reviews/{reviewId}/reject` | ADMIN | 审核拒绝 |

## 12. 黑盒测试支撑管理接口

以下接口均为公开 REST 管理接口，用于黑盒用例创建前置条件和查询可观察结果。用例级数据库、缓存和管理员账号隔离由测试 harness 完成，不通过业务 reset/bootstrap API 完成：

| Method | URL | 说明 |
|--------|-----|------|
| PUT | `/api/v1/admin/system/configs/{key}` | 覆盖运行期配置 |
| GET | `/api/v1/admin/system/configs/{key}` | 查询运行期配置 |
| PUT | `/api/v1/admin/system/clock` | 设置测试时钟 |
| DELETE | `/api/v1/admin/system/clock` | 重置测试时钟 |
| POST | `/api/v1/admin/ops/fault-injections` | 启用故障注入 |
| DELETE | `/api/v1/admin/ops/fault-injections` | 清空故障注入 |
| GET | `/api/v1/admin/events/failures` | 查询事件失败记录 |
| GET | `/api/v1/admin/notifications` | 查询通知记录 |
| POST | `/api/v1/admin/orders/timeout-cancel` | 触发订单超时取消扫描 |
