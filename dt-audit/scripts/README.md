# 脚本

Skill 只依赖三个入口。其余全部由 Skill（Agent）自己用 `git` 完成。

| 入口 | 什么时候用 |
|---|---|
| `fetch_sr_mr_index` | 检测对象是 SR，用它拿关联的 MR 清单 |
| `fetch_mr_details` | 检测对象是一批 MR，按地址取详情 |
| `run_stats` | 步骤 5 出报告前，解析 `evidence.md`、算指标、校验一致性 |

检测对象是一批提交时，前两个都不调用，Skill 直接用本地 `git`。

**详细修改内容只从本地代码仓取。** 本包不提供取 diff 的入口：没有本地仓就记「无法获取」，不走平台接口兜底。

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
    └── stats.py            # run_stats —— 已实现
```

两个取数实现当前抛 `NotImplementedError`；`stats` 是纯本地解析，已可用。

## 公开 API

```python
from dt_audit import fetch_sr_mr_index, fetch_mr_details, run_stats

index  = fetch_sr_mr_index("SR-1234")
mrs    = fetch_mr_details(["https://git.corp/group/repo/-/merge_requests/1234"])
stats  = run_stats(evidence=Path(".dt-audit/SR-1234/evidence.md"))
```

只有这三个入口。不要增加配置加载、仓库检索、diff 计算之类的出口——本地仓路径由 Skill 询问用户后自行校验，diff 由 Skill 用 `git` 取。

## 运行

```bash
python run.py index  --sr SR-1234 --pretty
python run.py mr     --url https://git.corp/group/repo/-/merge_requests/1234 --pretty
python run.py stats  --evidence .dt-audit/SR-1234/evidence.md --pretty
```

`--url` 可重复，一次传完整批地址。`stats` 默认把 `stats.json` 写在证据表同目录，用 `--out` 覆盖。

或：

```bash
cd scripts && python -m dt_audit index --sr SR-1234 --pretty
```

要求 Python ≥ 3.8，只用标准库。

## 错误契约

| 退出码 | 含义 | 抛出 |
|---|---|---|
| 0 | 成功（`warnings` 可非空，属正常） | — |
| 1 | 未预期异常，或证据表校验不通过 | 任意异常 / `stats.EvidenceError` |
| 2 | 未找到（SR / MR / 仓） | `cli.NotFoundError` |
| 3 | 上游不可达或鉴权失败 | `cli.UpstreamError` |
| 64 | 命令行参数错误 | `cli._Parser.error` |

参数错误必须与「未找到」区分开：Skill 靠退出码判断是终止还是继续，混用会让它把一条敲错的命令读成「这个 SR 没有 MR」。

`stats` 校验不通过时，`stats.json` 仍然写出（带错误清单），但退出码为 1，Skill 必须修证据表后重跑，不得手改报告数字。

stdout 只允许 JSON。日志、进度、警告文本一律写 stderr。Skill 直接解析 stdout，混入任何非 JSON 内容都会解析失败。

## 输出样例

`index`：

```json
{
  "schema_version": "1.0",
  "sr_id": "SR-1234",
  "fetched_at": "2026-11-16T10:00:00+08:00",
  "mrs": [
    {
      "mr_id": "1234",
      "title": "订单校验逻辑",
      "web_url": "https://git.corp/group/repo/-/merge_requests/1234",
      "repo": {
        "project_path": "group/repo",
        "web_url": "https://git.corp/group/repo"
      },
      "target_branch": "master",
      "state": "merged",
      "linked_via": "AR-77",
      "base_sha": "a1b2c3d4e5f6...",
      "head_sha": "9f8e7d6c5b4a...",
      "commits": [
        {
          "sha": "9f8e7d6c5b4a...",
          "short_sha": "9f8e7d6",
          "message": "fix: 空列表分支返回空集合\n\n补充边界处理",
          "committed_at": "2026-11-10T14:22:33+08:00",
          "author_name": "zhangsan",
          "parents": ["a1b2c3d4e5f6..."]
        }
      ]
    }
  ],
  "warnings": []
}
```

`mr` 的返回结构见 `contracts.py` 的 `MrDetailBatch`，不另给样例。

`stats` 的返回结构：

```json
{
  "schema_version": "1.0",
  "对象": {"对象类型": "SR", "对象标识": "SR-1234", "变更单元数": "3"},
  "指标": {
    "变更生产代码行数": 788,
    "变更生产代码千行": 0.788,
    "新增DT数": 35,
    "新增DT密度": {"value": 44.4, "text": "44.4 个/千行"},
    "全部决策点": 63,
    "可判定决策点数": 50,
    "观测率": {"ratio": 0.5, "text": "50.0%"},
    "判别性断言占比": {"ratio": 0.786, "text": "78.6%"},
    "架空率": {"ratio": 0.08, "text": "8.0%"},
    "证据完整度": {"ratio": 0.7937, "text": "79.4%", "档": "部分", "低于下限": false}
  },
  "状态分布": {"已观测": 16, "部分观测": 9, "实现被替换": 4, "未观测": 11, "无测试": 10, "无法判定": 13},
  "问题分布": [
    {"编号": "A1", "类型": "无测试", "数量": 10, "单位": "个决策点",
     "占同类总数": "20.0%", "基础严重度": "严重", "严重度": "严重",
     "升级理由": null, "命中": true}
  ],
  "证据": {"ratio": 0.7937, "text": "79.4%", "档": "部分", "低于下限": false},
  "水位": {"证据完整度下限": 0.6, "观测率良好": 0.7, "覆盖低水位": 0.4},
  "校验": {"通过": true, "错误": [], "警告": []}
}
```

## 注意

- **不要**在取数脚本里判断测试文件、生成代码、Mock、断言档位。这些判据只在 Skill 里有一份，脚本再做一遍必然打架。
- `stats` 只解析固定表头、做算术、查结构矛盾，**不下沉任何判断**：观测状态、断言档位、Mock 类别都已由 Skill 写进证据表。
- **不要**在脚本里做网络重试以外的业务逻辑，保持无状态。
- 输出必须幂等：同一输入两次调用，JSON 字节级一致（`fetched_at` 除外）。
