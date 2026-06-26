# ShopHub 设计实现一致性检查与修复

## 1. 比赛主题与任务

**主题：** 设计实现一致性检查与修复。使用 AI Agent 检查 ShopHub 电商系统的设计文档与代码实现之间的不一致点，并修复代码使其匹配设计。

**核心任务：**

1. 阅读 `design-docs/` 中所有设计文档（验收基准）
2. 阅读 `code/` 中的 Java Spring Boot 多模块项目
3. 阅读本文档中冻结的 REST API 契约
4. 对比设计文档与代码实现，找出不一致点
5. 修复代码使其匹配设计（绝不可反向修改设计文档）
6. 保持项目可编译

## 2. 项目结构

```
public/
├── code/              # 业务代码（12 个 Maven 模块的 Spring Boot 电商系统）
├── design-docs/       # 业务设计文档（最终验收以此为准）
├── test-cases/        # 公开黑盒 JUnit 测试项目
└── README.md          # 本文档（比赛说明 + API 基线 + 黑盒用例说明）
```

## 3. 修改边界

### 允许修改
- Java 源代码（任意 `.java` 文件）
- `application.yml` / `application-test.yml`
- `pom.xml`
- JUnit 测试（可修改或删除，不参与最终评分）
- 可新增服务、配置、事件、DTO 等类（不得破坏 API 契约）

### 禁止修改
- REST API URL、HTTP Method、Request Header、Request/Response Body 字段名和类型
- `design-docs/` 下所有设计文档
- API 版本前缀（`/api/v1/`）
- 为特定测试用例硬编码逻辑
- 暴露数据库 reset/bootstrap 接口

## 4. 环境依赖

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 编译和运行需要 Java 17 或更高版本 |
| Maven | 3.6+ | 构建工具，需配置好 `mvn` 命令 |
| Spring Boot | 3.2.6 | 框架版本（已在 `pom.xml` 中声明，无需单独安装） |
| H2 Database | 内嵌 | 开发/测试使用文件或内存模式，无需外部数据库 |

> 项目不含 Maven Wrapper，请确保本地已安装 Maven 并可通过 `mvn` 命令调用。
>
> 本项目为纯后端服务，不含前端界面。验证方式为运行黑盒测试用例，关注用例通过情况即可，无需启动应用进行手动操作。

## 5. 验证命令

```bash
# 运行模块单元测试
mvn -f code/pom.xml test

# 安装业务代码到本地 Maven 仓库（运行黑盒测试前必须执行）
mvn -f code/pom.xml install -DskipTests

# 运行全部公开黑盒测试
mvn -f test-cases/pom.xml test

# 运行单个测试类
mvn -f test-cases/pom.xml -Dtest=PublicL1Test test
```

> `test-cases/` 是独立 Maven 测试项目，不在 `code/pom.xml` 的 reactor 中。运行黑盒测试前必须先 `install` 业务代码。不需要修改任何 POM 来运行黑盒测试。

## 6. API 基线（冻结契约）

所有 API 前缀固定为 `/api/v1/`。以下内容均不得修改：URL 路径、HTTP Method、Request/Response 字段名和类型、成功 HTTP 状态码、错误响应结构。

### 6.1 用户模块

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

### 6.2 商品模块

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

### 6.3 库存模块

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/admin/warehouses` | ADMIN | 201 |
| POST | `/api/v1/admin/inventory/inbound` | ADMIN | 201 |
| POST | `/api/v1/admin/inventory/outbound` | ADMIN | 201 |
| GET | `/api/v1/inventory/sku/{skuId}` | 匿名 | 200 |
| POST | `/api/v1/inventory/check` | 匿名 | 200 |
| POST | `/api/v1/admin/inventory/adjustments` | ADMIN | 201 |
| GET | `/api/v1/admin/inventory/warnings` | ADMIN | 200 |

### 6.4 购物车模块

| Method | URL | 认证 | 成功状态 |
|--------|-----|------|----------|
| POST | `/api/v1/cart/items` | USER | 201 |
| GET | `/api/v1/cart` | USER | 200 |
| PUT | `/api/v1/cart/items/{skuId}` | USER | 200 |
| DELETE | `/api/v1/cart/items/{skuId}` | USER | 204 |
| DELETE | `/api/v1/cart` | USER | 204 |
| POST | `/api/v1/cart/estimate` | USER | 200 |

### 6.5 订单模块

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

### 6.6 支付、退款、发票

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

### 6.7 促销、物流、积分、评价

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

### 6.8 黑盒测试支撑管理接口

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

## 7. 错误码

### 7.1 通用错误码

| 错误码 | HTTP | 说明 |
|--------|------|------|
| VALIDATION_FAILED | 400 | 请求参数校验失败 |
| RESOURCE_NOT_FOUND | 404 | 资源不存在 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| CONFLICT | 409 | 状态冲突或重复请求 |
| RATE_LIMITED | 429 | 触发限流 |
| INTERNAL_ERROR | 500 | 系统内部错误 |

### 7.2 业务错误码

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

## 8. 黑盒测试用例

### 公开用例

公开黑盒用例共 25 个，分为 16 个基础链路用例和 8 个补充业务场景用例。每个用例独立运行，使用随机 H2 内存库（测试 profile），通过 `testRunId` 隔离数据。

公开用例用于帮助验证关键 REST 场景，但**不覆盖全部验收范围**——除此之外还有非公开的黑盒测试用例，参赛者不可见。通过公开用例不代表实现已完整符合设计。

**通用执行约束：**
- BeforeEach：启动新 Spring Boot 上下文（随机 H2 + 测试 profile）→ seed 管理员 → 生成 testRunId → 获取 adminToken
- AfterEach：关闭上下文，丢弃内存库、缓存和静态状态
- 所有唯一字段带 `testRunId`（如 `email=user-{testRunId}@example.com`、`skuCode=SKU-{testRunId}`）
- 只通过 REST API 创建前置条件和观察结果

### 8.1 基础链路用例（PUB-001 ~ PUB-016）

| ID | 用例 | 主要前置条件 | 核心断言 |
|----|------|--------------|----------|
| PUB-001 | 用户注册激活登录 | 干净上下文 | 注册→PENDING_ACTIVATION，激活→ACTIVE，登录返回 JWT |
| PUB-002 | 创建地址并查询 | 已登录用户 | 地址字段 province/city/district/detail 与请求一致 |
| PUB-003 | 商品创建上架查询 | 管理员 | SKU 状态 ON_SHELF，skuCode 正确 |
| PUB-004 | 商品搜索 | 已上架 SKU | keyword 可命中，返回项 status=ON_SHELF |
| PUB-005 | 入库并查询库存 | 仓库、SKU | onHandStock=50、availableStock=50 |
| PUB-006 | 购物车添加和修改 | 用户、可售 SKU、库存 | 数量累加和修改正确 |
| PUB-007 | 购物车价格预估 | 用户、购物车含商品 | 返回 itemTotal、payableAmount、shippingFee、discountAmount |
| PUB-008 | 基础订单创建 | 用户、地址、SKU、库存 | 状态 CREATED，HTTP 201 |
| PUB-009 | 支付单创建 | 待支付订单 | paymentNo 非空，status=CREATED |
| PUB-010 | 支付回调成功 | 支付单 | 支付状态 SUCCESS |
| PUB-011 | 物流查询 | 已支付订单 | 返回 orderId 和 shipment 状态 |
| PUB-012 | 积分查询 | 已登录用户 | 返回 availablePoints，支持分页查询历史 |
| PUB-013 | 发票全额开具 | 已支付订单 | 发票金额=订单实付金额 |
| PUB-014 | 评价发布基础链路 | 已支付、发货、签收订单 | 评价状态 PENDING_REVIEW |
| PUB-015 | 销售统计查询 | 管理员、已支付订单 | totalOrders >= 1，totalAmount >= 订单 paidAmount |
| PUB-016 | 批量订单全部合法 | 用户、两个可售 SKU、库存 | 每条结果 status=SUCCESS，成功数量=2 |

### 8.2 补充业务场景用例（PUB-101 ~ PUB-108）

| ID | 用例 | 核心断言 |
|----|------|----------|
| PUB-101 | 8 折优惠券计算 | discountAmount=20.00（price 100.00，应为 price×0.8 而非 price×(1-0.8)） |
| PUB-102 | 创建订单返回 201 | HTTP 201，body.status=CREATED |
| PUB-103 | 冻结用户不可下单 | HTTP 403，code=USER_FROZEN |
| PUB-104 | 非包邮订单含运费 | shippingFee=8.00，payableAmount=itemTotal+shippingFee+packagingFee-discountAmount-pointsDeductionAmount |
| PUB-105 | 未激活不可登录 | HTTP 403，code=USER_NOT_ACTIVE |
| PUB-106 | 高风险订单风控拒绝 | HTTP 400，code=ORDER_RISK_REJECTED |
| PUB-107 | 发货流程不可跳步 | 先 PICKING → LABEL_PRINTED → OUTBOUND，不可支付后直接出库 |
| PUB-108 | 后置动作失败不阻塞支付 | 故障注入后支付仍 SUCCESS，后置失败不导致支付回滚 |

## 9. 关键原则

> **设计文档是验收基准，代码必须按设计修正。** 不要因代码当前行为或公开测试现状去修改设计文档。最终评分基于黑盒用例通过情况——包括公开用例和非公开用例。
