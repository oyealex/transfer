# Matt Pocock Skills：为 AI 编程 Agent 构建可预测、可组合的工程工作流

> 一份面向具备 Agent 使用经验的开发者的深度解读——不只是罗列技能，更是拆解其设计哲学、架构思路与最佳实践，并同 Superpowers 框架做系统性对比。

---

## 目录

1. [起点：一个问题](#起点一个问题)
2. [技能全景图](#技能全景图)
3. [设计哲学：可预测性为根](#设计哲学可预测性为根)
4. [核心架构决策](#核心架构决策)
5. [主线工作流：idea → ship](#主线工作流idea--ship)
6. [关键技能深度拆解](#关键技能深度拆解)
7. [词汇层：两个模型级参考](#词汇层两个模型级参考)
8. [跨会话与上下文管理](#跨会话与上下文管理)
9. [元技能：writing-great-skills](#元技能writing-great-skills)
10. [与 Superpowers 的深度对比](#与-superpowers-的深度对比)
11. [最佳实践总结](#最佳实践总结)

---

## 起点：一个问题

Matt Pocock 的 skills 仓库 README 里有一句定调的话——他批评那些"夺走你的控制权、让过程中的 bug 难以解决"的框架。这句话不是修辞，而是整个技能体系的**架构原点**：

> 技能应该是你可以阅读、修改、拥有的**小块积木**，而不是你必须臣服于的**系统**。

这不是说 Superpowers 或 Agent Skills 不好——它们的作者 Jesse Vincent 和 Addy Osmani 都是世界级的工程师，各自解决了不同的问题。但 Matt 选择了另一条路：**最大化开发者的控制权，最小化框架的强制性**。如果你用过 Superpowers 的完整 7 阶段流水线后感到"太重了"，或者觉得 Agent Skills 的 24 个技能像一个你不敢改的精密钟表——Matt 的技能体系就是为这个场景设计的。

这条路线有一组明确的取舍：

| 维度 | Matt Pocock 的选择 |
|------|-------------------|
| 流程控制 | 你控制流程，技能辅助执行 |
| 技能粒度 | 小而独立，自由组合 |
| 上下文负载 | 严格预算：只有需要时技能才占 token |
| 需求质量 | 最锋利的"需求审讯"循环 |
| 代码库健康 | 深度模块思维，长期可维护性 |

下面我们从全景图开始，逐层深入。

---

## 技能全景图

Matt 系列共 21+ 个技能，按在系统中的角色分为五层：

```
┌─────────────────────────────────────────────────────────┐
│                    元技能层                              │
│  ask-matt (路由器) · setup-matt-pocock-skills (初始化)    │
│  writing-great-skills (技能设计理论)                       │
├─────────────────────────────────────────────────────────┤
│                    词汇层 (模型调用)                       │
│  domain-modeling (领域语言) · codebase-design (深度模块)    │
├─────────────────────────────────────────────────────────┤
│          主线工作流: idea → ship                          │
│  grill-with-docs → to-spec → to-tickets → implement      │
│                    ↕ (手递手)                              │
│              prototype (分支探索)                          │
├─────────────────────────────────────────────────────────┤
│          匝道：产生工作的入口                              │
│  triage (issue 分诊) · diagnosing-bugs (调试)              │
│  wayfinder (大型迷雾探索)                                  │
├─────────────────────────────────────────────────────────┤
│          代码库健康 · 独立技能                             │
│  improve-codebase-architecture · code-review              │
│  research · teach · handoff · resolving-merge-conflicts   │
│  tdd · grill-me · prototype                              │
└─────────────────────────────────────────────────────────┘
```

**核心洞察**：这不是一个"系统"——这是一套**积木**。主线工作流只是最常见的拼法，你可以随时替换或跳过任何一块。例如：不用 `tdd` 而用自己的测试流程，不用 `to-spec` 而直接手写 spec，或者只取 `diagnosing-bugs` 这一个技能来强化调试流程。没有一块积木依赖"整个体系"才能工作。

---

## 设计哲学：可预测性为根

Matt 的技能设计理论（由 `writing-great-skills` 定义）有一个核心公理：

> **技能的存在意义是从随机系统中榨取出确定性。**
> ——根美德是**可预测性**（Predictability）：每次运行走相同的*过程*，而非产生相同的*输出*。

这个公理衍生出整个技能设计体系中的每一个决策。你不需要记住所有术语，但理解下面几个关键概念能够帮助你理解这套技能*为什么*长成它们现在的样子：

### 两种调用模式与两种负载

这是 Matt 技能体系最特殊的设计决策，也是区别于 Superpowers 的最大架构差异：

| | **模型调用 (Model-Invoked)** | **用户调用 (User-Invoked)** |
|---|---|---|
| **触发方式** | Agent 自动识别 + 用户手动 | 仅用户手动输入名称 |
| **对 agent 可见** | 是（description 始终在上下文中） | 否 |
| **可被其他技能调用** | 是 | 否 |
| **支付成本** | **上下文负载**：每个回合 description 都占用 token | **认知负载**：你必须记住它的存在 |
| **适用场景** | Agent 需要自主判断何时使用 | 仅在你明确需要时才触发 |
| **示例** | `domain-modeling`, `codebase-design` | `implement`, `to-spec`, `to-tickets` |

这个二分不是理论上的洁癖——在实际运行中，**模型调用的技能每个回合都在消耗 token**（description 字段始终在上下文窗口里）。对于一个有 10 个模型调用技能的项目，每轮可能额外消耗 500-1000 token。Matt 的立场是：如果 agent 不需要自主判断何时用这个技能，就不要让它占用上下文空间。

**Superpowers 的做法不同**：它在会话启动时通过 hook 注入约 2000 token 的引导指令，让 agent 在每次行动前查阅相关技能。两种方案各有取舍——Matt 的方案更省 token 但需要你记住技能名，Superpowers 的方案零记忆负担但恒定消耗 token。

### 信息层级 (Information Hierarchy)

技能内容按"agent 需要它的紧迫程度"排列，从高到低：

1. **步骤 (Steps)**：有序的行动指令，在 `SKILL.md` 中直接展示。每个步骤以一个**完成标准**（Completion Criterion）结束——一个 agent 可以自我判断"做完/没做完"的条件。
2. **内联参考 (In-file Reference)**：在同一文件中按需查阅的定义、规则、事实。
3. **外置参考 (Disclosed Reference)**：通过**上下文指针**（Context Pointer）链接到另一个文件的内容，只有指针触发时才加载。

理解这个层级是理解 Matt 技能"为什么有的长有的短"的关键。`diagnosing-bugs` 的 SKILL.md 很长，因为它的步骤和参考都在一个文件里——每个调试阶段都需要看到完整流程。`code-review` 的 SKILL.md 也偏长，因为它的 12 个代码坏味基线（smell baseline）必须在每一次审查中全部覆盖。而 `implement` 只有 9 行——它的内容就是对其他技能的编排。

### 引导词 (Leading Word)

这是 Matt 技能设计理论中最精妙的概念：

> **引导词**是一个已经存在于模型预训练中的紧凑概念，agent 在运行技能时用它来"思考"。它用最少的 token 编码一个行为原则，通过唤起模型已有的先验知识来工作。

例子：
- **"tight"**（紧凑）：`diagnosing-bugs` 中反复出现。一个"tight feedback loop"不是三个形容词的排列（fast + deterministic + low-overhead），而是一个模型已经理解的单一品质。技能只需要说"make the loop tight"，agent 就会朝正确的方向努力。
- **"red"**（变红）：同样来自 `diagnosing-bugs`。说"the loop goes red on this bug"比说"the test command exits with a non-zero status code indicating the presence of the specific defect"更精确——因为"red"是一个二进制的、可观察的状态，agent 无法模糊地解释它。
- **"tracer bullet"**（曳光弹）：来自 `to-tickets` 和 `tdd`。一个垂直切片，穿越所有层级但不求完整。这个词从《程序员修炼之道》进入模型预训练，Matt 直接复用它而不需要每次解释"一个窄但完整的端到端路径"。

引导词服务于可预测性的**两面**：
- **执行面**：在技能正文中，agent 每次遇到这个词就会触发相同的行为模式。
- **调用面**：在 description 中，当你的提示、文档和代码中使用了同样的词时，agent 就会将这个共享语言与技能关联起来，更可靠地触发它。

---

## 核心架构决策

### 1. 审讯优先于执行（Grill First, Build Later）

这是 Matt 系列最鲜明的特征。`grilling` 是几乎所有工作流的入口：

- 一次只问一个问题（"一次问多个问题令人眩晕"）
- 广度优先遍历决策树，逐层解决依赖关系
- 凡是能从代码库中查到的事实，绝不去问用户
- 每个问题附上推荐答案，用户一键确认即可

对比 Superpowers 的 `brainstorming`（Socratic 式澄清，输出设计文档），Matt 的 grilling 更强调**遍历决策依赖**——先解决被依赖的决策，再处理依赖它的决策。这是一种来自编译器设计的思维（拓扑排序），被移植到了需求工程中。

### 2. TDD 不含重构

这是一个"真正的哲学分裂"（genuine philosophical split）：

| | Matt Pocock | Superpowers / 经典 TDD |
|---|---|---|
| **循环** | Red → Green | Red → Green → Refactor |
| **重构归属** | 代码审查阶段 (`code-review`) | TDD 循环内部 |
| **理由** | 重构需要全局视角（两个轴的审查），不应在单测的局部循环中进行 | 重构是 TDD 的第三步，保持代码持续整洁 |

这个分歧不是孰优孰劣的问题。Matt 的立场适用于**将审查作为架构级活动**的团队；经典 TDD 适用于**重构是持续微活动**的团队。

### 3. 双轴代码审查

`code-review` 将审查拆成两个独立轴，用**并行子 agent** 执行：

- **Standards 轴**：代码是否符合仓库的编码标准？附带 12 个 Fowler 代码坏味基线（Mysterious Name, Feature Envy, Shotgun Surgery 等），即使仓库没有任何文档也会执行。
- **Spec 轴**：代码是否忠实实现了原始 issue/PRD？从 commit message 中的 `#123` 引用自动追溯 spec 来源。

两个轴分开报告，互不混合。这解决了"代码写得漂亮但做错了事"和"功能正确但破坏项目规范"两类问题互相遮蔽的痛点。一条改动可以 Standards pass + Spec fail，反之亦然——而传统的一次性 code review 往往会让一个轴的好坏掩盖另一个轴的问题。

### 4. 曳光弹式 Ticket（Tracer Bullet Tickets）

`to-tickets` 将 spec 分解为 ticket 的方式是 Matt 技能体系中对工程质量影响最大的设计决策之一：

- 每个 ticket 是一个**垂直切片**——跨越 schema → API → UI → test 的窄但完整的路径
- 每个 ticket 声明其**阻塞边**（blocking edges）——哪些 ticket 必须先完成
- 一个完成的 ticket 是**可演示或可验证的**，而非"后端写好了但前端没接"
- 唯独**宽重构**（wide refactor）例外——采用 expand-contract 模式：先同时存在新旧形式 → 逐个迁移 → 最后删除旧形式

对比 Superpowers 的 `writing-plans`（2-5 分钟的原子任务，带有具体文件路径和代码片段），Matt 的 ticket 偏**行为描述**而非实现指令。这是因为 Matt 的 ticket 是为 AFK agent 准备的——agent 需要自己去探索代码库做实现决策，ticket 只需要告诉它"做什么"和"怎么判断做完"。

### 5. Agent Brief：可持久化的行为规约

`triage` 技能中定义的 Agent Brief 是一个被低估的设计精华。当 issue 进入 `ready-for-agent` 状态时，需要写一份 brief 作为 agent 的工作合约：

- **持久性优先于精确性**：不写文件路径和行号（它们会过期）。写类型名、函数签名、行为契约
- **行为描述而非过程描述**：写"系统应该做什么"，而非"怎么实现"
- **完整的验收标准**：每个标准独立可验证
- **明确的范围边界**：列出不应触碰的部分

这个设计理念直面了一个现实问题：issue 可能在 `ready-for-agent` 状态停留数天甚至数周，代码库会发生变化。一份引用 `src/triage/handler.ts:150` 的 brief 届时已毫无意义；一份描述 `SkillConfig` 类型应新增 `schedule: CronExpression` 字段的 brief 则仍然有效。

---

## 主线工作流：idea → ship

这是 Matt 技能体系中最常见的拼法——但再次强调，它是积木的组合，不是强制的管道。

```
有代码库                        无代码库
   │                               │
   ▼                               ▼
grill-with-docs                grill-me
(审讯 + 领域建模)             (纯审讯, 不落盘)
   │                               │
   ├─ 需要可运行的答案? ──→ handoff → prototype → handoff 回来
   │                               │
   ▼                               │
 分支: 多会话构建?                  │
   ├─ 是 → to-spec → to-tickets    │
   │         │                      │
   │         ▼                      │
   │     implement (per ticket)     │
   │     内部驱动: tdd → code-review │
   │                                │
   └─ 否 → implement (同一窗口) ←──┘
```

### 上下文卫生（Context Hygiene）

Matt 提出了一个在实践中极其重要的约束：

> 步骤 1-3（grilling → spec → tickets）应保持在**一个连续的上下文窗口**中——不要 compact 或 clear——直到 `/to-tickets` 完成。这样审讯、spec 和 ticket 都建立在同一套思维之上。

之后，每个 `/implement` 从一个**全新上下文**开始，仅携带 ticket 内容。这解决了 LLM 在长会话中"注意力衰减"的问题——也被称为 [smart zone](https://www.aihero.dev/ai-coding-dictionary/smart-zone)（约 120K token 内模型推理依然犀利）。

**Superpowers 的解法不同**：它为每个任务派一个干净上下文的子 agent（subagent-driven development），用架构手段隔离上下文污染。Matt 依赖的是流程纪律——compact 只在有意为之的阶段间停顿处使用。

---

## 关键技能深度拆解

### diagnosing-bugs：不建反馈回路不罢休

这是 Matt 系列中最具方法论强度的技能，体现了"过程优于直觉"的极致：

```
Phase 1: 构建反馈回路 ←── 这就是技能本身
Phase 2: 复现 + 最小化
Phase 3: 生成 3-5 个可证伪假设
Phase 4: 一次只改变一个变量的针对性插桩
Phase 5: 先写回归测试再修复
Phase 6: 清理 + 事后复盘
```

**Phase 1 是灵魂**。它说：花不成比例的精力构建一个 tight feedback loop——一个**已经在这条 bug 上变红过的命令**。它给出了 10 种构建方法（从单元测试到 HITL bash 脚本），并设置了硬门槛：

- ✅ 红能力（red-capable）：断言的必须是用户描述的*这个* bug，而非"不崩就行"
- ✅ 确定性（deterministic）：每次运行相同结论
- ✅ 快（fast）：秒级，非分钟级
- ✅ Agent 可运行（agent-runnable）：无需人类参与

在完成 Phase 1 之前，"跳到代码里读着读着就产生假设"是最常见也是最被禁止的失败模式——**这正是这个技能要防止的事情**。Superpowers 的 `systematic-debugging` 也有类似的 4 阶段流程（investigate → hypothesize → experiment → fix），但缺乏 Phase 1 对反馈回路的偏执式强调。

### wayfinder：在迷雾中规划

`wayfinder` 是 Matt 系列中最复杂的技能，用于**一个会话装不下的大型探索**——可能是绿地项目、大型功能构建、或者一次数据迁移。它的核心隐喻是**战争迷雾**（fog of war）：

- **地图 (Map)**：一个 issue，标签 `wayfinder:map`，包含目的地描述、已做出的决策索引、以及尚未清晰到能写成 ticket 的"迷雾"区域
- **决策 Ticket**：每个 ticket 是一个**需要被回答的问题**，而非一段需要被构建的代码。Ticket 类型有四种：Research（AFK 自主）、Prototype（HITL 交互）、Grilling（HITL 审讯）、Task（HITL 或 AFK 的体力活）
- **边界 (Frontier)**：开放的、未被阻塞的、未被认领的 ticket——可被任何会话立即拾取

关键规则：**每次会话最多解决一个 ticket**（研究类 ticket 除外）。这不是限制，而是保护——wayfinder 的 ticket 每个都需要深度思考，一会话多 ticket 会导致浅尝辄止。

wayfinder 工作完毕后的产物不是可执行的代码，而是一组**决策**——然后交接到主线的 `to-spec` → `to-tickets` → `implement` 流程。这与 Superpowers 的从 brainstorming 到 subagent 执行的完整流水线形成了鲜明对比：Matt 的 wayfinder **只规划和决策，不构建**。

### improve-codebase-architecture：深度模块扫描

这个技能做一件在其他框架中很难找到对标的事：**把代码库的架构债务变成可视化的候选改造项**。

流程：
1. **探索**：扫描 commit 历史找到热点（hot spots），用 `codebase-design` 词汇评估模块的**深度**（depth）。不是死板地跑 lint，而是有机地感受摩擦：哪里 bounce 了很多小模块才能理解一个概念？哪里接口几乎和实现一样复杂（浅模块）？
2. **呈现**：生成一个自包含的 HTML 报告（Tailwind + Mermaid CDN），每个候选改造项一张卡片，配 before/after 可视化图表。推荐强度分三级：Strong / Worth exploring / Speculative。
3. **审讯循环**：选定一个候选后，启动 `grilling` 走决策树，同时驱动 `domain-modeling` 更新术语表。

生成的 HTML 报告不进入仓库——写入 OS 临时目录并自动打开。这是一种刻意为之的"不污染代码库"的设计。

---

## 词汇层：两个模型级参考

这两个技能是 Matt 体系中唯一被设置为**模型调用**（Model-Invoked）的参考技能——其他技能在需要时会自动拉取它们：

### domain-modeling（领域建模）

一个**主动**的领域语言管理纪律。不是被动地读 `CONTEXT.md`，而是：

- 当用户用了与已有术语冲突的词语，当场挑战
- 当模糊或过载的词汇出现，提出精确的规范术语
- 发明边界场景来压力测试领域关系
- 交叉验证代码与用户陈述的一致性
- 术语一旦确定，立即写入 `CONTEXT.md`

**ADR（架构决策记录）的节制使用**：只有在三个条件同时满足时才提议创建 ADR——(1) 难以逆转 (2) 缺乏上下文会令人惊讶 (3) 真正经历了权衡取舍。Matt 的 ADR 可以是**一句话**——价值在于记录了"做出了什么决定以及为什么"，而非填充格式化的章节。

### codebase-design（代码库设计）

一个定义**深度模块**语言的共享词汇表：

- **Module**（模块）：有接口和实现的任何东西——函数、类、包、横跨多层的切片。刻意地尺度无关。
- **Interface**（接口）：调用者要知道的*所有*信息——类型签名、不变性、顺序约束、错误模式、必需的配置、性能特征。比 `API` 和 `signature` 都要宽。
- **Depth**（深度）= 小接口背后的行为量。**浅模块** = 接口几乎和实现一样复杂（避免）。
- **Seam**（接缝）= 不编辑原地就能改变行为的位置。来自 Michael Feathers。
- **删除测试**（deletion test）：想象删除这个模块。复杂性消失了 → 它是通道（pass-through）；复杂性在 N 个调用者处重新出现 → 它在赚钱（earning its keep）。

还有一套依赖分类（in-process → local-substitutable → remote-but-owned → true external），以及"Design It Twice"并行子 agent 模式，用于深度化一个模块时探索多个截然不同的接口设计。

---

## 跨会话与上下文管理

Matt 对"会话之间如何传递上下文"有明确的二分：

| 手段 | 行为 | 使用场景 |
|------|------|---------|
| **`/handoff`** | 将当前对话压缩为 handoff 文档写入 OS 临时目录，**打开新会话**引用该文件 | 需要全新上下文窗口但必须保留当前对话 |
| **`/compact`**（内置） | 在同一会话中总结早期回合 | 意图书阶段间的停顿；不在阶段中 compact |

handoff 文档包含"suggested skills"节（建议下一个 agent 调用的技能）并会脱敏。它在主线工作流中还有一个关键用例：`prototype` 分支。当 grilling 中遇到需要可运行答案的问题时，handoff 出 → 开新会话做 prototype → handoff 回来——两个方向都用 handoff 桥接。

---

## 元技能：writing-great-skills

这是 Matt 系列中最后一个被安装的技能，但它实际上是理解所有其他技能的**元钥匙**。它定义了技能设计的完整理论框架，核心概念包括：

**信息层级的运作**——什么放在 SKILL.md 里，什么推到外置引用文件中，什么推到外部。判定标准是**分支（branch）**：所有分支都需要的内容内联；只有部分分支需要的内容外置。

**何时拆分技能**——两种切分：(1) 按调用拆分：当你有独立的引导词需要独立触发；(2) 按顺序拆分：当一个步骤的后续步骤会诱惑 agent 提前结束当前步骤。

**失败模式的诊断**：
- **Premature Completion**（过早完成）：步骤还没真正做到位就说做完了。防御：先磨锐完成标准（便宜且局部）；只有标准确实无法更精确且你真的观察到 rushing 时，才隐藏后续步骤。
- **Duplication**（重复）：同一含义在多个地方出现——维护成本加倍，且人为抬高了该含义在信息层级上的位置。
- **Sediment**（沉积）：旧层次积累，因为"加东西安全、删东西危险"。没有修剪纪律的技能最终都会走向这里。
- **No-Op**（无操作指令）：一行 agent 默认就会做的事。`be thorough` 在 agent 已经 fairly thorough 时就是 no-op。修复是换更强的引导词（`relentless`），而非换技术。
- **Negation**（否定反噬）：禁止某行为 = 让该行为在上下文中更活跃。`don't think of an elephant` 让大象无处不在。治愈：用正面目标替换禁令，如"写一行注释"替代"永远不要写啰嗦的注释"。

这个理论框架让 Matt 的技能不是一个随意的脚本集合，而是一个有内在一致性的**设计系统**。每个技能都在公理（可预测性）和约束（上下文预算、认知预算）之间找到了自己的位置。

---

## 与 Superpowers 的深度对比

Superpowers（由 Jesse Vincent / obra 创建，截至 2026 年中 233K+ GitHub Stars）和 Matt Pocock Skills 是目前 Claude Code 生态中两个最有影响力的技能体系。它们解决同一个问题——"模型天生倾向于跳过流程"——但走了两条截然不同的路。

### 哲学分歧

| 维度 | Matt Pocock | Superpowers |
|------|-------------|-------------|
| **根本立场** | 技能是可组合的积木；你控制流程 | 技能是一个完整方法论；框架引导流程 |
| **首要瓶颈** | 需求清晰度（"你根本不知道你要什么"） | 执行可靠性（"你写了代码但那是错的"） |
| **自主性 vs 可控性** | 开发者始终在驾驶位 | 用户规划后放手让 agent 自主执行 |
| **流程重量** | 轻——从工具箱里拿你要的 | 重——完整 7 阶段流水线 |
| **设计灵感** | 编译器设计（拓扑排序决策依赖）、DDD（领域语言）、Feathers/Ousterhout（接缝与深度） | Google 工程实践、严格 TDD、subagent 编排 |

### 架构差异

```
Matt Pocock 的架构：
  技能是积木 → 你组装它们 → 你决定跳过什么
  上下文预算由你控制（模型调用 vs 用户调用）

Superpowers 的架构：
  技能是管道 → 系统编排它们 → 管道阶段有硬门槛
  上下文始终加载引导指令（~2000 token hook）
```

**上下文管理策略对比**：

- Matt：通过 `handoff`（手递手）和 `compact`（压缩）进行**显式的**、开发者控制的上下文管理。`implement` 每次都从干净的 ticket 开始。
- Superpowers：通过**架构手段**（每个任务一个全新子 agent）自动隔离上下文污染——代价是每个子任务都有 agent 启动开销。

**子 agent 使用模式对比**：

- Matt：子 agent 用于**特定维度**——`code-review` 的两个轴并行审查、`codebase-design` 的 Design It Twice 多接口探索、`research` 的后台调研。
- Superpowers：子 agent 是**默认执行单元**——`subagent-driven-development` 为每个原子任务派发一个子 agent，带两阶段审查。

### 需求工程对比

这是两个体系差异最大的领域：

| | Matt Pocock `grilling` | Superpowers `brainstorming` |
|---|---|---|
| **交互模式** | 一次一个问题，逐个解决依赖 | Socratic 澄清，输出设计文档 |
| **状态管理** | 落盘到 `CONTEXT.md` + ADRs | 落盘到 `docs/superpowers/specs/` |
| **代码库感知** | 事实从代码库查找，不问用户 | 以设计文档为中心 |
| **输出产物** | 清晰的领域术语 + 架构决策记录 | 批准的设计文档 |
| **硬门槛** | 无代码门槛——但深入的审讯本身就是门槛 | **硬门槛**：文档被批准前不能写任何代码 |

Superpowers 的 brainstorming 有一个 Matt 体系没有的特点：**硬设计门槛**。这防止了 agent 在需求不清时就开始写代码。Matt 的立场是用审讯本身的质量来替代硬门槛——一个彻底的 grilling 结束后，你自然就知道要做什么了。

### TDD 哲学分裂

这是两个体系间"真正的哲学分裂"：

- **Superpowers**：经典 RED → GREEN → REFACTOR 三步循环。**代码写在测试之前会被删除**。这是 Superpowers 三条铁律之首。
- **Matt Pocock**：RED → GREEN 两步循环。重构属于 `code-review` 阶段。理由是重构需要全局视角，不应在单个测试的局部循环中进行。

两者都有道理，但对工程实践有不同影响。Superpowers 的方法更激进地保证"每行代码都有测试"；Matt 的方法更务实地区分"验证行为"和"改善结构"两个不同阶段。

### 调试方法论对比

| | Matt `diagnosing-bugs` | Superpowers `systematic-debugging` |
|---|---|---|
| **阶段数** | 6 | 4 |
| **核心创新** | Phase 1：构建反馈回路的 10 种方法 | Root-cause investigation 作为硬前提 |
| **进入 Phase 2 的条件** | tight + red-capable + deterministic + fast + agent-runnable | 已确认根本原因 |
| **可证伪假设** | 3-5 个排序假设，展示给用户后再测试 | 假设-实验-修复线性流程 |
| **事后** | 向 `improve-codebase-architecture` 提交架构改进建议 | 修复验证 |

Matt 的调试流程更长、更偏执——尤其是 Phase 1 对反馈回路的投资和 Phase 2 的"每个剩余元素都负载关键"的最小化要求。Superpowers 的流程更直接，但"必须先做根因分析"是相似的硬前提。

### 覆盖范围

Superpowers 的覆盖范围远超 Matt 系列：
- **科学计算**：80+ skills（matplotlib, pytorch, scikit-learn, biopython, pennylane...）
- **设计**：17 skills（design systems, animations, micro-interactions...）
- **后端**：CQRS, event sourcing, saga orchestration, Temporal...
- **数据处理**：Airflow, dbt, Spark, Polars...
- **文档**：docx, pdf, xlsx, pptx 编程式创建/编辑

Matt 系列刻意保持狭窄——聚焦于**软件工程过程**本身（需求 → 设计 → 规划 → 构建 → 审查 → 调试 → 架构健康），不扩展到领域特定的实现知识。这个选择有利有弊：利在专注和深度；弊在你还需要额外的领域知识来源。

### 可移植性

- **Superpowers v6.0.0**（2026 年 6 月）已将技能语言**供应商中立化**，支持 18+ 编程 harness（Claude Code, OpenAI Codex, Cursor, Gemini CLI, GitHub Copilot CLI, Windsurf, Kiro, Aider 等）。
- **Matt Pocock Skills** 目前是针对 **Claude Code** 设计。它的很多概念（模型调用 vs 用户调用、上下文指针、引导词）依赖于 Claude Code 的技能系统机制。

### 规模与社区

- **Superpowers**：233K+ stars, 680K+ 安装量, 246+ 技能, 由 Prime Radiant 团队维护
- **Matt Pocock Skills**：由 Matt Pocock（TypeScript 教育者、Total TypeScript 创始人）个人维护，规模更小但每一行都经作者亲自打磨

### 选择指南

| 你的情况 | 推荐 |
|---------|------|
| 需求是你最大的瓶颈，常常"做完了才发现不是用户要的" | **Matt Pocock** — grilling 是三个框架中最锋利的需求审讯循环 |
| 你需要在多个 AI 编程工具间切换（今天用 Claude Code，明天用 Cursor） | **Superpowers** — 多 harness 支持 |
| 你想把整个功能丢给 agent 让它自己搞定 | **Superpowers** — subagent 流水线为此设计 |
| 你想保持对流程的完全控制，只在不同阶段借助 AI | **Matt Pocock** — 积木式组合 |
| 你需要 Python 科学计算、生物信息学、量子计算等领域的 AI 辅助 | **Superpowers** — 覆盖 80+ 科学技能 |
| 你的代码库架构债务在积累，需要系统性的架构审视 | **Matt Pocock** — improve-codebase-architecture 是独特能力 |
| 团队需要统一的 AI 编程标准 | **Superpowers** 或 **Agent Skills** — 完整的团队级方法论 |
| 你想深入理解技能设计并可能自己写技能 | **Matt Pocock** — writing-great-skills 是唯一的技能设计理论框架 |
| 小任务、明确需求——不想为流程负重 | **两个都不用** — 裸提示就够了 |

### 一个尚未回答的问题

截至 2026 年中，没有公开的 A/B 测试对比任何技能框架 vs 同一模型的裸提示基线。这意味着我们无法确定：
- 技能框架的 token 开销在简单任务上是否会降低结果质量
- 流程纪律带来的提升是否超过引导指令的注意力分散成本
- 不同模型对同一套技能指令的响应差异有多大

在进行你自己的评估时，建议在同一个中等复杂度的任务上分别跑 Matt 技能、Superpowers 和裸提示，对比结果质量和耗时。这是目前唯一可靠的选型方法。

---

## 最佳实践总结

基于 Matt 技能体系的设计理念和实际使用经验，以下是关键的最佳实践：

### 1. 理解"重"与"轻"的边界

不是所有场景都需要完整的 grill → spec → tickets → implement 流水线：

- 🔴 **用完整流水线**：多会话构建、多人协作、需求本身还在模糊状态
- 🟡 **只用 grilling + implement**：单人项目，需求基本清楚但需要打磨，一个会话能做完
- 🟢 **只用一个技能**：修复一个明确 bug → 只用 `diagnosing-bugs`；审查一个 PR → 只用 `code-review`

### 2. 投资 Phase 1——反馈回路是调试的超能力

Matt 调试方法论中最容易被低估的是 Phase 1。当 bug 出现时，最自然的冲动是"读代码，建假设"——而 `diagnosing-bugs` 明确禁止这样做。先花时间把反馈回路做到 tight + red-capable + deterministic + fast + agent-runnable。一旦这个回路存在，定位根因只是一个时间问题。

### 3. 让 grilling 做它最擅长的事——依赖排序

grilling 的核心价值不在于"问清楚需求"（这谁都会），而在于**按依赖关系组织决策**。当你面对一组相互缠绕的决策时，grilling 会先解决被依赖的决策，再处理依赖它的决策。这是从编译器设计中借来的拓扑排序思维——如果你自己都不知道哪个决策应该先做，把问题交给 grilling。

### 4. 善用 handoff 保护智能区

context window 是有限的，模型的推理质量在接近窗口上限时会下降（smart zone 约 120K token）。不要试图在一个会话里做所有事：
- grill → spec → tickets 保持同一个窗口
- 每个 `implement` 开新会话
- 需要分支探索时用 `handoff`，不要在当前窗口里堆砌

### 5. CONTEXT.md 是活文档——边用边更新

不要等到"项目文档完善了"才开始用 `domain-modeling`。在 grilling 中每确定一个术语，立即写入 CONTEXT.md。好的领域词汇是后来所有工作的基础设施——ticket 命名、测试命名、agent brief 编写都用同样的语言。

### 6. ADR 是稀有物种——只在三个条件同时满足时创建

不要为每个决策写 ADR。Matt 的标准是：难以逆转 + 缺乏上下文会令人惊讶 + 真正经历了权衡取舍。大多数日常决策不满足这三个条件——放过它们。一个一句话的 ADR 比一个填满模板但毫无信息量的 ADR 有价值得多。

### 7. 代码审查时，让两个轴独立运行

不要在一段文字里混合 Standards 和 Spec 的审查发现。它们是两个不同的子 agent，运行在不同的评判标准上。一个改动可能 Standards pass + Spec fail（代码优雅但功能错了），也可能反过来。分开报告，分开评估。

### 8. 写 Agent Brief 时，想象它会在三周后被读取

不写文件路径和行号。写类型名、函数签名、行为契约。验收标准要独立可验证。明确写出范围之外的东西——这比写出范围内的东西更能防止 agent 过度发挥。

### 9. 技能也需要修剪

你的 `.claude/skills/` 目录会积累沉积累（sediment）。如果你发现自己不再使用某个技能，或者某个 user-invoked 技能你从不记得手动调用——考虑把它从安装列表中移除或降级。Matt 的 `writing-great-skills` 理论同样适用于你如何使用这些技能：保持 relevance，删除 no-op。

### 10. 不要默认把所有技能设为模型调用

每增加一个模型调用的技能，每轮对话都会消耗额外的 token。在 Matt 的体系中，只有 `domain-modeling` 和 `codebase-design` 是模型调用的——因为它们是被其他技能拉取的共享词汇层。其余都是用户调用——你需要它们时才叫它们。如果你觉得某个技能"应该自动触发"，先问自己：它真的需要在每个回合都在上下文里吗？

---

## 参考资源

- [Matt Pocock Skills 仓库](https://github.com/mattpocock/skills)
- [Superpowers 仓库](https://github.com/obra/superpowers)
- [Superpowers vs Agent Skills vs Pocock: Three Philosophies of AI Coding Workflows](https://dev.to/jamilxt/superpowers-vs-agent-skills-vs-pocock-three-philosophies-of-ai-coding-workflows-e6n) — 第三方深度对比
- [smart zone 概念](https://www.aihero.dev/ai-coding-dictionary/smart-zone) — AI Hero 的 AI 编程词典
- [Superpowers 中文增强版](https://github.com/jnmetacode/superpowers-zh) — 116K+ stars 的 Superpowers 汉化版
- [LUNARTECH Superpowers Complete Skills Library](https://www.lunartech.ai/blog/lunartech-superpowers-complete-claude-ai-skills-library) — 246+ 技能的完整目录

---

*本文基于 Matt Pocock Skills 仓库（截至 2026 年 7 月）的实际技能文件撰写。所有技能内容、设计理念和术语均来源于原始 SKILL.md 文件及其关联参考文档。*
