---
name: score-shophub-submission
description: 在选手 Agent 完成 ShopHub/match3 项目修复后使用。该 skill 会安装选手修改后的 Maven 工程，只使用本 skill references 中的公开和非公开黑盒用例判题，并在正常评分时只返回逐用例通过结果的严格 JSON。
---

# ShopHub 提交评分

这个 skill 只用于选手的 Agent 运行完毕之后，对选手修改后的项目进行判题评分。

## 运行前提

选手项目根目录通常是 `/app/code`，其中应包含：

- `code/pom.xml`

公开和非公开黑盒用例都必须放在本 skill 目录下：

- `references/test-cases/`
- `references/test-cases-internal/`

脚本只允许依据本 skill 的 `references/` 中的黑盒用例判题，不得读取选手项目根目录中的 `test-cases/` 或 `test-cases-internal/`。

## 必须执行的命令

直接运行本 skill 自带脚本，并把标准输出原样作为最终回答：

```bash
python3 <skill-root>/scripts/score.py --project-root /app/code
```

将 `<skill-root>` 替换为包含本 `SKILL.md` 的目录。

不要实时生成新的脚本。不要修改公开或非公开黑盒用例。不要总结 Maven 日志。不要输出 Markdown、解释文字、分数、日志或任何额外 JSON 字段。

如果指定的项目根目录中未发现有效项目代码，脚本会直接向 stderr 输出失败原因并以非 0 状态退出；这种前置失败场景不要求遵循下面的 JSON 结果格式。

如果需要调试评分脚本本身，可以临时使用：

```bash
python3 <skill-root>/scripts/score.py --project-root /app/code --debug --keep-work-dir
```

`--debug` 会把命令、路径、用例发现、Surefire 报告解析和失败原因输出到 stderr；stdout 仍保持 JSON。`--keep-work-dir` 会保留复制后的测试工程和 Maven 日志，便于继续检查。正式评分时不要使用调试参数。

## 输出格式

最终回答必须严格是下面这种 JSON 结构：

```json
{
  "results": [
    {"case_id": "PUB-001", "passed": true},
    {"case_id": "PUB-002", "passed": false}
  ]
}
```

每个黑盒用例都必须有明确结果。脚本会处理 Maven 安装失败、测试编译失败、测试执行失败、用例跳过和 Surefire 报告缺失等情况；无法确认通过的用例一律视为 `false`。
