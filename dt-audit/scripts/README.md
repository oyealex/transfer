# 脚本

Skill 只依赖四个入口。其余全部由 Skill（Agent）自己用 `git` 完成。

| 入口 | 什么时候用 |
|---|---|
| `fetch_sr_mr_index` | 检测对象是 SR / AR，用它拿关联的 MR 清单（只给元数据） |
| `fetch_mr_details` | 检测对象是一个 MR 地址，用它拿元数据 |
| `fetch_mr_changes` | 仓不可达时，用它把 MR 的修改内容落到临时目录 |
| `run_stats` | 步骤 5 出报告前，解析 `evidence.md`、算指标、校验一致性 |

**变更单元恒为 MR。** 没有提交批次这一路。

仓可达时改动一律用 `git` 取，不调 `fetch`：`git diff <基准> <头>`，基准是首个提交的第一父提交（首个提交是合并提交时用 `git merge-base`）。只有仓不可达才调 `fetch`。

```
scripts/
├── run.py                  # 启动器
└── dt_audit/
    ├── __init__.py         # 对外导出
    ├── __main__.py         # python -m dt_audit
    ├── contracts.py        # 数据契约（唯一来源）
    ├── cli.py              # 参数解析与错误契约
    ├── index.py            # fetch_sr_mr_index —— 待实现
    ├── mr.py               # fetch_mr_details —— 待实现
    ├── fetch.py            # fetch_mr_changes —— 待实现
    └── stats.py            # run_stats —— 已实现
```

三个取数实现当前抛 `NotImplementedError`，签名与实现要点写在各自的 docstring 里；`stats` 是纯本地解析，已可用。

## 公开 API

```python
from dt_audit import fetch_sr_mr_index, fetch_mr_details, fetch_mr_changes, run_stats

index = fetch_sr_mr_index("SR-1234")        # 或 "AR-77"
mrs   = fetch_mr_details(["https://git.corp/group/repo/-/merge_requests/1234"])
man   = fetch_mr_changes(["https://…/-/merge_requests/1234"], ".dt-audit/SR-1234/_diffs")
stats = run_stats(evidence=Path(".dt-audit/SR-1234/evidence.md"))
```

只有这四个入口。不要增加配置加载、仓库检索、diff 计算之类的出口——本地仓路径由 Skill 询问用户后自行校验，diff 由 Skill 用 `git` 取。

## 运行

```bash
python run.py index --id SR-1234 --pretty
python run.py index --id AR-77 --pretty
python run.py mr    --url https://git.corp/group/repo/-/merge_requests/1234 --pretty
python run.py fetch --url https://…/-/merge_requests/1234 --out .dt-audit/SR-1234/_diffs --pretty
python run.py stats --evidence .dt-audit/SR-1234/evidence.md --pretty
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
| 2 | 未找到（SR / AR / MR / 仓） | `cli.NotFoundError` |
| 3 | 上游不可达或鉴权失败 | `cli.UpstreamError` |
| 64 | 命令行参数错误 | `cli._Parser.error` |

参数错误必须与「未找到」区分开：Skill 靠退出码判断是终止还是继续，混用会让它把一条敲错的命令读成「这个 SR 没有 MR」。

`stats` 校验不通过时，`stats.json` 仍然写出（带错误清单），但退出码为 1，Skill 必须修证据表后重跑，不得手改报告数字。

stdout 只允许 JSON。日志、进度、警告文本一律写 stderr。

## 输出样例

`index`（`mr` 同构）：

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

`stats` 的返回结构：指标与定性在顶层 `指标`、`定性` 两个块里，问题分布取 `分布表`（已排序、只含命中项），逐类明细在 `问题分布`。字段含义见 Skill 的 `references/报告结构.md` §二。

## 注意

- **不要**在取数脚本里判断测试文件、生成代码、Mock、断言档位。这些判据只在 Skill 里有一份，脚本再做一遍必然打架。
- **不要**为了让 diff 上下文更长去拉两端文件重算——那会让兜底路径依赖完整文件，正是它拿不到的东西。平台给多少上下文就落多少。
- **不要**把 diff 塞进 stdout。一个 MR 的 diff 动辄几十 KB，塞 JSON 会撑爆上下文；写文件，Skill 需要哪份读哪份。
- `stats` 只解析固定表头、做算术、查结构矛盾，**不下沉任何判断**：观测状态、断言档位、Mock 类别都已由 Skill 写进证据表。
- **不要**在脚本里做网络重试以外的业务逻辑，保持无状态。
- 输出必须幂等：同一输入两次调用，JSON 字节级一致（`fetched_at` 除外）。
