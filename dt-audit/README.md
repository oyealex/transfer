# DT 质量审计

评估一次改动里的**设计测试（DT）有没有盯住改动本身**：每条行为分支有没有用例跑到、断言有没有核对这条分支的结果。只给近似数据与分类，不给测试是否充分、功能是否正确的结论。

## 结论长什么样

报告是一份 Markdown，四块内容：

1. **定性**：优秀 / 合格 / 待改进 / 证据不足，由五类判定的占比算出。
2. **分布**：有效、未覆盖、无效、低价值、不确定各多少点、占比多少，分母是全部逻辑点。
3. **缺口 Top 3**：按文件聚合问题点数，降序取前三。
4. **逐点问题与用例名单**：每个点为什么归这一类；哪些用例完全无效、哪些只判了形态。

## 概念

三个概念：**逻辑点**、**用例**，以及连起两者的**关联**。

| 概念 | 含义 |
|---|---|
| 逻辑点 | 本次新增或修改的一条行为分支；不可解析片段折一个占位点 |
| 用例 | 新增 / 修改 / 既有三类测试方法 |
| 关联 | 用例能不能执行到某个点；断言有没有核对这个点的结果 |

每个点恰归一类，五类之和等于全部逻辑点：

| 分类 | 一句话 |
|---|---|
| 有效 | 至少一条覆盖它的用例，断言核对到了这条分支的结果 |
| 未覆盖 | 没有任何用例能执行到这条分支 |
| 无效 | 有用例执行到了，但断言没核对该分支的结果，或跑的是假实现 |
| 低价值 | 断言只判非空、大小、类型这类形态 |
| 不确定 | 证据不足，判不出上面任一类 |

判定规则（格四态、点取最好、13 条原因）见 [问题分类与判定](references/问题分类.md)。

## 五步

| 步 | 做什么 | 产出 |
|---|---|---|
| 1 | 取数、收敛范围、判短路 | `evidence.md` 的概览 / 单元 / 范围 |
| 2 | 枚举逻辑点、列用例、建关联 | 用例清单 + 逻辑点证据的前五列 |
| 3 | 逐点判格、定分类与原因 | 逻辑点证据的问题 / 原因 / 说明 |
| 4 | 跑脚本自检、抽看 3 条 | 修正后的证据表 |
| 5 | 算数、写判断、装配 | `narrative.json` + `DT-AUDIT-REPORT.md` |

同一份 `evidence.md` 贯穿五步；`stats.json` 与报告都由脚本产出，不手写。

## 快速开始

```bash
# 取数
python scripts/run.py fetch --url <MR 地址> --out .dt-audit/SR1234/_diffs --pretty

# 范围 + 骨架
python scripts/run.py scope --manifest .dt-audit/SR1234/_diffs/manifest.json --out .dt-audit/SR1234/scope.json
python scripts/run.py assemble --mode init --evidence .dt-audit/SR1234/evidence.md \
  --manifest .dt-audit/SR1234/_diffs/manifest.json --object-type SR --object-id SR1234

# 合并枚举片、回填裁定
python scripts/run.py assemble --mode merge --evidence .dt-audit/SR1234/evidence.md --shards shard-1.json
python scripts/run.py assemble --mode judge --evidence .dt-audit/SR1234/evidence.md --judges judge-1.json

# 算数与报告
python scripts/run.py stats --evidence .dt-audit/SR1234/evidence.md
python scripts/run.py render --stats .dt-audit/SR1234/stats.json --evidence .dt-audit/SR1234/evidence.md \
  --narrative .dt-audit/SR1234/narrative.json --check
```

三个取数函数（`fetch_sr_mr_index` / `fetch_mr_details` / `fetch_mr_changes`）需要先由使用方实现，签名见 [scripts/README](scripts/README.md)。

## 证据三档

| 档位 | 条件 | 能读到什么 |
|---|---|---|
| 含本地仓 | `fetch` 成功且本地仓可达、remote 匹配 | manifest + diff + 仓内任意文件 |
| 仅 diff | `fetch` 成功但本地仓不可达 | manifest + diff |
| 无法获取 | `fetch` 失败 | 什么都没有，不进分母 |

档位只说明能读到多少上下文，不决定能不能判；读不到结论所需的证据时记「不确定」。

## 两个省成本的机制

- **短路**：生产改动行 = 0（只改测试、只改文档）时跳过步骤 2–4，直接记「合格」，报告结构照出。
- **分片**：生产改动行 > 800 按文件簇切枚举片；合并后逻辑点 > 50 时按 50 点一批切裁定片。窗口、契约与提示词见 [分片协议](references/分片协议.md)，调度不做规定。

## 目录

```
SKILL.md                  # 五步流程与判定口径
README.md                 # 本文件
references/
  问题分类.md             # 关联、格四态、五分类、13 原因、定性
  逻辑点规则.md           # 什么算一条分支、编号、条件变化
  报告模板.md             # 报告骨架 + 3.4 释义
  分片协议.md             # 枚举片 / 裁定片契约
  范围规则.md             # 哪些文件纳入
  测试框架识别.md         # 怎么找用例与断言
scripts/
  run.py                  # 命令行入口
  dt_audit/               # 13 个模块
  README.md               # 命令、错误码、报告口径
```

## 限制

- 只覆盖 Java / Python 的行为分支；C / C++ 只统计文件数与语言分类，不进占比分母。
- 判据来自 diff 的静态阅读，不跑测试、不构造错误实现。
- 只回答「改动有没有被盯住」，不评估需求质量、实现质量、同名测试规范。
- 场景是否充分、用例数量够不够，判不了也不判；报告里只出现能指到行号的事实。

## 维护

- 阈值只在 `scripts/dt_audit/stats.py` 定义，`stats.json` 的 `水位` 块原样输出；报告 3.4.3 是读者副本，改常量时同步。
- 证据表的段名与表头由 `stats.py` 的 `SECTIONS` 决定，改这里必须同步 `assemble.py`、`报告模板.md` 与 `SKILL.md`。
- 判定口径改一处就要改两处：本文档与 `references/问题分类.md`。
