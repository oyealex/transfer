# Repository Guidelines

## 项目定位

本仓库是 AI Agent 比赛项目。比赛目标是让选手构造 Skill 或 Agent，自动阅读给定代码工程和设计文档，识别设计实现不一致的问题，并修改代码使实现符合设计。

`public/` 是最终提供给选手的内容，包括代码、设计文档、公开黑盒测试用例和比赛说明。选手看到的项目根目录等同于本仓库的 `public/` 目录。`02-04-scoring/` 是判题 skill，存放独立的公开黑盒用例和非公开黑盒用例，仅用于内部验收，不应在面向选手的说明或修复建议中泄露隐藏用例细节。

## 项目结构与模块组织

`public/code/` 是 Spring Boot 电商系统的 Maven 多模块项目，父 POM 为 `public/code/pom.xml`。业务模块按领域命名，例如 `ecommerce-user`、`ecommerce-product`、`ecommerce-order`、`ecommerce-payment`；`ecommerce-common` 放共享 DTO、异常、事件和测试工具；`ecommerce-app` 负责组装可运行应用。

`public/design-docs/` 是设计验收基准，`public/README.md` 汇总比赛说明、冻结 REST API 契约和公开用例说明，`public/test-cases/` 是公开黑盒测试工程。`example/` 是参考题解，不包含黑盒用例。`02-04-scoring/references/test-cases-internal/` 是判题 skill 的隐藏测试工程，仅供内部验证。

## 构建、测试与本地运行

仓库维护者从仓库根目录执行：

- `mvn -s public/maven-settings.xml -f public/code/pom.xml test`：编译全部业务模块并运行模块测试。
- `mvn -s public/maven-settings.xml -f public/code/pom.xml -pl ecommerce-product test`：只运行指定模块测试。
- `mvn -s public/maven-settings.xml -f public/code/pom.xml install`：安装业务模块，供黑盒测试解析依赖。
- `mvn -s public/maven-settings.xml -f public/test-cases/pom.xml test`：运行公开 REST 黑盒用例。
- `mvn -s 02-04-scoring/references/maven-settings.xml -f 02-04-scoring/references/test-cases-internal/pom.xml test`：运行判题 skill 中的非公开隐藏用例。

面向选手的文档和命令必须以 `public/` 为根目录书写，例如 `mvn -s maven-settings.xml -f code/pom.xml test`，不要出现 `public/` 前缀。

## 代码探索

本项目已使用 codegraph 建立代码索引。探索代码结构、调用链、影响范围或定位实现时，优先使用 codegraph 工具，例如 `codegraph_explore`、`codegraph_node`、`codegraph_callers` 和 `codegraph_impact`。只有在需要查看非代码文件、构建文件、文档或 codegraph 结果不足时，再使用 `rg`、`sed` 等文件级工具。

## 修改原则

比赛修复应以设计文档和 API 基线为准。允许修改 `public/code/` 中的 Java 源码、配置、POM 和测试，但不得修改 `public/design-docs/` 中的设计要求，不得改变 `public/README.md` 和 `public/design-docs/附录A-API接口参考.md` 中冻结的 URL、HTTP Method、请求字段、响应字段和错误结构。

修复应避免针对公开或隐藏用例硬编码。优先修正领域规则、状态流转、异常码、事务边界和跨模块事件协作，使实现自然符合设计。

## 编码与测试

使用 Java 17 和 Spring Boot 3.2。包名保持在 `com.ecommerce.<domain>` 下，缩进使用 4 个空格。Spring Bean 优先使用构造器注入。类名使用清晰后缀，例如 `Controller`、`Service`、`Repository`、`Dto`、`Request`、`Response`。

测试框架为 JUnit 5、Spring Boot Test、Mockito 和 AssertJ。测试类命名为 `*Test`，放在与被测代码对应的包结构下。修改业务规则时运行相关模块测试；修改 REST 行为时同时运行公开黑盒测试。内部验收可追加运行判题 skill 中的 `references/test-cases-internal`。

## 文档边界

所有 `public/` 下文档都应从选手视角书写，不体现内部处理过程，不引用判题 skill 或隐藏用例，不描述隐藏用例细节。路径必须按选手看到的根目录表达，例如 `code/`、`design-docs/`、`test-cases/`。
