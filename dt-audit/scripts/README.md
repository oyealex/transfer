# 脚本

Skill 只依赖八个入口，全部是确定性变换：取数、收敛、扫描、装配、统计、渲染。`git` 只用来补读上下文，不参与取改动。

| 入口 | 做什么 | 不做 |
|---|---|---|
| `index` | 按 SR / AR 编号查关联 MR 清单 | 不取 diff |
| `mr` | 按 MR 地址查元数据 | 不取 diff |
| `fetch` | 批量取改动，manifest 与 diff 落盘 | 不判范围 |
| `scope` | 分类变更文件，算体量指标 | 不读内容特征 |
| `scan` | 扫测试 diff，给方向原因的线索 | 不判是否构成方向原因 |
| `assemble` | 生成证据表骨架、合并分片、回填裁定 | 不做判断 |
| `stats` | 算占比、派生用例名单、定性并校验 | 不改证据表 |
| `render` | 装配报告并回读校验 | 不写措辞 |

三个取数函数（`fetch_sr_mr_index` / `fetch_mr_details` / `fetch_mr_changes`）由使用方实现，签名固定，其余模块不依赖其内部实现。

## 公开 API

```python
fetch_sr_mr_index(object_id: str) -> SrMrIndex
fetch_mr_details(mr_urls: Sequence[str]) -> MrDetailBatch
fetch_mr_changes(mr_urls: Sequence[str], out_dir: str | Path) -> MrChangeManifest

analyze_scope(manifest_path: Path) -> dict     # run_scope 的内核
scan_manifest(manifest_path: Path) -> dict     # run_scan 的内核

assemble_init(*, evidence, manifest_path, scope_path=None, index_path=None,
              meta_path=None, object_type="", object_id="", tier="仅 diff")
assemble_merge(*, evidence, shards)
assemble_judge(*, evidence, judges)
run_assemble(*, mode, evidence, manifest=None, scope=None, index=None, meta=None,
             object_type="", object_id="", tier="仅 diff", shards=(), judges=())

analyze(path: Path) -> dict                    # run_stats 的内核
load_evidence(path: Path) -> dict              # 只解析，不校验
run_stats(*, evidence, out=None)

render_report(*, template, stats_path, evidence_path, narrative_path) -> (str, dict, list)
run_render(*, template, stats, evidence, narrative, out=None, check=False)
```

## 运行

```bash
# 步骤 1：取数
python run.py index --id SR1234 --pretty
python run.py mr --url https://git/.../merge_requests/1 --pretty
python run.py fetch --url <地址> --out .dt-audit/SR1234/_diffs --pretty

# 步骤 1：范围收敛 + 证据表骨架
python run.py scope --manifest .dt-audit/SR1234/_diffs/manifest.json --out .dt-audit/SR1234/scope.json
python run.py assemble --mode init --evidence .dt-audit/SR1234/evidence.md \
  --manifest .dt-audit/SR1234/_diffs/manifest.json --scope .dt-audit/SR1234/scope.json \
  --object-type SR --object-id SR1234

# 步骤 2：退化候选 + 合并枚举片
python run.py scan --manifest .dt-audit/SR1234/_diffs/manifest.json --out .dt-audit/SR1234/signals.json
python run.py assemble --mode merge --evidence .dt-audit/SR1234/evidence.md --shards .dt-audit/SR1234/shard-1.json

# 步骤 3：回填裁定片
python run.py assemble --mode judge --evidence .dt-audit/SR1234/evidence.md --judges .dt-audit/SR1234/judge-1.json

# 步骤 5：算数与装配
python run.py stats --evidence .dt-audit/SR1234/evidence.md --out .dt-audit/SR1234/stats.json
python run.py render --stats .dt-audit/SR1234/stats.json --evidence .dt-audit/SR1234/evidence.md \
  --narrative .dt-audit/SR1234/narrative.json --out .dt-audit/SR1234/DT-AUDIT-REPORT.md --check
```

`--url` 可重复，一次传完整批地址。`stats` 默认把 `stats.json` 写在证据表同目录，用 `--out` 覆盖。

`assemble` 的三种模式：

- `init`：从 manifest / scope / index 生成 `evidence.md` 骨架（五段）。没有 `--index` 时单元的标题 / 作者留空，由 Skill 补；`--meta` 可直接给 `{object_type, object_id, units:[…]}`。缺 `--object-id` 时 `warnings` 会提示，因为 `stats` 会直接报「缺少必需键 对象标识」。
- `merge`：合并枚举片 JSON，按「位置 + 条件摘要」去重、统一编号，并用片里的候选用例填 `覆盖用例`。
- `judge`：回填 `覆盖用例 / 问题 / 原因 / 说明`。

`scope` / `scan` / `assemble` 的返回都是对同一份输入的确定性变换，不含判断；判不准的内容留给 Skill 复核。

## 错误契约

| 码 | 含义 |
|---|---|
| 0 | 成功（`warnings` 可非空，属正常） |
| 1 | 校验失败或内部异常（`EvidenceError` / `RenderError` / 其它） |
| 2 | 对象不存在或地址无法解析 |
| 3 | 平台不可达 |
| 64 | 命令行参数错误 |

`stats` / `render` 失败时仍会把 `stats.json` / 报告写出来（`render` 加了 `--check` 时，校验不过不写文件），便于定位。

## 报告口径

报告骨架与 3.4 附录在 `references/报告模板.md`；本节讲每个数字怎么算、阈值是多少。数值以 `stats.py` 的常量为准，`stats.json` 的 `水位` 块会原样输出，报告 3.4.3 是读者副本。

### 指标

| 指标 | 算法 |
|---|---|
| 全部逻辑点 | 「逻辑点证据」段的行数 |
| 有效 / 未覆盖 / 无效 / 低价值 / 不确定 | 按「问题」列计数，五者之和 = 全部逻辑点 |
| 各类占比 | 该类计数 ÷ 全部逻辑点，保留一位小数 |
| 新增 DT / 修改 DT / 既有用例 | 用例清单按「变更类型」计数 |
| 变更生产代码行数 | 范围表里「分类=生产代码且语言为 Java / Python」的新增行之和 |
| 变更生产代码 KLOC | 上一个数 ÷ 1000；**报告只列这一行**——KLOC 与行数是同一个数，只是单位不同 |
| 新增 DT 密度 | 新增 DT ÷ 变更生产代码 KLOC；KLOC 为 0 时「不适用」 |
| 生产改动行 | 同范围的新增 + 删除，用于短路与分片，不进报告 |

分母一律是全部逻辑点，五个占比用最大余额法保留一位小数，合计恰为 100.0%。

### 定性（1.2 第一行）

按序判定，命中即止：

| 序 | 取值 | 条件 |
|---|---|---|
| 0 | 证据不足（前置） | 全部变更单元都是「无法获取」 |
| 1 | 合格 | 逻辑点 = 0 |
| 2 | 证据不足 | 不确定 > 40% |
| 3 | 待改进 | 未覆盖 ≥ 30%，或无效 ≥ 30%，或有效 < 50% |
| 4 | 优秀 | 有效 ≥ 75%、未覆盖 = 0、无效 = 0、不确定 ≤ 10%，且逻辑点 ≥ 8 |
| 5 | 合格 | 其余 |

阈值常量：`UNCERTAIN_BAD = 0.40`、`COVERAGE_OR_INVALID_BAD = 0.30`、`EFFECTIVE_BAD = 0.50`、`EFFECTIVE_GOOD = 0.75`、`UNCERTAIN_GOOD = 0.10`、`EXCELLENT_MIN_POINTS = 8`。

### 零值的唯一口径

- 逻辑点 = 0：定性「合格」；分布表不出；2.1 / 2.2 总评写「无行为分支，不评估」；1.2 写「本次变更没有产生逻辑点」。存在 C / C++ 受限文件时必须同时写受限文件数，**不得**写「本次改动未引入行为分支」。
- 逻辑点 > 0 但全部「不确定」：分布表照出，占比 100%，定性「证据不足」，2.x 总评写「全部判不清，不评估」。

### 章节与排序

1.1 基本信息 → 1.2 结论（定性、分布表、数据行、一句话判断）→ 1.3 主要缺口 → 2.1 逻辑点问题 → 2.2 用例名单 → 3.1–3.4 附录。

分布表固定顺序：未覆盖、无效、低价值、不确定、有效。1.3 按「文件」聚合未覆盖 + 无效 + 低价值点数，降序取前三，由 `narrative.json` 的 `top3` 点名文件与建议。

## 注意

- 证据表的段名、表头、列顺序固定，`stats` 靠表头定位；改一个列名就会解析失败。
- 单元格里的 `|` 必须写成 `\|`，`stats` 解析时会还原。
- 报告是唯一交付物，`render --check` 会拦中间产物文件名、脚本命令与 skill 文档这类读者拿不到的引用。
- 同一对象重复运行覆盖同名文件，不新建时间戳副本。
