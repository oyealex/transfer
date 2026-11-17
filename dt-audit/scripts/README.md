# 脚本

Skill 只依赖八个入口，全部是确定性变换：取数、收敛、扫描、装配、统计、渲染。`git` 只用来补读上下文，不参与取改动。

| 入口 | 什么时候用 |
|---|---|
| `fetch_sr_mr_index` | 检测对象是 SR / AR，用它拿关联的 MR 清单（只给元数据） |
| `fetch_mr_details` | 检测对象是一个 MR 地址，用它拿元数据 |
| `fetch_mr_changes` | 两种输入都走它：把 MR 的改动落成 `manifest.json` + 逐文件 diff |
| `run_scope` | 步骤 1：按范围规则分类变更文件，输出范围表数据与体量指标 |
| `run_scan` | 步骤 2：扫测试 diff，输出测试退化候选 |
| `run_assemble` | 步骤 1–3：生成证据表骨架、合并枚举片、回填裁定片 |
| `run_stats` | 步骤 5 出报告前，解析 `evidence.md`、算指标、校验一致性 |
| `run_render` | 步骤 5 把 `stats.json` + `evidence.md` + `manifest.json` + `narrative.json` 装配成报告并落盘；带 `--check` 时先回读校验、不通过不写文件。`render_report` 是它的纯函数内核，只返回文本不落盘 |

**变更单元恒为 MR。** 没有提交批次这一路。

**改动一律由 `fetch` 产出**：`manifest.json` + 逐文件 diff，两种输入没有区别。本地仓可不可达只影响 Skill 能额外读到多少上下文，不影响改动内容的来源。仓可达时 `git` 只做只读补充（`show` / `log` / `cat-file` / `remote -v`）。

```
scripts/
├── run.py                  # 启动器
└── dt_audit/
    ├── __init__.py         # 对外导出
    ├── __main__.py         # python -m dt_audit
    ├── contracts.py        # 数据契约（唯一来源）
    ├── errors.py           # 异常类型（取数模块与此处共用，避免循环导入）
    ├── cli.py              # 参数解析与错误契约
    ├── index.py            # fetch_sr_mr_index
    ├── mr.py               # fetch_mr_details
    ├── fetch.py            # fetch_mr_changes
    ├── scope.py            # run_scope
    ├── scan.py             # run_scan
    ├── assemble.py         # run_assemble
    ├── render.py           # render_report
    └── stats.py            # run_stats
```

三个取数实现负责把 MR 元数据与改动落盘；`scope` / `scan` / `assemble` / `stats` / `render` 都是纯本地处理，不下判断。

`render` 只做两件事：标量替换、行块展开。它不做任何判断——选哪几条问题、影响档、措辞都来自 `narrative.json`；也不做统计——数字全部来自 `stats.json`。模板语法见 `render.py` 的模块 docstring。

## 公开 API

```python
from dt_audit import (
    fetch_sr_mr_index, fetch_mr_details, fetch_mr_changes,
    run_scope, run_scan, run_assemble, run_stats,
)

index = fetch_sr_mr_index("SR-1234")        # 或 "AR-77"
mrs   = fetch_mr_details(["https://git.corp/group/repo/-/merge_requests/1234"])
man   = fetch_mr_changes(["https://…/-/merge_requests/1234"], ".dt-audit/SR-1234/_diffs")
scope = run_scope(manifest=Path(".dt-audit/SR-1234/_diffs/manifest.json"))
scan  = run_scan(manifest=Path(".dt-audit/SR-1234/_diffs/manifest.json"))
stats = run_stats(evidence=Path(".dt-audit/SR-1234/evidence.md"))
```

只有这八个入口。不要增加配置加载、仓库检索、diff 计算之类的出口——本地仓路径由 Skill 询问用户后自行校验，只用于补读上下文；改动内容一律由 `fetch` 产出。

## 运行

```bash
python run.py index --id SR-1234 --pretty
python run.py index --id AR-77 --pretty
python run.py mr    --url https://git.corp/group/repo/-/merge_requests/1234 --pretty
python run.py fetch --url https://…/-/merge_requests/1234 --out .dt-audit/SR-1234/_diffs --pretty

# 步骤 1：范围收敛 + 生成证据表骨架
python run.py scope --manifest .dt-audit/SR-1234/_diffs/manifest.json --out .dt-audit/SR-1234/scope.json --pretty
python run.py assemble --mode init --evidence .dt-audit/SR-1234/evidence.md \
  --manifest .dt-audit/SR-1234/_diffs/manifest.json --scope .dt-audit/SR-1234/scope.json \
  --index .dt-audit/SR-1234/index.json --tier "仅 diff" --pretty

# 步骤 2：退化候选 + 合并枚举片
python run.py scan --manifest .dt-audit/SR-1234/_diffs/manifest.json --pretty
python run.py assemble --mode merge --evidence .dt-audit/SR-1234/evidence.md \
  --shards .dt-audit/SR-1234/shard-01.json --pretty

# 步骤 3：回填裁定片
python run.py assemble --mode judge --evidence .dt-audit/SR-1234/evidence.md \
  --judges .dt-audit/SR-1234/judge-01.json --pretty

# 步骤 5
python run.py stats --evidence .dt-audit/SR-1234/evidence.md --pretty
python run.py render --stats .dt-audit/SR-1234/stats.json --evidence .dt-audit/SR-1234/evidence.md --manifest .dt-audit/SR-1234/_diffs/manifest.json --narrative .dt-audit/SR-1234/narrative.json --out .dt-audit/SR-1234/DT-AUDIT-REPORT.md --check
```

`--url` 可重复，一次传完整批地址。`stats` 默认把 `stats.json` 写在证据表同目录，用 `--out` 覆盖。

`assemble` 的三种模式：

- `init`：从 manifest / scope / index 生成 `evidence.md` 骨架（概览、变更单元、范围、五张空表）。没有 `--index` 时，单元的标题 / 作者 / 提交会留空，由 Skill 补；`--meta` 可直接给 `{object_type, object_id, units:[…]}`，字段同「变更单元」表头。
- `merge`：合并枚举片 JSON，按「位置 + 条件摘要」去重、统一编号、算「断言随动」。
- `judge`：回填裁定片 JSON 的状态、断言证据、档位与「只验交互」。

`scope` / `scan` / `assemble` 的返回都是对同一份输入的确定性变换，不含判断；判不准的内容留给 Skill 复核。

或：

```bash
cd scripts && python -m dt_audit index --id SR-1234 --pretty
```

要求 Python ≥ 3.8，只用标准库。

## 错误契约

| 退出码 | 含义 | 抛出 |
|---|---|---|
| 0 | 成功（`warnings` 可非空，属正常） | — |
| 1 | 未预期异常，或证据表校验不通过 | 任意异常 / `stats.EvidenceError` |
| 2 | 未找到（SR / AR / MR / 仓） | `errors.NotFoundError` |
| 3 | 上游不可达或鉴权失败 | `errors.UpstreamError` |
| 64 | 命令行参数错误 | `cli._Parser.error` |

参数错误必须与「未找到」区分开：Skill 靠退出码判断是终止还是继续，混用会让它把一条敲错的命令读成「这个 SR 没有 MR」。

`stats` 的两类失败要分开看：

- **语义校验失败**（字段取值不对、合计对不上）：`stats.json` 仍然写出，带错误清单，退出码 1。Skill 必须修证据表后重跑，不得手改报告数字。
- **结构解析失败**（缺段、表头不符、文件不存在）：走不到写文件那一步，只往 stderr 打错误、退出码 1，`stats.json` 不会生成。Skill 先补证据表结构再重跑。

stdout 只允许 JSON。日志、进度、警告文本一律写 stderr。

## 输出样例

`index` / `mr`（两者结构一致，`mr` 的 `object_type` 恒为 `"MR"`，多地址时 `object_id` 追加 `+N`）：

```json
{
  "schema_version": "1.1",
  "object_type": "SR",
  "object_id": "SR-1234",
  "fetched_at": "2026-11-17T10:00:00+08:00",
  "mrs": [
    {
      "mr_id": "1234",
      "title": "订单校验逻辑",
      "web_url": "https://git.corp/group/repo/-/merge_requests/1234",
      "author": "zhangsan",
      "repo": {
        "project_path": "group/repo",
        "web_url": "https://git.corp/group/repo"
      },
      "target_branch": "master",
      "state": "merged",
      "linked_via": "AR-77",
      "first_commit": {"sha": "a1b2c3d4e5f6...", "short_sha": "a1b2c3d"},
      "last_commit": {"sha": "9f8e7d6c5b4a...", "short_sha": "9f8e7d6"}
    }
  ],
  "warnings": []
}
```

`fetch`：

```json
{
  "schema_version": "1.1",
  "fetched_at": "2026-11-17T10:00:00+08:00",
  "out_dir": "D:\\work\\.dt-audit\\SR-1234\\_diffs",
  "mrs": [
    {
      "mr_id": "1234",
      "web_url": "https://git.corp/group/repo/-/merge_requests/1234",
      "files": [
        {
          "path": "src/main/java/com/x/OrderService.java",
          "old_path": null,
          "change_type": "modified",
          "is_binary": false,
          "additions": 12,
          "deletions": 3,
          "diff_path": "1234/src__main__java__com__x__OrderService.java.diff",
          "truncated": false
        }
      ]
    }
  ],
  "warnings": []
}
```

落盘结构：

```
<out_dir>/
├── manifest.json                 # 与返回值同构
└── <mr_id>/
    └── <扁平化路径>.diff          # 逐个文件的 unified diff
```

`stats` 的返回结构：指标与定性在顶层 `指标`、`定性` 两个块里，问题分布取 `分布表`（已排序、只含命中项），逐类明细在 `问题分布`；证据缺口在 `证据缺口`（含 `无法获取单元数`），测试退化条数在 `测试退化`（`计入条数` / `提示条数`）。另有 `短路`（生产改动行 = 0）与 `抽样`（3 个已断言 / 断言偏弱 + 全部未断言的编号，供步骤 4 直接用）。字段含义见本文「报告口径」。

## 报告口径

报告骨架与 3.4 附录在 `references/报告模板.md`；本节讲每个数字怎么算、阈值是多少、定性怎么判。数值以本目录 `stats.py` 的常量为准，`stats.json` 的 `水位` 块会原样输出，报告 3.4.7 是给读者看的副本。

### 指标

| 指标 | 公式 |
|---|---|
| 变更生产代码量（KLOC） | 范围表里「分类 = 生产代码且语言为 Java / Python」的新增行之和 ÷ 1000；仅陈述 |
| 全部逻辑点 | 按逻辑点规则枚举、按身份去重后的总数，**所有比率共用这个分母** |
| 可判定逻辑点数 | 全部逻辑点 − 无法判定 |
| 断言覆盖率 | （已断言 + 断言偏弱）÷ 全部逻辑点 |
| 有效断言率 | 已断言 ÷ 全部逻辑点 |
| 实现被替换率 | 实现被替换 ÷ 全部逻辑点 |
| 证据完整度 | 可判定 ÷ 全部逻辑点 |
| 新增 DT 数 | 新增的测试方法数 |
| 新增 DT 密度 | 新增 DT 数 ÷ KLOC；KLOC 为 0 时写「不适用」 |

**分母只有一个**：逻辑点类比率都除以全部逻辑点，包括读不到的那部分；读不到多少由证据完整度单独说明。由此必然有 `有效断言率 ≤ 断言覆盖率 ≤ 证据完整度`，`stats` 会校验，违反即报错。

**一条断言的身份是「用例 + 断言行号」**：同一身份只计一次，档位取它在所有绑定逻辑点上的最高档（有效 > 形状 > 存活）。

**断言统计范围**：状态判定可以用既有用例的断言；有效断言条数只统计新增或修改用例内的断言。问题类型与报告 2.1 一律引用这条口径。

**C / C++**：不枚举逻辑点，只给文件级结论，不进任何分母与 KLOC，按文件数统计。

**KLOC 按范围表逐文件累加**：不含删除行、测试文件、C / C++ 与被排除的分类；`stats` 会用范围表反算并与「变更生产代码行数」对账，对不上给 warning。

### 水位

| 指标 | 阈值 |
|---|---|
| 证据完整度 | 下限 0.60；≥0.90 完整、≥0.60 较完整、其余不完整 |
| 断言覆盖率（2.3 总评） | 良好 0.70、低 0.40 |
| 实现被替换率（2.2 总评） | ≥0.15 高、等于 0（且可判定 > 0）低、其余中 |
| 有效断言率 | 不设水位，只陈述数值，仅用于 1.2 定性 |
| 新增 DT 密度 | 参考带 5–30 个/KLOC，仅用于措辞，不参与判定 |

2.1 不判高 / 中 / 低。

### 定性（1.2 第一行）

| 步 | 条件 | 结论 |
|---|---|---|
| 前置 | 全部变更单元均为「无法获取」 | 证据不足（不产出质量结论） |
| R0 | 全部逻辑点 = 0 | 合格 |
| R1 | 新增 + 修改用例 = 0 且证据完整度 < 60% | 待改进 |
| R2 | 证据完整度 < 60% | 证据不足 |
| R3 | 有效断言率 < 50% 或计入的测试退化 > 2 条 | 待改进 |
| R4 | 有效断言率 ≥ 75% 且全部逻辑点 ≥ 8 且计入的测试退化 = 0 | 优秀 |
| R5 | 其余 | 合格 |

命中即停。三个取向：**优秀要少**，三个条件同时满足才给；**待改进看可见的问题**，没带测试又看不清覆盖算待改进，不算证据不足；**证据不足只做兜底**，不拿它当「不想判」的出口。

R4 不再单列证据完整度条件：有效断言率的分母是全部逻辑点，≥75% 已经意味着完整度 ≥75%。

### 零值的唯一口径

- 逻辑点 = 0：定性「合格」；分布位置写「无可枚举分支」；证据缺口写「不适用（无逻辑点）」；三个率写「不适用」；2.x 总评写「无行为分支，不评估」；跳过步骤 4。用例类与信号类命中按 0 计（没有分支就没有可守的对象）。存在 C / C++ 受限文件时必须同时写受限文件数，且**不得**写「本次改动未引入行为分支」。
- 逻辑点 > 0 但可判定 = 0：保留 3.3 矩阵，三个率照常给 0.0%，2.x 总评写「全部无法判定，不评估」，定性按 R1 / R2。

### 章节与排序

- 章节骨架、表头、顺序以 `references/报告模板.md` 为准；本节只管口径。
- 1.3 与 2.3 的条目按影响档降序；1.2 分布表按「严重度 → 单位 → 数量」排序；2.1 / 2.2 的问题清单按严重度（严重 → 高）排序。
- 占比分母：逻辑点类用全部逻辑点，用例类用新增 + 修改用例数（既有用例不计入），信号条写「—」。
- **断言质量低只统计触达了本次逻辑点的用例**；与本次改动无关的新增测试不进结论。逻辑点为 0 时用例类与信号类命中按 0 计，避免「合格」与命中问题并存。
- 2.x 必含：**2.1** 断言总数与有效条数、无断言用例、只用交互验证、测试退化（计入与提示分开）、断言抽检（抽查数 / 通过 / 不通过）；**2.2** Mock 情形分布、实现被替换率、被替换逻辑点清单、契约漂移；**2.3** 断言覆盖率、有效断言率、未覆盖清单、异常路径与边界细分。
- 3.1 按 MR 列来源表，另加八条说明：取数、去重、证据完整度、无法判定清单、无法获取清单、仅 diff 的 MR、受限语言、关联能力。
- 3.3 行数等于全部逻辑点；C / C++ 不进本表。

## 注意

- **不要**在取数脚本里判断测试文件、生成代码、Mock、断言档位。这些判据只在 Skill 里有一份，脚本再做一遍必然打架。
- **不要**为了让 diff 上下文更长去拉两端文件重算——那会让兜底路径依赖完整文件，正是它拿不到的东西。平台给多少上下文就落多少。
- **不要**把 diff 塞进 stdout。一个 MR 的 diff 动辄几十 KB，塞 JSON 会撑爆上下文；写文件，Skill 需要哪份读哪份。
- `stats` 只解析固定表头、做算术、查结构矛盾，**不下沉任何判断**：状态、断言档位、Mock 类别都已由 Skill 写进证据表。
- **不要**在脚本里做网络重试以外的业务逻辑，保持无状态。
- 输出必须幂等：同一输入两次调用，JSON 字节级一致（`fetched_at` 除外）。
