# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目背景

这是一个 **AI Agent 比赛项目**。比赛主题为"设计实现一致性检查与修复"——参赛者需要构造 Skill 或 Agent，自动识别 ShopHub 电商系统中设计文档与代码实现之间的不一致点，并自动修改代码使其匹配设计。

### 仓库结构说明

| 目录 | 用途 | 可见性 |
|------|------|--------|
| `public/code/` | 基础工程代码（含单元测试），故意包含若干与设计不一致的问题 | 选手可见 |
| `public/design-docs/` | 业务设计文档，最终验收以此为准 | 选手可见 |
| `public/test-cases/` | 公开黑盒 JUnit 测试项目 | 选手可见 |
| `public/README.md` | 项目背景、任务规则、API 基线和公开用例说明 | 选手可见 |
| `example/` | 参考题解项目，不包含黑盒用例 | 维护者可见 |
| `02-04-scoring/references/test-cases/` | 判题 skill 自带公开黑盒用例副本 | 评分方内部使用 |
| `02-04-scoring/references/test-cases-internal/` | 判题 skill 自带非公开黑盒用例，用于最终评分 | 评分方内部使用 |

### 评分机制

- 公开黑盒用例（约 25 个）：用于给选手提供线索，初始代码上预期 16-17 个通过、8-9 个失败
- 隐藏黑盒用例（约 57 个）：最终评分的核心，选手不可见
- 静态检查：验证设计文档未被篡改、API 契约未被修改
- 修复报告：满分 10 分，按完整性、准确性、清晰度和修复日志四个维度评分
- JUnit 测试不参与最终评分，选手可自由修改或删除

**核心原则：** 设计文档是验收基准，代码必须按设计修正，绝不可反向修改设计文档。

## 代码探索：优先使用 CodeGraph

本项目已通过 CodeGraph 对 `public/code/` 和 `public/test-cases/` 建立了代码知识图谱索引。探索代码时，**优先使用 CodeGraph MCP 工具**，而非直接 grep 或逐个读取文件。

### 工具选择指南

| 场景 | 使用工具 |
|------|----------|
| 理解某个功能如何工作、追溯调用链路、架构分析 | `codegraph_explore`（首选，一次调用即可返回相关符号的完整源码） |
| 查找某个符号的名称和位置 | `codegraph_search` |
| 查看谁调用了某个方法/类 | `codegraph_callers` |
| 查看某个方法/类调用了什么 | `codegraph_callees` |
| 评估修改某个符号的影响范围 | `codegraph_impact` |
| 确认某个具体细节 CodeGraph 未覆盖 | 最后才用 `Read`/`Grep` |

CodeGraph 能够追踪动态分派（回调、React 重渲染、JSX 子组件）等 grep 无法跟踪的调用路径。对于"X 如何到达 Y"这类问题，直接向 `codegraph_explore` 提供涉及的符号名称即可。

## 关键约束规则

### 禁止修改
- REST API 的 URL、HTTP Method、Request/Response 字段名和类型
- `public/design-docs/` 目录下所有设计文档（含附录）
- `public/README.md` 中的 API 基线和任务规则
- API 版本前缀（`/api/v1/`）

### 允许修改
- Java 源代码（任意 `.java` 文件）
- `application.yml` 和 `application-test.yml`
- `pom.xml` 文件
- JUnit 测试（可修改或删除，不参与最终评分）
- 可新增服务、配置、事件、DTO 等类，但不得破坏 API 契约

### 严禁事项
- 为隐藏测试硬编码逻辑
- 修改 `design-docs/` 以迁就当前代码行为
- 修改 API 签名（URL、Method、Request/Response 字段）
- 暴露数据库 reset/bootstrap 接口

## 构建、测试与验证命令

所有命令从仓库根目录（`/home/oyealex/project/match3`）执行：

```bash
# 构建所有业务模块并运行模块单元测试
mvn -s public/maven-settings.xml -f public/code/pom.xml test

# 运行单个模块的测试
mvn -s public/maven-settings.xml -f public/code/pom.xml -pl ecommerce-product test

# 将业务代码安装到本地 Maven 仓库（运行黑盒测试前必须执行）
mvn -s public/maven-settings.xml -f public/code/pom.xml install -DskipTests

# 运行公开黑盒 REST 测试
mvn -s public/maven-settings.xml -f public/test-cases/pom.xml test

# 运行单个公开黑盒测试类
mvn -s public/maven-settings.xml -f public/test-cases/pom.xml -Dtest=PublicL1Test test

# 运行隐藏黑盒测试（仅在评分方内部使用，选手不可见）
mvn -s 02-04-scoring/references/maven-settings.xml -f 02-04-scoring/references/test-cases-internal/pom.xml test

# 本地启动应用
mvn -s public/maven-settings.xml -f public/code/pom.xml -pl ecommerce-app spring-boot:run
```

运行黑盒测试前务必先执行 `mvn -s public/maven-settings.xml -f public/code/pom.xml install -DskipTests`，因为 `public/test-cases/` 和判题 skill 中的 `references/test-cases-internal/` 都是独立 Maven 项目，不在 `code/pom.xml` 的 `<modules>` 中，需要通过本地 Maven 仓库解析 `ecommerce-app` 依赖。不需要也不应为运行黑盒用例而修改业务工程 POM。

## 架构总览

ShopHub 是一个**模块化单体** — 全部 12 个模块运行在同一个 Spring Boot 进程中，但强制执行严格的领域边界。模块之间不得直接访问彼此的数据库表、Repository 或 JPA Entity。跨模块访问通过以下方式：

1. **同步本地接口**（如 `ProductQueryService`、`InventoryQueryService`）— 用于查询类和强一致链路
2. **Spring ApplicationEvent** — 用于支付成功后的物流、积分、通知等弱耦合后置动作
3. **REST API** — 用于黑盒测试和外部客户端访问

### 模块清单

| 模块 | 包名 | 职责 |
|------|------|------|
| `ecommerce-common` | `com.ecommerce.common` | 共享 DTO、异常、MonetaryUtil、事件基类、统一通知、限流 |
| `ecommerce-user` | `com.ecommerce.user` | 注册、登录、JWT、用户资料、地址、账户冻结 |
| `ecommerce-product` | `com.ecommerce.product` | SPU、SKU、类目、品牌、搜索、上下架 |
| `ecommerce-inventory` | `com.ecommerce.inventory` | 仓库、入库、出库、库存预占、扣减、盘点、预警 |
| `ecommerce-cart` | `com.ecommerce.cart` | 购物车增删改查、TTL、价格预估，数据存储在 Caffeine 缓存中（TTL 7 天） |
| `ecommerce-order` | `com.ecommerce.order` | 订单创建、状态机、批量导入、取消、销售统计 |
| `ecommerce-payment` | `com.ecommerce.payment` | 支付、退款、回调、对账、发票、结算 |
| `ecommerce-promotion` | `com.ecommerce.promotion` | 优惠券、满减、秒杀、会员折扣、优惠叠加规则 |
| `ecommerce-logistics` | `com.ecommerce.logistics` | 发货、拣货、面单打印、物流轨迹、运费模板 |
| `ecommerce-loyalty` | `com.ecommerce.loyalty` | 积分赚取、抵扣、冻结、过期、会员等级、权益 |
| `ecommerce-review` | `com.ecommerce.review` | 评价、追评、图片评价、审核、敏感词过滤 |
| `ecommerce-app` | `com.ecommerce.app` | 应用启动、Spring Security + JWT 配置、H2、Caffeine、测试 profile 支撑 |

### 模块依赖关系（简化）
```
app-bootstrap → user、product、common
user + product + inventory → cart → order（含 promotion）
order → payment、logistics、loyalty → review
```

### 关键跨模块接口
- `ProductQueryService` → 供 inventory、cart、order 查询商品/SKU/上下架状态
- `InventoryQueryService` + `InventoryReservationService` → 供 product、cart、order 查询库存和预占
- `UserQueryService` → 供 order、review 校验用户状态和冻结状态
- `OrderQueryService` → 供 payment、review、logistics、loyalty 查询订单和验证购买记录
- `PromotionCalculationService` → 供 order、cart 计算优惠
- `LocalNotificationService` → 供所有模块发送统一通知（严禁直接调用 MockMailSender/MockSmsSender）

### 关键业务规则
1. 创建订单只预占库存，不扣减。支付成功后扣减库存。
2. 支付金额必须等于订单应付金额 — 不支持部分支付。
3. 支付成功后的物流创建、积分发放、通知发送等后置动作通过事件异步处理 — 失败不得回滚支付主流程。
4. 已支付订单取消必须进入商家审核流程（CANCEL_REVIEWING），不得直接取消。
5. 退款必须经过商家审核 → 仓库验收 → 退款，不可跳过仓库验收环节。
6. 发票支持部分开票，累计开票金额不得超过订单实付金额。
7. 积分抵扣受双重约束：单笔最多 10,000 积分，且不得超过订单实付金额的 50%。
8. 评价必须校验用户已购买且订单已签收。
9. 优惠叠加顺序：满减 → 优惠券 → 会员折扣。
10. 物流发货流程：PICKING → LABEL_PRINTED → OUTBOUND（不可跳过步骤）。

## 设计文档索引

所有文档位于 `public/design-docs/`：

| 文件 | 内容 |
|------|------|
| `01-项目概述.md` | 系统定位、技术栈、模块清单、关键业务原则、数据格式、响应格式、运行模式 |
| `02-系统架构.md` | 架构风格、模块依赖图、跨模块接口清单、事件驱动设计、事务边界、缓存设计、安全架构 |
| `03-通用规范与非功能设计.md` | 金额计算规则、通用异常定义、幂等规范、限流规则、黑盒测试隔离、审计日志、通知规范、事件失败处理 |
| `04-用户服务设计.md` 至 `15-本地通知组件设计.md` | 各模块详细设计 |
| `附录A-API接口参考.md` | 冻结的 REST API 契约（URL、Method、Request/Response 字段、错误码） |
| `附录B-配置参考.md` | 运行期配置项 |
| `附录C-数据模型.md` | 数据库表结构 — 表名、字段、类型 |
| `附录D-本地事件契约.md` | 事件契约 — 事件类型、发布方、监听方、载荷字段 |

## API 基线

`public/README.md` 和 `public/design-docs/附录A-API接口参考.md` — 冻结的 REST API 契约。包含：
- 完整 API 索引（所有端点、Method、认证要求、成功状态码）
- 服务前缀：`/api/v1`
- 标准错误码（通用 + 业务）
- 黑盒测试支撑管理接口（配置覆盖、故障注入、测试时钟、事件失败记录查询、通知记录查询）

各操作对应的 HTTP 状态码约定：
- POST 创建：201
- GET 查询：200
- PUT 更新：200
- DELETE：204

## 工作步骤

查找和修复不一致问题的推荐流程：

1. **先通读设计文档** — 重点阅读 `01-项目概述.md`、`02-系统架构.md`、`03-通用规范与非功能设计.md` 和 `附录A-API接口参考.md`
2. **阅读 API 基线** — `public/README.md` 和 `public/design-docs/附录A-API接口参考.md`，确认冻结的 API 契约
3. **使用 CodeGraph 探索代码** — 用 `codegraph_explore` 按模块追踪关键实现，对比设计文档中的规范
4. **运行公开黑盒测试** — 观察失败用例以定位不一致位置
5. **逐模块对比设计与代码** — 设计文档是验收基准，代码必须匹配设计
6. **修复代码**使其匹配设计，绝不可反向修改设计文档
7. **验证** — 运行 `mvn -s public/maven-settings.xml -f public/code/pom.xml test` 确保可编译，再运行 `mvn -s public/maven-settings.xml -f public/test-cases/pom.xml test`
8. **输出修复报告**，包含不一致点清单、设计依据、实际代码行为、修复方式和未修复风险

## 已知预埋不一致点

初始代码故意包含 43 个不一致点，分布在 5 个层级：

- **L1 行级/语句级（10 个）**：错误的常量、运算符、异常类型、HTTP 状态码
- **L2 方法级（13 个）**：缺失校验、错误的计算逻辑、遗漏的步骤
- **L3 业务流/状态机级（10 个）**：工作流缺失步骤、错误的处理顺序
- **L4 模块边界级（8 个）**：跨模块直接访问数据库而非通过接口
- **L5 架构级（2 个）**：应使用异步事件却使用同步耦合、缺失事件失败记录

### 预埋问题速查表

| 编号 | 模块 | 问题描述 |
|------|------|----------|
| L1-01 | promotion | 8 折券计算为 `price * (1 - 0.8)`，应为 `price * 0.8` |
| L1-02 | inventory | 库存检查使用 `available > request`，应为 `available >= request` |
| L1-03 | order | 金额校验抛出 `IllegalArgumentException`，应抛出 `OrderValidationException` |
| L1-04 | payment | 税率硬编码 0.13，应从配置读取默认 0.06 |
| L1-05 | user | 地址格式化 province/city 顺序调换 |
| L1-06 | order | 下单前校验只检查 user != null，未校验冻结状态 |
| L1-07 | common | 金额舍入使用 HALF_DOWN，应为 HALF_UP |
| L1-08 | order | 创建订单返回 200，应返回 201 |
| L1-09 | logistics | 免邮门槛使用 `>` 199，应为 `>=` 199 |
| L1-10 | loyalty | GOLD 会员倍率写 1.1，应为 1.2 |
| L2-01 | order | 订单总价漏加 shippingFee |
| L2-02 | payment | 支付校验只要求 paidAmount > 0，未要求等于订单应付金额 |
| L2-03 | inventory/order | 创建订单时直接扣减库存，应只预占 |
| L2-04 | promotion | 优惠券校验缺少有效期检查 |
| L2-05 | user | 注册后直接 ACTIVE，应为 PENDING_ACTIVATION |
| L2-06 | payment | 退款金额计算多减 1.00 元 |
| L2-07 | logistics | 物流回调只打日志，未更新订单物流状态 |
| L2-08 | loyalty | 积分计算漏乘 activityMultiplier |
| L2-09 | payment | 发票开具忽略 invoiceAmount，不支持部分开票 |
| L2-10 | product | 商品搜索默认包含 OFF_SHELF 商品 |
| L2-11 | cart | 重复 SKU 加入购物车覆盖数量，应累加 |
| L2-12 | review | 敏感词只校验完全相等，应支持包含匹配 |
| L2-13 | payment | 支付回调不校验 X-Payment-Signature |
| L3-01 | order | 下单流程缺少风控校验 |
| L3-02 | payment | 退款审核后直接退款，缺少仓库验收环节 |
| L3-03 | promotion | 优惠叠加顺序错误（会员折扣→满减→优惠券） |
| L3-04 | logistics | 支付后直接出库，缺少拣货和面单步骤 |
| L3-05 | order | 批量订单使用整体事务，一条失败回滚整批 |
| L3-06 | order/payment | 已支付订单直接取消全额退款，应进入取消审核 |
| L3-07 | loyalty | 没有积分过期处理机制 |
| L3-08 | order | 超时取消只改订单状态，未释放库存预占 |
| L3-09 | review/loyalty | 评价提交即发积分，应审核通过后才发放 |
| L3-10 | invoice/payment | 结算批次包含未支付订单 |
| L4-01 | payment→order | 直接用 JdbcTemplate 查 orders 表 |
| L4-02 | product→inventory | 直接注入 InventoryRepository |
| L4-03 | review→order | 保存评价前不校验购买记录 |
| L4-04 | cart | 购物车落 H2 表而非 Caffeine 缓存 |
| L4-05 | order→logistics | 支付确认同步创建发货单 |
| L4-06 | inventory→product | 库存模块定义了 Product Entity |
| L4-07 | all→common | 直接调用 MockMailSender/MockSmsSender |
| L4-08 | loyalty→order | 直接读 orders 表统计会员等级 |
| L5-01 | payment | 支付确认同步执行所有后置动作，任一失败导致支付失败 |
| L5-02 | common | 事件监听失败只记日志，无持久化失败记录和重放能力 |

## 技术栈

- Java 17、Spring Boot 3.2.6、Spring Data JPA/Hibernate
- H2 数据库（开发模式用文件存储，测试用内存模式）
- Spring Cache + Caffeine（本地缓存）
- Spring Security + JWT（jjwt 0.12.5）
- Maven 多模块、JUnit 5、Mockito、AssertJ
- 包命名约定：`com.ecommerce.<domain>`
- CodeGraph 代码知识图谱索引（位于 `public/code/.codegraph/` 和 `public/test-cases/.codegraph/`）
