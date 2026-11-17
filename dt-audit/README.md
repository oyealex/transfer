# dt-audit

审计一个 MR（或 SR / AR 关联的一组 MR）的**设计测试（DT）质量**：代码自己引入的分支，有没有被自己的测试盯住；盯住的是不是真实实现。

它不运行测试，只读源码与 diff，产出**一份自包含的 Markdown 报告**（`DT-AUDIT-REPORT.md`）——报告是唯一交付物，读者不需要拿到本目录的任何文件。

## 它回答什么

1. **代码被自己的测试盯住了吗？** 每个被改动引入或修改的行为分支（逻辑点），有没有断言读到它的产出。
2. **盯住的是真实实现吗？** 覆盖该分支的用例，执行的是生产代码，还是被 Mock / 桩 / 探针换掉的假实现。

不回答：需求覆盖是否完整、实现有没有写对、测试当前是红是绿、DT 命名与目录是否规范。

## 报告里有什么

- **1.2 结论**：四类问题的命中数量与占比、证据缺口、三个率、一句话判断、DT 质量定性（优秀 / 合格 / 待改进 / 证据不足）。
- **2.1 / 2.2 / 2.3**：断言质量、Mock 质量、分支覆盖三个维度，各自带问题清单与补测建议。
- **3.x 附录**：逐 MR 的证据来源、变更构成、逐逻辑点状态矩阵、术语与判定规则。

三个率同分母（全部逻辑点）：

```
断言覆盖率 = (已断言 + 断言偏弱) / 全部逻辑点
有效断言率 = 已断言 / 全部逻辑点
实现被替换率 = 实现被替换 / 全部逻辑点
证据完整度 = 可判定 / 全部逻辑点
```

恒有 `有效断言率 ≤ 断言覆盖率 ≤ 证据完整度`，由 `stats` 校验。

## 快速开始

要求 Python ≥ 3.8，只用标准库；`git` 可选，仅用于补读本地仓上下文。

完整流程（五步）：

```bash
# 1. 取数、范围收敛、生成证据表骨架
python scripts/run.py index --id SR-1234 --pretty > .dt-audit/SR-1234/index.json
python scripts/run.py fetch --url https://…/-/merge_requests/1234 --out .dt-audit/SR-1234/_diffs --pretty
python scripts/run.py scope --manifest .dt-audit/SR-1234/_diffs/manifest.json --out .dt-audit/SR-1234/scope.json
python scripts/run.py assemble --mode init --evidence .dt-audit/SR-1234/evidence.md \
  --manifest .dt-audit/SR-1234/_diffs/manifest.json --scope .dt-audit/SR-1234/scope.json \
  --index .dt-audit/SR-1234/index.json

# 2–3. 盘点、裁定；有分片时合并回证据表
python scripts/run.py scan --manifest .dt-audit/SR-1234/_diffs/manifest.json
python scripts/run.py assemble --mode merge --evidence .dt-audit/SR-1234/evidence.md --shards …
python scripts/run.py assemble --mode judge --evidence .dt-audit/SR-1234/evidence.md --judges …

# 4. 按 stats 的「抽样」块做自查，结论写回复核列

# 5. 算数 → 写判断 → 装配
python scripts/run.py stats  --evidence .dt-audit/SR-1234/evidence.md --out .dt-audit/SR-1234/stats.json
python scripts/run.py render --stats .dt-audit/SR-1234/stats.json \
  --evidence .dt-audit/SR-1234/evidence.md \
  --manifest .dt-audit/SR-1234/_diffs/manifest.json \
  --narrative .dt-audit/SR-1234/narrative.json \
  --out .dt-audit/SR-1234/DT-AUDIT-REPORT.md --check
```

`scope` 分类、`scan` 扫退化候选、`assemble` 拼证据表，都是确定性变换；枚举、裁定、写判断仍由 Skill 做。`--check` 会回读生成的报告，核对数字、行数、章节与内部引用；不通过就不落盘。

## 工作方式

| 步骤 | 做什么 | 产出 |
|---|---|---|
| 1 确定范围与取数 | 拿 MR 清单、取 diff、分类收敛、按身份去重 | `evidence.md` 的概览、变更单元、范围三段 |
| 2 盘点 | 枚举逻辑点、录用例 / 断言 / Mock 清单、扫退化信号 | 五段表；超过 800 生产改动行时派枚举片 |
| 3 定状态 | 关联触达 → 产出对账 → 落六档之一 | 逻辑点证据补全；超过 50 点派裁定片 |
| 4 抽样自查 | 抽 3 个 + 全部未断言项，核对「断言读的值真来自这条分支」 | 复核列 |
| 5 出报告 | `stats` 算数、`narrative` 写判断、`render` 装配并回读校验 | `stats.json` / 报告 |

贯穿全程的机制：

- **短路**：范围收敛后「生产改动行」（Java / Python 生产代码的新增 + 删除）= 0 时，跳过步骤 2–4，直接出报告，定性记「合格」，报告章节不变。
- **证据来源三档**：含本地仓 / 仅 diff / 无法获取。档位只决定能读到多少上下文，不决定能不能判。
- **逻辑点**：全流程的原子计量单位，一条被引入或修改的行为分支。枚举规则见 [references/逻辑点规则.md](references/逻辑点规则.md)。
- **状态六档**：无测试 / 已断言 / 断言偏弱 / 实现被替换 / 未断言 / 无法判定。判档按产出对账：先看分支的产出项与决定性，再看断言读到哪一项；拿不准按「疑罪从无」维持已断言，缺证据才落无法判定。

分片（体量大时）：

- 生产改动行 > 800 → 步骤 2 派**枚举片**（按生产文件簇切，每片 ≤ 500–600 行）。
- 逻辑点 > 50 → 步骤 3 派**裁定片**（每 50 点一批，只判状态与证据，不读 diff）。
- 调度方式由运行的 agent 决定；协议只约束切片标准、每片的输入与输出契约。见 [references/分片协议.md](references/分片协议.md)。

## 目录

```
dt-audit/
├── SKILL.md                     # 入口：边界、五步流程、证据表结构、阈值索引
├── README.md                    # 本文件
├── references/
│   ├── 逻辑点规则.md             # 逻辑点计入 / 不计入、编号、枚举冻结
│   ├── 断言与Mock判据.md         # 产出对账、三档、Mock 七类、不判的情形
│   ├── 问题类型.md               # 四类问题、子类、严重度升级、测试退化六信号
│   ├── 范围规则.md               # 哪些文件纳入审计、测试文件与生成代码识别
│   ├── 测试框架识别.md           # 用例 / 断言 / Mock 的识别口径
│   ├── 分片协议.md               # 枚举片与裁定片的切片、契约、合并规则
│   └── 报告模板.md               # 报告骨架 + 3.4 附录（render 读取）
└── scripts/
    ├── run.py                   # 命令行入口
    ├── README.md                # 脚本用法、错误契约、报告口径
    └── dt_audit/
        ├── index.py / mr.py / fetch.py   # 取数
        ├── scope.py             # 范围收敛
        ├── scan.py              # 退化候选扫描
        ├── assemble.py          # 证据表骨架 / 分片合并 / 裁定回填
        ├── stats.py             # 指标、定性与校验
        └── render.py            # 报告装配与回读校验
```

## 已知限制

- **不运行测试**：用例当前是否通过未知；红色用例与绿色用例在证据上一视同仁。
- **只覆盖实现自证能力**：漏实现、设计缺陷不在检测范围内。
- **C / C++ 只到文件级**：不枚举逻辑点，不进任何比率，只记受限文件数。
- **仅 diff 档有盲区**：看不到未改动的测试与调用方，相关证据缺口会单独列出。

## 维护

- **判据只改一处**：[SKILL.md](SKILL.md) 的状态定义、[references/断言与Mock判据.md](references/断言与Mock判据.md) 的对账规则、[references/问题类型.md](references/问题类型.md) 的分类与升级条件。报告 3.4 是它们的**读者副本**，允许保留一份定义与数字，改源文件时同步。
- **阈值以代码为准**：`scripts/dt_audit/stats.py` 的常量是唯一权威，`stats.json` 的 `水位` 块会原样输出；口径说明在 [scripts/README.md](scripts/README.md) 的「报告口径」。
- **改证据表结构**：同步改 `stats.py` 的 `REQUIRED_SECTIONS`、`SKILL.md` 的字段表与 `render.py` 的装配逻辑，然后跑一次 `stats` + `render --check`。
- **改报告骨架**：模板在 [references/报告模板.md](references/报告模板.md)，改完必须 `render --check` 通过。
