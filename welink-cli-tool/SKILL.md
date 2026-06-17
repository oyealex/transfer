1|---
2|name: welink-cli-tool
3|description: WeLink CLI 综合封装工具，提供消息查询（过滤+翻页）、群组搜索、联系人搜索三种能力。当用户需要在 WeLink 中搜索消息、群组或联系人时使用此 skill。
4|category: productivity
5|---
6|
7|# WeLink CLI 工具
8|
9|封装脚本为 `./scripts/welink_msg_filter.py`，对 `welink-cli` 的三个核心能力进行了封装增强：
10|
11|| 子命令 | 封装内容 | 底层命令 |
12||--------|----------|----------|
13|| `query-messages`（`msg`） | 多条件过滤 + 游标自动翻页 + 401 重鉴权 | `welink-cli im query-history-message` |
14|| `search-group` | 关键字搜索 + 手动分页 | `welink-cli search group` |
15|| `search-person` | 关键字搜索 + 手动分页 | `welink-cli search person` |
16|
17|> **路径约定**：本文中 `./` 均相对于本 SKILL.md 所在目录。所有子命令共享 `--cli-path`（默认 `welink-cli`）和 `--verbose`（进度输出到 stderr）。
18|
19|---
20|
21|# 一、query-messages — 消息查询
22|
23|查询群聊或私聊的历史消息，支持按时间、发送人、接收人、消息类型、关键字（含正则）过滤。
24|
25|## 命令
26|
27|```bash
28|python3 ./scripts/welink_msg_filter.py query-messages \
29|  --group-id <id> | --user-account <acct> \
30|  [--sender <acct>] [--receiver <acct>] \
31|  [--start-time <ts>] [--end-time <ts>] \
32|  [--content-type <type>] [--keyword <kw>] [--regex] \
33|  [--max-count <n>] [--page-size <n>] [--message-id <id>]
34|```
35|
36|## 参数
37|
38|### 目标（互斥，必选其一）
39|| 参数 | 说明 |
40||------|------|
41|| `--group-id <id>` | 群聊 ID，纯数字字符串 |
42|| `--user-account <acct>` | 用户帐号，`a` + 数字格式 |
43|
44|### 分页
45|| 参数 | 默认 | 说明 |
46||------|------|------|
47|| `--message-id <id>` | `0` | 起始游标（`0`=最新），返回 msgId **严格小于**此值 |
48|| `--page-size <n>` | `100` | 每页条数（1–100） |
49|
50|### 过滤
51|| 参数 | 说明 |
52||------|------|
53|| `--start-time <ts>` | 最早时间（含），Unix ms/s 或 `"YYYY-MM-DD HH:MM:SS"` |
54|| `--end-time <ts>` | 最晚时间（含），格式同上 |
55|| `--sender <acct>` | 按发送人过滤 |
56|| `--receiver <acct>` | 按接收人过滤（私聊） |
57|| `--content-type <type>` | `TEXT_MSG` / `IMAGESPAN_MSG` / `PICTURE_MSG` / `CARD_MSG` / `FILE_MSG` |
58|| `--keyword <kw>` | 内容关键字，大小写不敏感 |
59|| `--regex` | `--keyword` 使用 Python 正则 |
60|| `--max-count <n>` | 最多返回 N 条，`0`=不限 |
61|
62|## 翻页原理
63|
64|底层 API 以 `message-id` 为游标，返回比它更旧的消息（不包含该 ID）。脚本自动用每批最小 `msgId` 推进游标，直到：达到 `--max-count`、超出时间范围、API 返回空、或出错。消息**从新到旧**排序。
65|
66|## 输出
67|
68|```json
69|{
70|  "success": true,
71|  "summary": {
72|    "target_type": "group", "target_id": "...",
73|    "filters": { "start_time": null, "sender": "z00000003", ... },
74|    "matched_count": 8, "total_fetched": 25, "total_pages": 1, "truncated": false
75|  },
76|  "messages": [
77|    { "msgId": ..., "serverSendTime": ..., "sender": ..., "content": ..., "contentType": ... }
78|  ],
79|  "partial": false
80|}
81|```
82|
83|## 示例
84|
85|```bash
86|# 查某人最近消息
87|python3 ./scripts/welink_msg_filter.py query-messages --group-id 943609380077752540 --sender z00000003 --max-count 20
88|
89|# 关键字
90|python3 ./scripts/welink_msg_filter.py query-messages --group-id 943609380077752540 --keyword "周报"
91|
92|# 正则
93|python3 ./scripts/welink_msg_filter.py query-messages --group-id 943609380077752540 --keyword "\\d+个(Feature|Bug)" --regex
94|
95|# 时间范围
96|python3 ./scripts/welink_msg_filter.py query-messages --group-id 943609380077752540 --start-time "2024-06-01" --end-time "2024-06-15"
97|
98|# 文件消息
99|python3 ./scripts/welink_msg_filter.py query-messages --group-id 943609380077752540 --content-type FILE_MSG
100|```
101|
102|## ID 解析流程
103|
104|用户通常不会直接提供群聊 ID 或用户帐号，而是用自然语言描述目标（如"查一下 AI 突击队的消息"）。此时按以下流程处理：
105|
106|### 1. 判断目标类型
107|
108|从用户描述中推断目标是群组还是联系人：
109|
110|| 用户表述 | 推断类型 |
111||----------|----------|
112|| "XX 群的消息"、"XX 群聊"、群名称 | 群组 |
113|| "XX 发的消息"、"XX 的聊天记录"、人名 | 联系人 |
114|
115|### 2. 搜索候选
116|
117|- **群组** → `search-group --keyword <提取的关键字>`
118|- **联系人** → `search-person --keyword <提取的关键字>`
119|
120|### 3. 匹配结果处理
121|
122|向用户展示候选项，让其选择。Agent 的交互工具（如 Hermes 的 `clarify`）通常限制每次 4 个显式选项，按以下策略展示：
123|
124|**≤ 4 条结果**：直接全部列出，每条一行关键信息。1 条时可自动使用并告知用户。
125|
126|**> 4 条结果**：每轮展示 3 个候选 + 翻页选项：
127|
128|```
129|选项 1–3：前 3 条候选项
130|选项 4：  "下一页（还有 N 条）"
131|选项 5（自由输入）：用户输入更精确的描述
132|```
133|
134|用户点"下一页"时展示下一批 3 条，最后一页不足 3 条则展示剩余全部并去掉翻页选项。用户输入更精确描述时，用新关键字重新搜索。
135|
136|每条候选的展示格式：
137|
138|| 类型 | 格式 |
139||------|------|
140|| 群组 | `groupName（成员数 人）` |
141|| 联系人 | `chineseName — w3account — deptName` |
142|
143|### 4. 执行查询
144|
145|用户选择后用对应的 `groupId` 或 `w3account` 执行 `query-messages`。
146|
147|### 示例对话
148|
149|```
150|用户：查一下 AI 突击队最近的消息
151|
152|Agent：
153|  1. search-group --keyword "AI突击" → 返回 1 条，groupId=1000000000000000001
154|  2. "找到群组「AI研发突击队」(29人)，正在查询消息..."
155|  3. query-messages --group-id 1000000000000000001 --max-count 20
156|```
157|
158|```
159|用户：张三最近说了什么
160|
161|Agent：
162|  1. search-person --keyword "张三" → 返回 5 条
163|  2. 向用户展示选择：
164|
165|     找到 5 个匹配"张三"的联系人，请选择：
166|       1. 张三 — z00000001 — AI训练组
167|       2. 张三丰 — z00000015 — 基础架构部
168|       3. 张三石 — a12345678 — 测试部
169|       4. 下一页（还有 2 条）
170|
171|  3. 用户点"下一页"，展示第二轮：
172|
173|     继续浏览：
174|       1. 张三千 — z00000099 — 安全部
175|       2. 张三木 — z00000150 — 运维部
176|       
177|     （最后一页不足 3 条，无翻页选项，用户可通过自由输入重搜）
178|
179|  4. 用户输入"AI训练组的张三"
180|  5. 重新 search-person --keyword "AI训练组的张三" 或直接匹配到第一条
181|  6. query-messages --user-account z00000001 --sender z00000001 --max-count 20
182|```
183|
184|> **注意**：查联系人消息时，如果目的是"查此人发的消息"，需同时加 `--sender` 过滤，因为 `query-messages --user-account` 查的是私聊对话，不是你与该联系人在所有群里的发言。
185|
186|---
187|
188|# 二、search-group — 搜索群组
189|
190|按名称关键字搜索群组。
191|
192|## 命令
193|
194|```bash
195|python3 ./scripts/welink_msg_filter.py search-group --keyword <name> [--page-size 20] [--page 1]
196|```
197|
198|## 参数
199|
200|| 参数 | 默认 | 说明 |
201||------|------|------|
202|| `--keyword` | **必填** | 群组名称关键字 |
203|| `--page-size` | `20` | 每页条数（1–100） |
204|| `--page` | `1` | 页码 |
205|
206|## 分页原理
207|
208|底层不支持翻页，脚本查询 `page × page_size` 条结果后切片。如需第 2 页（每页 20）→ 实际查 40 条，返回第 21–40 条。
209|
210|## 输出
211|
212|```json
213|{
214|  "success": true,
215|  "summary": { "keyword": "AI", "page": 1, "page_size": 20, "total_matches": 15, "returned_count": 7 },
216|  "results": [
217|    { "groupId": "...", "groupName": "AI研发突击队", "memberNum": 29, "ownerId": "z00000001", "createDate": 1773728211515, "activeDate": 1781681467149 }
218|  ]
219|}
220|```
221|
222|### results 字段
223|
224|| 字段 | 说明 |
225||------|------|
226|| `groupId` | 群 ID（字符串） |
227|| `groupName` | 群名称 |
228|| `memberNum` | 成员数 |
229|| `ownerId` | 群主帐号 |
230|| `createDate` | 创建时间（Unix ms） |
231|| `activeDate` | 最近活跃（Unix ms） |
232|
233|---
234|
235|# 三、search-person — 搜索联系人
236|
237|按关键字搜索联系人（匹配中文名、英文名、帐号）。
238|
239|## 命令
240|
241|```bash
242|python3 ./scripts/welink_msg_filter.py search-person --keyword <name> [--page-size 20] [--page 1]
243|```
244|
245|## 参数
246|
247|| 参数 | 默认 | 说明 |
248||------|------|------|
249|| `--keyword` | **必填** | 联系人关键字 |
250|| `--page-size` | `20` | 每页条数（1–100） |
251|| `--page` | `1` | 页码 |
252|
253|## 分页原理
254|
255|与 `search-group` 相同：查询 `page × page_size` 条后切片。
256|
257|## 输出
258|
259|```json
260|{
261|  "success": true,
262|  "summary": { "keyword": "张", "page": 1, "page_size": 20, "total_matches": 1, "returned_count": 1 },
263|  "results": [
264|    { "w3account": "z00000001", "chineseName": "张三", "englishName": "Zhang San", "sex": "M", "appointPos": "高级工程师A", "appointGrade": "17", "departmentName": "...", "deptL1Name": "ICT BG", "deptName": "AI训练组", "mobilePhone": "+86-...", "personMail": "...", "personType": "Employee" }
265|  ]
266|}
267|```
268|
269|### results 字段
270|
271|| 字段 | 说明 |
272||------|------|
273|| `w3account` | 帐号 |
274|| `chineseName` | 中文名 |
275|| `englishName` | 英文名 |
276|| `sex` | M / F / 0 |
277|| `appointPos` | 岗位 |
278|| `appointGrade` | 职级 |
279|| `departmentName` | 部门全称 |
280|| `deptL1Name` | 一级部门 |
281|| `deptName` | 末级部门 |
282|| `mobilePhone` | 电话 |
283|| `personMail` | 邮箱 |
284|| `personType` | 类型 |
285|
286|---
287|
288|# 通用错误处理
289|
290|所有子命令输出 JSON 结构统一。处理流程：
291|
292|1. **`success=false`** → 读取 `error` 和 `error_type`
293|2. **`error_type=auth_error`** → 已自动重鉴权，仍失败则需手动 `welink-cli auth login`
294|3. **`error_type=api_error`** → API 异常，报告 `error`
295|4. **`error_type=network_error/unknown`** → 建议重试
296|5. **`error_type=parse_error`** → CLI 输出非法 JSON
297|6. **`query-messages` 特有** → `truncated=true` 且无错 = 达 max-count；`partial=true` = 中途出错但已收集部分消息
298|
299|## 通用参数
300|
301|| 参数 | 说明 |
302||------|------|
303|| `--cli-path <path>` | `welink-cli` 路径，默认系统 PATH 中查找 |
304|| `--verbose` / `-v` | 进度信息输出到 stderr |
305|