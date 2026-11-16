# 取数脚本

Skill 只依赖三个方法。其余全部由 Skill（Agent）自己用 `git` 完成。

| 方法 | 什么时候用 |
|---|---|
| `fetch_sr_mr_index` | 检测对象是 SR，用它拿关联的 MR 清单 |
| `fetch_mr_details` | 检测对象是一批 MR，按地址取详情 |
| `fetch_mr_change_detail` | 本地仓不可达时，取单个 MR 的文件级变更 |

检测对象是一批提交时，三个方法都不调用，Skill 直接用本地 `git`。

```
scripts/
├── run.py                  # 启动器
└── dt_audit/
    ├── __init__.py         # 导出三个方法
    ├── __main__.py         # python -m dt_audit
    ├── contracts.py        # 数据契约（唯一来源）
    ├── cli.py              # 参数解析与错误契约
    ├── index.py            # fetch_sr_mr_index —— 待实现
    ├── mr.py               # fetch_mr_details —— 待实现
    └── detail.py           # fetch_mr_change_detail —— 待实现
```

三个实现文件当前都抛 `NotImplementedError`。

## 公开 API

```python
from dt_audit import fetch_sr_mr_index, fetch_mr_details, fetch_mr_change_detail

index  = fetch_sr_mr_index("SR-1234")
mrs    = fetch_mr_details(["https://git.corp/group/repo/-/merge_requests/1234"])
detail = fetch_mr_change_detail("1234", project_path="group/subgroup/repo")
```

只有这三个方法。不要增加配置加载、仓库检索、diff 计算之类的出口——本地仓路径由 Skill 询问用户后自行校验，diff 由 Skill 用 `git` 取。

## 运行

```bash
python run.py index  --sr SR-1234 --pretty
python run.py mr     --url https://git.corp/group/repo/-/merge_requests/1234 --pretty
python run.py detail --mr 1234 --project group/subgroup/repo --context 10 --pretty
```

`--url` 可重复，一次传完整批地址。

或：

```bash
cd scripts && python -m dt_audit index --sr SR-1234 --pretty
```

要求 Python ≥ 3.8，只用标准库。

## 错误契约

| 退出码 | 含义 | 抛出 |
|---|---|---|
| 0 | 成功（`warnings` 可非空，属正常） | — |
| 1 | 未预期异常 | 任意异常 |
| 2 | 未找到（SR / MR / 仓） | `cli.NotFoundError` |
| 3 | 上游不可达或鉴权失败 | `cli.UpstreamError` |
| 64 | 命令行参数错误 | `cli._Parser.error` |

参数错误必须与「未找到」区分开：Skill 靠退出码判断是终止还是继续，混用会让它把一条敲错的命令读成「这个 SR 没有 MR」。

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

`mr`（结构与 `index` 相同，入口换成地址列表）：

```json
{
  "schema_version": "1.0",
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
      "linked_via": "MR",
      "base_sha": "a1b2c3d4e5f6...",
      "head_sha": "9f8e7d6c5b4a...",
      "commits": []
    }
  ],
  "warnings": ["https://git.corp/group/repo/-/merge_requests/9999 解析失败：未找到"]
}
```

`detail`：

```json
{
  "schema_version": "1.0",
  "mr_id": "1234",
  "base_sha": "a1b2c3d4e5f6...",
  "head_sha": "9f8e7d6c5b4a...",
  "files": [
    {
      "path": "src/main/java/com/x/OrderService.java",
      "old_path": null,
      "change_type": "modified",
      "is_binary": false,
      "is_submodule": false,
      "additions": 12,
      "deletions": 3,
      "truncated": false,
      "diff": "--- a/src/main/java/com/x/OrderService.java\n+++ b/...\n@@ -85,6 +85,9 @@ ..."
    }
  ],
  "warnings": []
}
```

## 实现清单

`fetch_sr_mr_index`

- [ ] 同时解析直接挂 SR 与挂 AR 的 MR，`linked_via` 标来源
- [ ] `base_sha` 能拿到就必须返回（决定证据来源是「本地精确」还是「本地推导」）
- [ ] `commits` 按拓扑序，带完整 message
- [ ] `mrs` 按 `mr_id` 排序
- [ ] 单 MR 失败写 `warnings`，不抛异常
- [ ] SR 不存在抛 `NotFoundError`，平台异常抛 `UpstreamError`

`fetch_mr_details`

- [ ] 支持 GitLab、GitHub、Gitee 的常见 MR 地址形态，解析出仓路径与 MR 编号
- [ ] 返回的 `MrRef` 与 `index` 完全同构，Skill 侧不做区分
- [ ] `base_sha` 能拿到就必须返回（决定证据来源是「本地精确」还是「本地推导」）
- [ ] `commits` 按拓扑序，带完整 message
- [ ] `mrs` 按 `mr_id` 排序
- [ ] 单条地址失败写 `warnings`，不抛异常；全部失败才抛 `NotFoundError`

`fetch_mr_change_detail`

- [ ] unified diff，上下文至少 10 行
- [ ] 二进制、子模块、超限文件正确处理标志位
- [ ] 重命名给 `old_path`
- [ ] `files` 按 `path` 排序
- [ ] 单文件失败写 `warnings`

## 注意

- **不要**在脚本里判断测试文件、生成代码、Mock、断言档位。这些判据只在 Skill 里有一份，脚本再做一遍必然打架。
- **不要**在脚本里做网络重试以外的业务逻辑，保持无状态。
- 输出必须幂等：同一输入两次调用，JSON 字节级一致（`fetched_at` 除外）。
