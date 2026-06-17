---
name: welink-msg-query
description: 查询 WeLink 群聊或私聊消息，支持按时间、发送人、类型、关键字/正则等条件过滤。当用户要求搜索、查找、过滤 WeLink 消息时使用此 skill。
---

# WeLink 消息查询

通过 `welink-cli` 查询 WeLink 群聊或私聊消息，自动分页，支持丰富的过滤条件。封装脚本位于 `~/project/transfer/welink-cli-msg-script/welink_msg_filter.py`，Agent 只需调用它并解析 JSON 输出即可。

## 适用场景

用户需要从 WeLink 群聊或私聊中搜索、过滤消息。触发条件包括：

- "查一下 XXX 群最近的消息"
- "找 z00000003 发的所有文件消息"
- "搜索包含 '周报' 的消息"
- "查 6月1号到6月15号之间的聊天记录"
- 任何涉及 `welink-cli im query-history-message` 的需求

## 命令速查

```bash
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id <id> | --user-account <acct> \
  [--sender <acct>] [--receiver <acct>] \
  [--start-time <ts>] [--end-time <ts>] \
  [--content-type <type>] [--keyword <kw>] [--regex] \
  [--max-count <n>] [--page-size <n>] [--message-id <id>] \
  [--verbose]
```

## 参数说明

### 目标（互斥，必选其一）
| 参数 | 说明 |
|------|------|
| `--group-id <id>` | 群聊 ID，纯数字字符串（如 `943609380077752540`） |
| `--user-account <acct>` | 用户帐号，`a` + 数字格式（如 `a12345678`） |

### 分页控制
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--message-id <id>` | `0` | 起始游标。`0` = 从最新消息开始。返回的消息 msgId **严格小于**此值 |
| `--page-size <n>` | `100` | 每次 API 调用拉取的消息数（1–100）。值越小翻页越多，但在中途出错时能保留更细粒度的部分结果 |

### 过滤条件
| 参数 | 说明 |
|------|------|
| `--start-time <ts>` | 最早消息时间（含）。支持 Unix 毫秒、Unix 秒、`"YYYY-MM-DD HH:MM:SS"` |
| `--end-time <ts>` | 最晚消息时间（含）。格式同上 |
| `--sender <acct>` | 只返回此帐号发送的消息 |
| `--receiver <acct>` | 只返回发给此帐号的消息（仅私聊场景） |
| `--content-type <type>` | 消息类型，可选：`TEXT_MSG`、`IMAGESPAN_MSG`、`PICTURE_MSG`、`CARD_MSG`、`FILE_MSG` |
| `--keyword <kw>` | 内容关键字，大小写不敏感，子串匹配 |
| `--regex` | 将 `--keyword` 视为 Python 正则表达式 |
| `--max-count <n>` | 最多返回 N 条匹配消息。`0` = 不限制 |

### 其他
| 参数 | 说明 |
|------|------|
| `--verbose` / `-v` | 翻页进度输出到 stderr（不影响 stdout 的 JSON） |

## 输出格式

stdout 始终输出 JSON。退出码：`0` = 成功，`1` = 参数错误，`2` = 运行错误。

### 成功响应
```json
{
  "success": true,
  "error": null,
  "error_type": null,
  "summary": {
    "target_type": "group",
    "target_id": "943609380077752540",
    "filters": {
      "start_time": 1718496000000,
      "end_time": null,
      "sender": "z00000003",
      "receiver": null,
      "content_type": null,
      "keyword": null
    },
    "matched_count": 8,
    "total_fetched": 25,
    "total_pages": 1,
    "truncated": false
  },
  "messages": [
    {
      "msgId": 89080138787310823,
      "serverSendTime": 1718841600000,
      "sender": "z00000003",
      "receiver": "",
      "contentType": "FILE_MSG",
      "groupId": 943609380077752540,
      "content": "[文件] Q2项目总结报告_v3.pdf",
      "at": true,
      "atAccountList": ["z00000001"]
    }
  ],
  "partial": false
}
```

### 错误 / 部分结果响应
```json
{
  "success": false,
  "error": "API returned resultCode=1, context=Internal Server Error",
  "error_type": "api_error",
  "summary": { "...": "..." },
  "messages": [ /* 出错前已收集的消息 */ ],
  "partial": true
}
```

### 关键字段含义

| 字段 | 含义 |
|------|------|
| `success` | `true` = 完整完成；`false` = 发生了错误 |
| `partial` | `true` = 发生了错误但已收集到部分消息 |
| `error_type` | 错误类型：`auth_error`、`api_error`、`network_error`、`parse_error`、`arg_error`、`unknown` |
| `summary.truncated` | `true` = 提前终止（达到 max-count 或出错），可能还有未拉取的消息 |
| `summary.matched_count` | 通过所有过滤条件的消息数量 |
| `summary.total_fetched` | 从 API 拉取的总消息数（所有页合计） |
| `summary.total_pages` | 共发起了多少次 API 调用 |

## 工作原理

1. 调用 `welink-cli im query-history-message`，以 `--message-id` 作为游标
2. 消息按**从新到旧**排序返回。每条消息的 `serverSendTime` 是 Unix 毫秒时间戳
3. 逐条匹配过滤条件。当某条消息的时间戳**早于 `--start-time`** 时，立即停止翻页——后续消息只会更早
4. 每批消息中最小的 `msgId` 作为下一页的游标
5. 停止条件（满足任一）：达到 max-count、超出时间范围、API 返回空、发生错误
6. 遇到 `401` 错误时，自动执行 `welink-cli auth login` 并重试一次

## 常用示例

### 查某人最近的消息
```bash
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id 943609380077752540 --sender z00000003 --max-count 20
```
→ 返回 z00000003 最近的 20 条消息。

### 关键字搜索
```bash
# 子串匹配（大小写不敏感）
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id 943609380077752540 --keyword "周报"

# 正则匹配
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id 943609380077752540 --keyword "\\d+个(Feature|Bug)" --regex
```

### 时间范围查询
```bash
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id 943609380077752540 \
  --start-time "2024-06-01" --end-time "2024-06-15"
```
时间支持 Unix 毫秒、Unix 秒、日期字符串三种格式。

### 查找群内所有文件
```bash
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id 943609380077752540 --content-type FILE_MSG
```

### 组合过滤 + 观察翻页过程
```bash
python3 ~/project/transfer/welink-cli-msg-script/welink_msg_filter.py \
  --group-id 943609380077752540 --sender z00000003 --keyword "紧急" -v
```

## 错误处理指引

收到响应后的处理流程：

1. **先检查 `success`。** 如果 `false`，读取 `error` 和 `error_type`
2. **`partial=true` 表示有可用数据。** `messages` 数组中保存了出错前已收集的消息。向用户展示这些结果，同时说明发生了错误
3. **`error_type=auth_error`：** 脚本已自动尝试重鉴权。如果仍然失败，告知用户可能需要手动执行 `welink-cli auth login`
4. **`error_type=network_error` 或 `unknown`：** 建议重试，或检查网络 / VPN
5. **`error_type=api_error`：** API 返回了非零 resultCode。向用户报告具体错误信息
6. **`truncated=true` 且无错误：** 达到了 `--max-count` 上限。告知用户可能还有更多匹配消息

## 注意事项

- `--group-id` 和 `--user-account` 互斥，脚本会强制校验
- `--message-id` 是**排他性**游标：传 `--message-id 100` 返回的是 msgId **严格小于** 100 的消息
- 时间过滤：`--start-time` 是**最早**要包含的消息时间，`--end-time` 是**最晚**。因为消息从新到旧返回，当脚本遇到早于 `--start-time` 的消息时会立即停止翻页
- 默认 `--page-size` 是 100（API 上限）。只在需要测试分页或希望中途出错时保留更细粒度部分结果时才调小
- 正则使用 Python `re` 语法，自动附带 `re.IGNORECASE | re.DOTALL` 标志
- Mock CLI 位于 `~/project/transfer/welink-cli-msg-script/mock/welink-cli`，可用于测试。测试用例位于 `~/project/transfer/welink-cli-msg-script/tests/test_filter.py`（65 个用例，全部通过）

## 代码调用示例

```python
import json, subprocess

result = subprocess.run(
    ["python3", "~/project/transfer/welink-cli-msg-script/welink_msg_filter.py",
     "--group-id", "943609380077752540", "--sender", "z00000003", "--max-count", "10"],
    capture_output=True, text=True
)
data = json.loads(result.stdout)

if data["success"]:
    for msg in data["messages"]:
        print(f"[{msg['sender']}] {msg['content']}")
elif data["partial"]:
    print(f"部分结果（{data['summary']['matched_count']} 条）: {data['error']}")
else:
    print(f"错误: {data['error']}")
```
