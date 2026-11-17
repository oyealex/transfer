# 脚本

Skill 只依赖五个入口。`git` 只用来补读上下文，不参与取改动。

| 入口 | 什么时候用 |
|---|---|
| `fetch_sr_mr_index` | 检测对象是 SR / AR，用它拿关联的 MR 清单（只给元数据） |
| `fetch_mr_details` | 检测对象是一个 MR 地址，用它拿元数据 |
| `fetch_mr_changes` | 两种输入都走它：把 MR 的改动落成 `manifest.json` + 逐文件 diff |
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
    ├── index.py            # fetch_sr_mr_index —— 待实现
    ├── mr.py               # fetch_mr_details —— 待实现
    ├── fetch.py            # fetch_mr_changes —— 待实现
    ├── render.py           # render_report —— 已实现
    └── stats.py            # run_stats —— 已实现
```

三个取数实现当前抛 `NotImplementedError`，签名与实现要点写在各自的 docstring 里；`stats` 与 `render` 是纯本地处理，已可用。

`render` 只做三件事：标量替换、行块展开、静态段注入。它不做任何判断——选哪几条问题、影响档、措辞都来自 `narrative.json`；也不做统计——数字全部来自 `stats.json`。模板语法见 `render.py` 的模块 docstring。

## 公开 API

```python
from dt_audit import fetch_sr_mr_index, fetch_mr_details, fetch_mr_changes, run_stats

index = fetch_sr_mr_index("SR-1234")        # 或 "AR-77"
mrs   = fetch_mr_details(["https://git.corp/group/repo/-/merge_requests/1234"])
man   = fetch_mr_changes(["https://…/-/merge_requests/1234"], ".dt-audit/SR-1234/_diffs")
stats = run_stats(evidence=Path(".dt-audit/SR-1234/evidence.md"))
```

只有这五个入口。不要增加配置加载、仓库检索、diff 计算之类的出口——本地仓路径由 Skill 询问用户后自行校验，只用于补读上下文；改动内容一律由 `fetch` 产出。

## 运行

```bash
python run.py index --id SR-1234 --pretty
python run.py index --id AR-77 --pretty
python run.py mr    --url https://git.corp/group/repo/-/merge_requests/1234 --pretty
python run.py fetch --url https://…/-/merge_requests/1234 --out .dt-audit/SR-1234/_diffs --pretty
python run.py stats --evidence .dt-audit/SR-1234/evidence.md --pretty
python run.py render --stats .dt-audit/SR-1234/stats.json --evidence .dt-audit/SR-1234/evidence.md --manifest .dt-audit/SR-1234/_diffs/manifest.json --narrative .dt-audit/SR-1234/narrative.json --out .dt-audit/SR-1234/DT-AUDIT-REPORT.md --check
```

`--url` 可重复，一次传完整批地址。`stats` 默认把 `stats.json` 写在证据表同目录，用 `--out` 覆盖。

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

`stats` 的返回结构：指标与定性在顶层 `指标`、`定性` 两个块里，问题分布取 `分布表`（已排序、只含命中项），逐类明细在 `问题分布`；证据缺口在 `证据缺口`（含 `无法获取单元数`），测试退化条数在 `测试退化`（`计入条数` / `提示条数`）。字段含义见 Skill 的 `references/报告结构.md` §二。

## 注意

- **不要**在取数脚本里判断测试文件、生成代码、Mock、断言档位。这些判据只在 Skill 里有一份，脚本再做一遍必然打架。
- **不要**为了让 diff 上下文更长去拉两端文件重算——那会让兜底路径依赖完整文件，正是它拿不到的东西。平台给多少上下文就落多少。
- **不要**把 diff 塞进 stdout。一个 MR 的 diff 动辄几十 KB，塞 JSON 会撑爆上下文；写文件，Skill 需要哪份读哪份。
- `stats` 只解析固定表头、做算术、查结构矛盾，**不下沉任何判断**：状态、断言档位、Mock 类别都已由 Skill 写进证据表。
- **不要**在脚本里做网络重试以外的业务逻辑，保持无状态。
- 输出必须幂等：同一输入两次调用，JSON 字节级一致（`fetched_at` 除外）。
