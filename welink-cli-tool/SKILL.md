---
name: welink-cli-tool
description: WeLink CLI 综合封装工具，提供消息查询（过滤+翻页）、群组搜索、联系人搜索三种能力。当用户需要在 WeLink 中搜索消息、群组或联系人时使用此 skill。
category: productivity
---

# WeLink CLI 工具

封装脚本为本 SKILL.md 同目录下的 `welink_msg_filter.py`，对 `welink-cli` 的三个核心能力进行了封装增强：

| 子命令 | 封装内容 | 底层命令 |
|--------|----------|----------|
| `query-messages`（`msg`） | 多条件过滤 + 游标自动翻页 + 401 重鉴权 | `welink-cli im query-history-message` |
| `search-group` | 关键字搜索 + 手动分页 | `welink-cli search group` |
| `search-person` | 关键字搜索 + 手动分页 | `welink-cli search person` |

> **路径约定**：本文中 `./` 均相对于本 SKILL.md 所在目录。所有子命令共享 `--cli-path`（默认 `welink-cli`）和 `--verbose`（进度输出到 stderr）。

---

# 一、query-messages — 消息查询

查询群聊或私聊的历史消息，支持按时间、发送人、接收人、消息类型、关键字（含正则）过滤。

## 命令

```bash
python3 ./welink_msg_filter.py query-messages \
  --group-id <id> | --user-account <acct> \
  [--sender <acct>] [--receiver <acct>] \
  [--start-time <ts>] [--end-time <ts>] \
  [--content-type <type>] [--keyword <kw>] [--regex] \
  [--max-count <n>] [--page-size <n>] [--message-id <id>]
```

## 参数

### 目标（互斥，必选其一）
| 参数 | 说明 |
|------|------|
| `--group-id <id>` | 群聊 ID，纯数字字符串 |
| `--user-account <acct>` | 用户帐号，`a` + 数字格式 |

### 分页
| 参数 | 默认 | 说明 |
|------|------|------|
| `--message-id <id>` | `0` | 起始游标（`0`=最新），返回 msgId **严格小于**此值 |
| `--page-size <n>` | `100` | 每页条数（1–100） |

### 过滤
| 参数 | 说明 |
|------|------|
| `--start-time <ts>` | 最早时间（含），Unix ms/s 或 `"YYYY-MM-DD HH:MM:SS"` |
| `--end-time <ts>` | 最晚时间（含），格式同上 |
| `--sender <acct>` | 按发送人过滤 |
| `--receiver <acct>` | 按接收人过滤（私聊） |
| `--content-type <type>` | `TEXT_MSG` / `IMAGESPAN_MSG` / `PICTURE_MSG` / `CARD_MSG` / `FILE_MSG` |
| `--keyword <kw>` | 内容关键字，大小写不敏感 |
| `--regex` | `--keyword` 使用 Python 正则 |
| `--max-count <n>` | 最多返回 N 条，`0`=不限 |

## 翻页原理

底层 API 以 `message-id` 为游标，返回比它更旧的消息（不包含该 ID）。脚本自动用每批最小 `msgId` 推进游标，直到：达到 `--max-count`、超出时间范围、API 返回空、或出错。消息**从新到旧**排序。

## 输出

```json
{
  "success": true,
  "summary": {
    "target_type": "group", "target_id": "...",
    "filters": { "start_time": null, "sender": "z00000003", ... },
    "matched_count": 8, "total_fetched": 25, "total_pages": 1, "truncated": false
  },
  "messages": [
    { "msgId": ..., "serverSendTime": ..., "sender": ..., "content": ..., "contentType": ... }
  ],
  "partial": false
}
```

## 示例

```bash
# 查某人最近消息
python3 ./welink_msg_filter.py query-messages --group-id 943609380077752540 --sender z00000003 --max-count 20

# 关键字
python3 ./welink_msg_filter.py query-messages --group-id 943609380077752540 --keyword "周报"

# 正则
python3 ./welink_msg_filter.py query-messages --group-id 943609380077752540 --keyword "\\d+个(Feature|Bug)" --regex

# 时间范围
python3 ./welink_msg_filter.py query-messages --group-id 943609380077752540 --start-time "2024-06-01" --end-time "2024-06-15"

# 文件消息
python3 ./welink_msg_filter.py query-messages --group-id 943609380077752540 --content-type FILE_MSG
```

## ID 解析流程

用户通常不会直接提供群聊 ID 或用户帐号，而是用自然语言描述目标（如"查一下 AI 突击队的消息"）。此时按以下流程处理：

### 1. 判断目标类型

从用户描述中推断目标是群组还是联系人：

| 用户表述 | 推断类型 |
|----------|----------|
| "XX 群的消息"、"XX 群聊"、群名称 | 群组 |
| "XX 发的消息"、"XX 的聊天记录"、人名 | 联系人 |

### 2. 搜索候选

- **群组** → `search-group --keyword <提取的关键字>`
- **联系人** → `search-person --keyword <提取的关键字>`

### 3. 匹配结果处理

向用户展示候选项，让其选择。Agent 的交互工具（如 Hermes 的 `clarify`）通常限制每次 4 个显式选项，按以下策略展示：

**≤ 4 条结果**：直接全部列出，每条一行关键信息。1 条时可自动使用并告知用户。

**> 4 条结果**：每轮展示 3 个候选 + 翻页选项：

```
选项 1–3：前 3 条候选项
选项 4：  "下一页（还有 N 条）"
选项 5（自由输入）：用户输入更精确的描述
```

用户点"下一页"时展示下一批 3 条，最后一页不足 3 条则展示剩余全部并去掉翻页选项。用户输入更精确描述时，用新关键字重新搜索。

每条候选的展示格式：

| 类型 | 格式 |
|------|------|
| 群组 | `groupName（成员数 人）` |
| 联系人 | `chineseName — w3account — deptName` |

### 4. 执行查询

用户选择后用对应的 `groupId` 或 `w3account` 执行 `query-messages`。

### 示例对话

```
用户：查一下 AI 突击队最近的消息

Agent：
  1. search-group --keyword "AI突击" → 返回 1 条，groupId=1000000000000000001
  2. "找到群组「AI研发突击队」(29人)，正在查询消息..."
  3. query-messages --group-id 1000000000000000001 --max-count 20
```

```
用户：张三最近说了什么

Agent：
  1. search-person --keyword "张三" → 返回 5 条
  2. 向用户展示选择：

     找到 5 个匹配"张三"的联系人，请选择：
       1. 张三 — z00000001 — AI训练组
       2. 张三丰 — z00000015 — 基础架构部
       3. 张三石 — a12345678 — 测试部
       4. 下一页（还有 2 条）

  3. 用户点"下一页"，展示第二轮：

     继续浏览：
       1. 张三千 — z00000099 — 安全部
       2. 张三木 — z00000150 — 运维部
       
     （最后一页不足 3 条，无翻页选项，用户可通过自由输入重搜）

  4. 用户输入"AI训练组的张三"
  5. 重新 search-person --keyword "AI训练组的张三" 或直接匹配到第一条
  6. query-messages --user-account z00000001 --sender z00000001 --max-count 20
```

> **注意**：查联系人消息时，如果目的是"查此人发的消息"，需同时加 `--sender` 过滤，因为 `query-messages --user-account` 查的是私聊对话，不是你与该联系人在所有群里的发言。

---

# 二、search-group — 搜索群组

按名称关键字搜索群组。

## 命令

```bash
python3 ./welink_msg_filter.py search-group --keyword <name> [--page-size 20] [--page 1]
```

## 参数

| 参数 | 默认 | 说明 |
|------|------|------|
| `--keyword` | **必填** | 群组名称关键字 |
| `--page-size` | `20` | 每页条数（1–100） |
| `--page` | `1` | 页码 |

## 分页原理

底层不支持翻页，脚本查询 `page × page_size` 条结果后切片。如需第 2 页（每页 20）→ 实际查 40 条，返回第 21–40 条。

## 输出

```json
{
  "success": true,
  "summary": { "keyword": "AI", "page": 1, "page_size": 20, "total_matches": 15, "returned_count": 7 },
  "results": [
    { "groupId": "...", "groupName": "AI研发突击队", "memberNum": 29, "ownerId": "z00000001", "createDate": 1773728211515, "activeDate": 1781681467149 }
  ]
}
```

### results 字段

| 字段 | 说明 |
|------|------|
| `groupId` | 群 ID（字符串） |
| `groupName` | 群名称 |
| `memberNum` | 成员数 |
| `ownerId` | 群主帐号 |
| `createDate` | 创建时间（Unix ms） |
| `activeDate` | 最近活跃（Unix ms） |

---

# 三、search-person — 搜索联系人

按关键字搜索联系人（匹配中文名、英文名、帐号）。

## 命令

```bash
python3 ./welink_msg_filter.py search-person --keyword <name> [--page-size 20] [--page 1]
```

## 参数

| 参数 | 默认 | 说明 |
|------|------|------|
| `--keyword` | **必填** | 联系人关键字 |
| `--page-size` | `20` | 每页条数（1–100） |
| `--page` | `1` | 页码 |

## 分页原理

与 `search-group` 相同：查询 `page × page_size` 条后切片。

## 输出

```json
{
  "success": true,
  "summary": { "keyword": "张", "page": 1, "page_size": 20, "total_matches": 1, "returned_count": 1 },
  "results": [
    { "w3account": "z00000001", "chineseName": "张三", "englishName": "Zhang San", "sex": "M", "appointPos": "高级工程师A", "appointGrade": "17", "departmentName": "...", "deptL1Name": "ICT BG", "deptName": "AI训练组", "mobilePhone": "+86-...", "personMail": "...", "personType": "Employee" }
  ]
}
```

### results 字段

| 字段 | 说明 |
|------|------|
| `w3account` | 帐号 |
| `chineseName` | 中文名 |
| `englishName` | 英文名 |
| `sex` | M / F / 0 |
| `appointPos` | 岗位 |
| `appointGrade` | 职级 |
| `departmentName` | 部门全称 |
| `deptL1Name` | 一级部门 |
| `deptName` | 末级部门 |
| `mobilePhone` | 电话 |
| `personMail` | 邮箱 |
| `personType` | 类型 |

---

# 通用错误处理

所有子命令输出 JSON 结构统一。处理流程：

1. **`success=false`** → 读取 `error` 和 `error_type`
2. **`error_type=auth_error`** → 已自动重鉴权，仍失败则需手动 `welink-cli auth login`
3. **`error_type=api_error`** → API 异常，报告 `error`
4. **`error_type=network_error/unknown`** → 建议重试
5. **`error_type=parse_error`** → CLI 输出非法 JSON
6. **`query-messages` 特有** → `truncated=true` 且无错 = 达 max-count；`partial=true` = 中途出错但已收集部分消息

## 通用参数

| 参数 | 说明 |
|------|------|
| `--cli-path <path>` | `welink-cli` 路径，默认系统 PATH 中查找 |
| `--verbose` / `-v` | 进度信息输出到 stderr |
