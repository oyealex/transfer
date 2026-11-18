"""报告装配器。

把 ``stats.json`` / ``evidence.md`` / ``narrative.json`` 拼成最终报告。

职责边界：

- **只做两件事**：标量替换、行块展开。
- **不做判断**：不决定定性、不挑问题、不写措辞。判断在 Skill 里，措辞在 ``narrative.json``。
- **排序是规则驱动的**：分布表按分类固定顺序，Top3 按问题点数降序，属于确定性变换。

模板语法（三种）：

============================  ====================================================
``{{键}}``                   标量替换；键不存在直接报错，不静默留白
``{{#each 数据集}}…{{/each}}`` 行块展开；块内 ``{{字段}}`` 取当前行，取不到回落全局
``{{#if 键}}…{{/if}}``        真假块；空串 / None / 空列表 / 0 视为假
============================  ====================================================

块可以嵌套，行上下文按栈查找：先当前行，再外层，最后全局。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Sequence

from .errors import AuditError
from .stats import PROBLEMS, load_evidence

TOKEN = re.compile(r"\{\{([^{}]*)\}\}")
COMMENT = re.compile(r"<!--.*?-->", re.S)


class RenderError(AuditError):
    """模板或输入数据不满足装配契约。"""


# ------------------------------------------------------------------ 模板解析


def _tokenize(text: str) -> list[tuple[str, str]]:
    parts: list[tuple[str, str]] = []
    pos = 0
    for match in TOKEN.finditer(text):
        if match.start() > pos:
            parts.append(("text", text[pos : match.start()]))
        parts.append(("tag", match.group(1).strip()))
        pos = match.end()
    if pos < len(text):
        parts.append(("text", text[pos:]))
    return parts


def _trim_body(nodes: list[tuple]) -> list[tuple]:
    """块体的首尾换行去掉，避免展开后表格行之间插进空行。"""
    if nodes and nodes[0][0] == "text":
        nodes[0] = ("text", nodes[0][1].lstrip("\n"))
    if nodes and nodes[-1][0] == "text":
        nodes[-1] = ("text", nodes[-1][1].rstrip("\n"))
    return [node for node in nodes if not (node[0] == "text" and node[1] == "")]


def _parse_until(
    parts: Sequence[tuple[str, str]], index: int, end_tag: str | None
) -> tuple[list[tuple], int]:
    nodes: list[tuple] = []
    while index < len(parts):
        kind, value = parts[index]
        if kind == "tag" and end_tag is not None and value == end_tag:
            return nodes, index + 1
        if kind == "text":
            nodes.append(("text", value))
            index += 1
            continue
        if value.startswith("#each ") or value.startswith("#if "):
            head, _, name = value.partition(" ")
            body, index = _parse_until(parts, index + 1, f"/{head[1:]}")
            nodes.append((head[1:], name.strip(), _trim_body(body)))
            continue
        if value.startswith("/"):
            raise RenderError(f"模板里有未配对的结束标记 {{{{ {value} }}}}")
        nodes.append(("scalar", value))
        index += 1
    if end_tag is not None:
        raise RenderError(f"模板里的块没有闭合，缺少 {{{{ {end_tag} }}}}")
    return nodes, index


def _parse(parts: Sequence[tuple[str, str]]) -> list[tuple]:
    nodes, _ = _parse_until(parts, 0, None)
    return nodes


def _lookup(name: str, stack: Sequence[dict[str, Any]], missing: list[str]) -> Any:
    for row in reversed(stack):
        if name in row:
            return row[name]
    missing.append(name)
    return ""


def _truthy(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, str):
        return value.strip() != ""
    if isinstance(value, (list, tuple, dict)):
        return len(value) > 0
    if isinstance(value, (int, float)):
        return value != 0
    return bool(value)


def _as_text(value: Any) -> str:
    if value is None:
        return ""
    return value if isinstance(value, str) else str(value)


def _escape_cell(text: str) -> str:
    """Markdown 表格单元格里的裸 | 会把一行拆成多格。

    证据表侧要求写 ``\\|``，stats 解析时会还原成 ``|``，所以装配输出时必须再转义一次。
    """
    return text.replace("|", "\\|")


def _at_table_line(out: Sequence[str]) -> bool:
    """当前输出是否落在以 ``|`` 开头的表格行里。只有表格单元格需要转义竖线。"""
    text = "".join(out)
    line = text[text.rfind("\n") + 1 :]
    return line.lstrip().startswith("|")


def _render_nodes(
    nodes: Sequence[tuple],
    stack: Sequence[dict[str, Any]],
    missing: list[str],
) -> str:
    out: list[str] = []
    for node in nodes:
        kind = node[0]
        if kind == "text":
            out.append(node[1])
        elif kind == "scalar":
            value = _as_text(_lookup(node[1], stack, missing))
            if _at_table_line(out):
                value = _escape_cell(value)
            out.append(value)
        elif kind == "if":
            if _truthy(_lookup(node[1], stack, missing)):
                out.append(_render_nodes(node[2], stack, missing))
        elif kind == "each":
            rows = _lookup(node[1], stack, missing)
            if not isinstance(rows, list):
                raise RenderError(f"{{{{#each {node[1]}}}}} 绑定的不是列表，是 {type(rows).__name__}")
            rendered = [_render_nodes(node[2], [*stack, row], missing) for row in rows]
            if rendered:
                out.append("\n".join(rendered))
                out.append("\n")
        else:  # pragma: no cover - 解析器不会产出别的类型
            raise RenderError(f"未知模板节点 {kind}")
    return "".join(out)


# ------------------------------------------------------------------ 工具


def _s(value: Any) -> str:
    return _as_text(value).strip()


def _no_tail(text: str) -> str:
    return text.rstrip("。；; ")


def _int(value: Any) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return 0


def _share(payload: dict[str, Any], name: str) -> str:
    for item in payload.get("问题分布") or []:
        if item.get("类型") == name:
            return str(item.get("占比", ""))
    return "0.0%"


def _problem_rows(parsed: dict[str, Any], problem: str) -> list[dict[str, str]]:
    return [row for row in parsed["逻辑点证据"] if _s(row.get("问题")) == problem]


def _case_position(parsed: dict[str, Any], name: str) -> str:
    for row in parsed["用例清单"]:
        if _s(row.get("用例")) == name:
            return _s(row.get("位置"))
    return ""


def _case_rows(parsed: dict[str, Any], names: Sequence[str]) -> list[dict[str, str]]:
    return [{"用例": name, "位置": _case_position(parsed, name)} for name in names]


# ------------------------------------------------------------------ 上下文


def build_context(
    stats: dict[str, Any],
    evidence_path: Path,
    narrative: dict[str, Any],
) -> dict[str, Any]:
    parsed = load_evidence(evidence_path)
    overview = parsed["_overview"]
    units = parsed["变更单元"]
    points = parsed["逻辑点证据"]
    case_rows = parsed["用例清单"]
    metrics = stats["指标"]
    verdict = stats["定性"]

    total = _int(metrics["全部逻辑点"])

    # 1.1 构成
    composition = [
        {
            "分类": item["分类"],
            "语言": item["语言"],
            "文件数": item["文件数"],
            "新增": item["新增"],
            "删除": item["删除"],
        }
        for item in stats["变更构成"]["纳入分类"]
    ]
    excluded = [
        {"分类": key, "数量": value} for key, value in (stats["变更构成"]["排除"] or {}).items()
    ]

    # 1.2 分布
    distribution = [
        {
            "类型": item["类型"],
            "数量": item["数量"],
            "占比": item["占比"],
            "位置摘要": item.get("位置摘要") or "—",
        }
        for item in stats["问题分布"]
    ]

    # 1.3 Top3
    gap_files = {item["文件"]: item for item in stats.get("缺口文件") or []}
    top3 = []
    for index, item in enumerate(narrative.get("top3") or [], start=1):
        path = _s(item.get("文件"))
        if path not in gap_files:
            raise RenderError(f"top3 第 {index} 条的「文件」不在问题分布里：{path}")
        bucket = gap_files[path]
        top3.append(
            {
                "序号": index,
                "文件": path,
                "点数": bucket["点数"],
                "构成": "、".join(
                    f"{name} {bucket[name]}"
                    for name in ("未覆盖", "无效", "低价值")
                    if bucket.get(name)
                ),
                "问题": _no_tail(_s(item.get("问题"))),
                "建议": _no_tail(_s(item.get("建议"))),
            }
        )

    # 2.1 分组
    groups = []
    for problem in ("未覆盖", "无效", "低价值", "不确定"):
        rows = _problem_rows(parsed, problem)
        if not rows:
            continue
        groups.append(
            {
                "分组标题": problem,
                "分组数量": len(rows),
                "行": [
                    {
                        "编号": _s(row.get("编号")),
                        "位置": _s(row.get("位置")),
                        "原因": _s(row.get("原因")),
                        "说明": _s(row.get("说明")) or "—",
                        "覆盖用例": _s(row.get("覆盖用例")) or "—",
                    }
                    for row in rows
                ],
            }
        )

    lists = stats.get("用例名单") or {}
    matrix = [
        {
            "编号": _s(row.get("编号")),
            "位置": _s(row.get("位置")),
            "条件摘要": _s(row.get("条件摘要")),
            "问题": _s(row.get("问题")),
            "原因": _s(row.get("原因")) or "—",
        }
        for row in points
    ]

    zero_point = total == 0
    short = bool(stats.get("短路"))
    limited = _int(stats.get("C/C++受限文件数", 0))
    if zero_point:
        if verdict["规则"] == "R0":
            note = "全部变更单元都是「无法获取」，不产出质量结论。"
        elif limited and short:
            note = (
                f"本次没有 Java / Python 生产代码改动；另有 {limited} 个 C/C++ 受限文件，"
                "未抽取逻辑点，直接记合格。"
            )
        elif limited:
            note = f"本次没有产生逻辑点；{limited} 个 C/C++ 受限文件未抽取逻辑点，直接记合格。"
        elif short:
            note = "本次变更没有生产代码改动（新增 + 删除 0 行），没有可判的逻辑点，直接记合格。"
        else:
            note = "本次变更没有产生逻辑点，直接记合格。"
    else:
        note = ""

    top3.sort(key=lambda item: (-item["点数"], item["文件"]))
    for index, item in enumerate(top3, start=1):
        item["序号"] = index

    ctx: dict[str, Any] = {
        # 标题与基本信息
        "检测对象标识": _s(overview.get("对象标识")) or "对象",
        "对象类型": _s(overview.get("对象类型")),
        "对象标识": _s(overview.get("对象标识")),
        "变更单元数": _s(overview.get("变更单元数")),
        "涉及仓": _s(overview.get("涉及仓")),
        "来源含本地仓": _s(overview.get("含本地仓单元数")),
        "来源仅diff": _s(overview.get("仅 diff 单元数")),
        "来源无法获取": _s(overview.get("无法获取单元数")),
        "变更单元": [
            {
                "MR 地址": _s(row.get("MR 地址")),
                "标题": _s(row.get("标题")),
                "作者": _s(row.get("作者")),
                "仓": _s(row.get("仓")),
                "来源档位": _s(row.get("来源档位")),
            }
            for row in units
        ],
        "构成": composition,
        "排除": excluded,
        "纳入文件数": stats["变更构成"]["纳入文件数"],
        "新增DT数": metrics["新增DT数"],
        "修改DT数": metrics["修改DT数"],
        "既有用例数": metrics["既有用例数"],
        "用例总数": metrics["用例总数"],
        "变更生产代码KLOC": metrics["变更生产代码KLOC"],
        "新增DT密度": metrics["新增DT密度"]["text"],
        "C/C++受限文件数": limited,
        "有受限": bool(limited),
        # 1.2
        "偏差声明": _no_tail(_s(narrative.get("偏差声明"))),
        "有偏差声明": bool(_s(narrative.get("偏差声明"))),
        "定性": verdict["结论"],
        "定性规则": verdict["规则"],
        "定性依据": _no_tail(verdict["依据"]),
        "分布": distribution,
        "全部逻辑点": total,
        "有效占比": _share(stats, "有效"),
        "未覆盖占比": _share(stats, "未覆盖"),
        "无效占比": _share(stats, "无效"),
        "低价值占比": _share(stats, "低价值"),
        "不确定占比": _share(stats, "不确定"),
        "命中类数": stats.get("命中类数", 0),
        "一句话判断": _no_tail(_s(narrative.get("一句话判断"))),
        "无逻辑点": zero_point,
        "无逻辑点说明": note,
        # 1.3
        "top3": top3,
        "有top3": bool(top3),
        "无top3": not top3,
        # 2.1
        "2.1总评": _no_tail(_s((narrative.get("维度") or {}).get("2.1", {}).get("总评"))),
        "2.1分组": groups,
        "2.1建议": [
            {"动作": _no_tail(_s(item))}
            for item in ((narrative.get("维度") or {}).get("2.1", {}) or {}).get("建议") or []
        ],
        # 2.2
        "2.2总评": _no_tail(_s((narrative.get("维度") or {}).get("2.2", {}).get("总评"))),
        "2.2建议": [
            {"动作": _no_tail(_s(item))}
            for item in ((narrative.get("维度") or {}).get("2.2", {}) or {}).get("建议") or []
        ],
        "无效用例": _case_rows(parsed, lists.get("无效") or []),
        "低价值用例": _case_rows(parsed, lists.get("低价值") or []),
        "未涉及用例": _case_rows(parsed, lists.get("未涉及改动") or []),
        "有无效用例": bool(lists.get("无效")),
        "有低价值用例": bool(lists.get("低价值")),
        "有未涉及用例": bool(lists.get("未涉及改动")),
        "无无效用例": not lists.get("无效"),
        "无低价值用例": not lists.get("低价值"),
        "无未涉及用例": not lists.get("未涉及改动"),
        "有21建议": bool((narrative.get("维度") or {}).get("2.1", {}).get("建议")),
        "有22建议": bool((narrative.get("维度") or {}).get("2.2", {}).get("建议")),
        "无21建议": not (narrative.get("维度") or {}).get("2.1", {}).get("建议"),
        "无22建议": not (narrative.get("维度") or {}).get("2.2", {}).get("建议"),
        # 3.1
        "取数": _no_tail(_s((narrative.get("3.1") or {}).get("取数"))),
        "去重": _no_tail(_s((narrative.get("3.1") or {}).get("去重"))),
        "额外限制": _no_tail(_s((narrative.get("3.1") or {}).get("额外限制"))),
        # 3.3
        "矩阵": matrix,
        "有矩阵": bool(matrix),
        "无矩阵": not matrix,
        "有变更单元": bool(units),
        "无变更单元": not units,
        "有构成": bool(composition),
        "无构成": not composition,
        "短路": short,
    }
    return ctx


# ------------------------------------------------------------------ 校验


def _section(text: str, start: str, end: str | None = None) -> str:
    begin = text.find(start)
    if begin < 0:
        return ""
    stop = text.find(end, begin) if end else len(text)
    return text[begin : stop if stop >= 0 else len(text)]


def _table_rows(block: str) -> int:
    lines = [line for line in block.splitlines() if line.strip().startswith("|")]
    return max(0, len(lines) - 2)


def check_report(text: str, ctx: dict[str, Any], stats: dict[str, Any]) -> list[str]:
    """回读生成的报告，做确定性校验。返回违规项，空列表表示通过。"""
    problems: list[str] = []

    if "{{" in text or "}}" in text:
        problems.append("报告里残留了未替换的 {{ }}")
    if "<!--" in text or "-->" in text:
        problems.append("报告里残留了 HTML 注释")

    missing_sections = [f"3.4.{n}" for n in range(1, 5) if f"#### 3.4.{n}" not in text]
    if missing_sections:
        problems.append("3.4 术语与判定说明不完整，缺：" + "、".join(missing_sections))

    anchors = {"定性": f"DT 质量定性：{ctx['定性']}"}
    if ctx["全部逻辑点"]:
        anchors["数据行"] = (
            f"有效 {ctx['有效占比']} ｜ 未覆盖 {ctx['未覆盖占比']} ｜ "
            f"无效 {ctx['无效占比']} ｜ 低价值 {ctx['低价值占比']} ｜ "
            f"不确定 {ctx['不确定占比']}"
        )
        anchors["合计"] = f"合计 {ctx['全部逻辑点']} 个逻辑点"
        anchors["命中类数"] = f"3 类问题中命中 {ctx['命中类数']} 类"
    for label, needle in anchors.items():
        if needle not in text:
            problems.append(f"{label} 与 stats.json 对不上：报告里找不到「{needle}」")

    matrix_rows = _table_rows(_section(text, "### 3.3", "### 3.4"))
    expect_matrix = _int(ctx["全部逻辑点"])
    if matrix_rows != expect_matrix:
        problems.append(f"3.3 矩阵 {matrix_rows} 行，逻辑点应为 {expect_matrix} 个")

    # 3.3 取自证据表、1.2 取自 stats.json，逐类对齐，防止两边静默不一致
    matrix_counts: dict[str, int] = {}
    for row in ctx["矩阵"]:
        name = row["问题"]
        matrix_counts[name] = matrix_counts.get(name, 0) + 1
    for item in stats.get("问题分布") or []:
        name = item["类型"]
        expected = _int(item["数量"])
        actual = matrix_counts.get(name, 0)
        if actual != expected:
            problems.append(f"3.3 矩阵里「{name}」有 {actual} 行，stats.json 是 {expected} 个")

    if ctx["全部逻辑点"]:
        dist_rows = _table_rows(_section(text, "**问题分布**", "**数据**"))
        if dist_rows != len(PROBLEMS):
            problems.append(f"1.2 分布表 {dist_rows} 行，应为 {len(PROBLEMS)} 行")

    top_block = _section(text, "### 1.3", "## 二、")
    top_items = re.findall(r"^\d+\. \*\*`", top_block, flags=re.M)
    if len(top_items) > 3:
        problems.append(f"1.3 列了 {len(top_items)} 条，最多 3 条")
    points = [int(n) for n in re.findall(r"问题点 (\d+) 个", top_block)]
    if points != sorted(points, reverse=True):
        problems.append("1.3 条目没有按问题点数降序排列")
    listed = [item["文件"] for item in ctx["top3"]]
    expected_top = [item["文件"] for item in (stats.get("缺口文件") or [])[: len(listed)]]
    if listed != expected_top:
        problems.append("1.3 列出的文件不是问题点最多的前几个：" + "、".join(listed))

    internal = (
        ".dt-audit",
        "_diffs",
        "evidence.md",
        "narrative.json",
        "stats.json",
        "manifest.json",
        "scope.json",
        "index.json",
        "signals.json",
        "shard-",
        "judge-",
        "run.py",
        "dt_audit",
        "references/",
        "SKILL.md",
    )
    leaked = [token for token in internal if token in text]
    if leaked:
        problems.append("报告里出现了读者拿不到的内部引用：" + "、".join(leaked))

    for heading in ("## 一、整体结论", "## 二、DT 质量评估", "## 三、附录", "### 3.4 术语与判定说明"):
        if heading not in text:
            problems.append(f"报告缺少固定章节：{heading}")

    return problems


# ------------------------------------------------------------------ 入口


REQUIRED_NARRATIVE = ("一句话判断", "维度", "top3", "3.1")
SECTION_RULES = {
    "2.1": ("总评", "建议"),
    "2.2": ("总评", "建议"),
}


def _validate_narrative(narrative: dict[str, Any], stats: dict[str, Any]) -> None:
    """判断类字段不许留空；类型写错直接报错，避免装配出空壳或崩在渲染中途。"""
    if not isinstance(narrative, dict):
        raise RenderError("narrative.json 顶层必须是对象")
    problems = [f"缺少字段「{name}」" for name in REQUIRED_NARRATIVE if name not in narrative]

    if not _s(narrative.get("一句话判断")):
        problems.append("字段「一句话判断」不能为空")
    declaration = narrative.get("偏差声明")
    if declaration is not None and not isinstance(declaration, str):
        problems.append("字段「偏差声明」必须是字符串")
    object_type = (stats.get("对象") or {}).get("对象类型", "")
    if not _s(declaration) and (object_type != "SR" or stats.get("短路")):
        problems.append("对象不是 SR、或走了短路时，「偏差声明」不能为空")

    sections = narrative.get("维度")
    if not isinstance(sections, dict):
        raise RenderError("narrative.json 不合格：缺少「维度」对象")
    for name, fields in SECTION_RULES.items():
        block = sections.get(name)
        if not isinstance(block, dict):
            problems.append(f"缺少字段「维度.{name}」")
            continue
        for field in fields:
            if field not in block:
                problems.append(f"缺少字段「维度.{name}.{field}」")
        if not _s(block.get("总评")):
            problems.append(f"维度.{name}.总评 不能为空")
        advice = block.get("建议")
        if not isinstance(advice, list):
            problems.append(f"维度.{name}.建议 必须是字符串数组")
        else:
            for index, item in enumerate(advice, start=1):
                if not isinstance(item, str) or not item.strip():
                    problems.append(f"维度.{name}.建议 第 {index} 条必须是非空字符串")

    block31 = narrative.get("3.1")
    if isinstance(block31, dict):
        for field in ("取数", "去重", "额外限制"):
            if field not in block31:
                problems.append(f"缺少字段「3.1.{field}」")
            elif not isinstance(block31.get(field), str):
                problems.append(f"字段「3.1.{field}」必须是字符串")
    elif "3.1" in narrative:
        problems.append("narrative.3.1 必须是对象")

    top3 = narrative.get("top3")
    if top3 is None:
        top3 = []
    if not isinstance(top3, list):
        problems.append("字段「top3」必须是数组")
        top3 = []
    if len(top3) > 3:
        problems.append(f"top3 最多 3 条，实际 {len(top3)} 条")
    for index, item in enumerate(top3, start=1):
        if not isinstance(item, dict):
            problems.append(f"top3 第 {index} 条必须是对象")
            continue
        for field in ("文件", "问题", "建议"):
            if not _s(item.get(field)):
                problems.append(f"top3 第 {index} 条缺少「{field}」")

    if problems:
        raise RenderError("narrative.json 不合格：" + "；".join(problems[:8]))


def _tidy(text: str) -> str:
    """收拾块展开留下的空行：表格中间不留空行，连续空行压成一个。"""
    lines = text.splitlines()
    kept: list[str] = []
    for index, line in enumerate(lines):
        if line.strip() == "":
            previous = next((l for l in reversed(kept) if l.strip()), "")
            following = next((l for l in lines[index + 1 :] if l.strip()), "")
            if previous.startswith("|") and following.startswith("|"):
                continue
        kept.append(line)
    return re.sub(r"\n{3,}", "\n\n", "\n".join(kept)).strip() + "\n"


def render_report(
    *,
    template: Path,
    stats_path: Path,
    evidence_path: Path,
    narrative_path: Path,
) -> tuple[str, dict[str, Any], list[str]]:
    """渲染并校验，返回（报告文本、上下文、校验问题）。"""
    if not template.is_file():
        raise RenderError(f"模板不存在：{template}")
    stats = json.loads(stats_path.read_text(encoding="utf-8"))
    narrative = json.loads(narrative_path.read_text(encoding="utf-8"))
    _validate_narrative(narrative, stats)
    ctx = build_context(stats, evidence_path, narrative)

    nodes = _parse(_tokenize(COMMENT.sub("", template.read_text(encoding="utf-8"))))
    missing: list[str] = []
    text = _render_nodes(nodes, [ctx], missing)
    if missing:
        raise RenderError(
            "模板引用了上下文里没有的键（拼错或数据缺字段）：" + "、".join(sorted(set(missing)))
        )
    text = _tidy(text)
    problems = check_report(text, ctx, stats)
    return text, ctx, problems


def run_render(
    *,
    template: Path,
    stats: Path,
    evidence: Path,
    narrative: Path,
    out: Path | None = None,
    check: bool = False,
) -> dict[str, Any]:
    text, ctx, problems = render_report(
        template=template,
        stats_path=stats,
        evidence_path=evidence,
        narrative_path=narrative,
    )
    target = out or evidence.with_name("DT-AUDIT-REPORT.md")
    if check and problems:
        raise RenderError(f"报告校验未通过（{len(problems)} 项）：" + "；".join(problems[:10]))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return {
        "报告路径": str(target),
        "校验": {"通过": not problems, "问题": problems},
        "定性": ctx["定性"],
    }
