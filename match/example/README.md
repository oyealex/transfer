# ShopHub 示例题解

## 1. 项目说明

这是一个参考题解项目，代码已经按照 `design-docs/` 中的设计要求完成修复。该目录只包含业务代码、设计文档和运行说明，不包含黑盒测试用例。

## 2. 项目结构

```
├── code/              # 业务代码（12 个 Maven 模块的 Spring Boot 电商系统）
├── design-docs/       # 业务设计文档
├── maven-settings.xml # Maven 内网镜像配置
└── README.md          # 本文档
```

## 3. 环境依赖

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 编译和运行需要 Java 17 或更高版本 |
| Maven | 3.6+ | 构建工具，需配置好 `mvn` 命令 |
| Spring Boot | 3.2.6 | 框架版本（已在 `pom.xml` 中声明，无需单独安装） |
| H2 Database | 内嵌 | 开发/测试使用文件或内存模式，无需外部数据库 |

> 项目不含 Maven Wrapper，请确保本地已安装 Maven 并可通过 `mvn` 命令调用。
>
> 项目根目录提供了 `maven-settings.xml`，其中配置了内网 Maven 镜像。下面的验证命令均显式使用该配置，避免依赖本机用户目录或 Maven 安装目录中的 `settings.xml`。
>
> 本项目为纯后端服务，不含前端界面。

## 4. 验证命令

```bash
# 运行模块单元测试
mvn -s maven-settings.xml -f code/pom.xml test

# 安装业务代码到本地 Maven 仓库
mvn -s maven-settings.xml -f code/pom.xml install -DskipTests

# 本地启动应用
mvn -s maven-settings.xml -f code/pom.xml -pl ecommerce-app spring-boot:run
```

## 5. API 基线（冻结契约）

所有 API 前缀固定为 `/api/v1/`。以下内容均不得修改：URL 路径、HTTP Method、Request/Response 字段名和类型、成功 HTTP 状态码、错误响应结构。

### 5.1 用户模块

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

### 5.2 商品模块

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

### 5.3 库存模块

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/admin/warehouses` | ADMIN | 201 |
| POST | `/api/v1/admin/inventory/inbound` | ADMIN | 201 |
| POST | `/api/v1/admin/inventory/outbound` | ADMIN | 201 |
| GET | `/api/v1/inventory/sku/{skuId}` | 匿名 | 200 |
| POST | `/api/v1/inventory/check` | 匿名 | 200 |
| POST | `/api/v1/admin/inventory/adjustments` | ADMIN | 201 |
| GET | `/api/v1/admin/inventory/warnings` | ADMIN | 200 |

### 5.4 购物车模块

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/cart/items` | USER | 201 |
| GET | `/api/v1/cart` | USER | 200 |
| PUT | `/api/v1/cart/items/{skuId}` | USER | 200 |
| DELETE | `/api/v1/cart/items/{skuId}` | USER | 204 |
| DELETE | `/api/v1/cart` | USER | 204 |
| POST | `/api/v1/cart/estimate` | USER | 200 |

### 5.5 订单模块

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

### 5.6 支付、退款、发票

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

### 5.7 促销、物流、积分、评价

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

### 5.8 黑盒测试支撑管理接口

全部需要 ADMIN 认证，用于测试配置覆盖、故障注入、时钟控制、事件失败查询等。

| Method | URL | 成功状态 | 说明 |
|--------|-----|----------|------|
| PUT | `/api/v1/admin/system/configs/{key}` | 200 | 运行时配置覆盖 |
| GET | `/api/v1/admin/system/configs/{key}` | 200 | 查询配置 |
| POST | `/api/v1/admin/ops/fault-injections` | 200 | 启用故障注入 |
| DELETE | `/api/v1/admin/ops/fault-injections` | 204 | 清除所有故障注入 |
| GET | `/api/v1/admin/events/failures` | 200 | 查询事件失败记录 |
| GET | `/api/v1/admin/notifications` | 200 | 查询通知记录 |
| PUT | `/api/v1/admin/system/clock` | 200 | 设置测试时钟 |
| DELETE | `/api/v1/admin/system/clock` | 200 | 重置测试时钟 |
| POST | `/api/v1/admin/orders/timeout-cancel` | 200 | 触发超时取消 |

## 6. 错误码

### 6.1 通用错误码

| 错误码 | HTTP | 说明 |
|--------|------|------|
| VALIDATION_FAILED | 400 | 请求参数校验失败 |
| RESOURCE_NOT_FOUND | 404 | 资源不存在 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| CONFLICT | 409 | 状态冲突或重复请求 |
| RATE_LIMITED | 429 | 触发限流 |
| INTERNAL_ERROR | 500 | 系统内部错误 |

### 6.2 业务错误码

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

## 7. 关键原则

> **设计文档是验收基准，代码必须按设计修正。** 不要因代码当前行为去修改设计文档。
