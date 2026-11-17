"""报告装配器。

把 ``stats.json`` / ``evidence.md`` / ``manifest.json`` / ``narrative.json`` 拼成最终报告。

职责边界（很重要）：

- **只做三件事**：标量替换、行块展开、静态段注入。
- **不做判断**：不决定选哪几条问题、不写措辞、不给影响档。那些由 ``narrative.json`` 提供。
- **排序是规则驱动的**：分布表、Top3、未覆盖清单按「影响档 / 严重度」这两个已声明的枚举排序，
  规则写在 ``references/报告结构.md``，属于确定性变换，不是判断。

模板语法（四种）：

============================  ====================================================
``{{键}}``                   标量替换；键不存在直接报错，不静默留白
``{{#each 数据集}}…{{/each}}`` 行块展开；块内 ``{{字段}}`` 取当前行，取不到回落全局
``{{#if 键}}…{{/if}}``        真假块；空串 / None / 空列表 / 0 视为假
``{{include: 文件名}}``       把 ``references/<文件名>.md`` 的正文逐字注入（去掉注释与 H1）
============================  ====================================================

块可以嵌套（例如「是 SR 对象」块里再展开 MR 清单），行上下文按栈查找：先当前行，再外层，最后全局。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Sequence

from .errors import AuditError
from .stats import SEVERITY_RANK, load_evidence

TOKEN = re.compile(r"\{\{([^{}]*)\}\}")
COMMENT = re.compile(r"<!--.*?-->", re.S)

CLASS_COVERAGE = "场景覆盖不足"
CLASS_ASSERTION = "断言质量低"
CLASS_OFFSET = "测试对象偏移"
CLASS_DEGRADE = "测试退化"
HINT_EVIDENCE = "证据缺口"

IMPACT_RANK = {"高": 0, "中": 1, "低": 2}
IMPACT_VALUES = ("高", "中", "低")

#: 包内 references 目录，`{{include}}` 的回落位置
DEFAULT_REFERENCES = Path(__file__).resolve().parents[2] / "references"

#: 报告里 3.3 矩阵的列，从「逻辑点证据」投影而来
MATRIX_FIELDS = ("编号", "位置", "类型", "条件摘要", "触达用例", "状态")


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
        if value.startswith("include:"):
            nodes.append(("include", value[len("include:") :].strip()))
            index += 1
            continue
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
    只在最终输出处转义，不能放进 ``_as_text``——上下文构建也用它，会变成双重转义。
    """
    return text.replace("|", "\\|")


def _render_nodes(
    nodes: Sequence[tuple],
    stack: Sequence[dict[str, Any]],
    search_dirs: Sequence[Path],
    missing: list[str],
) -> str:
    out: list[str] = []
    for node in nodes:
        kind = node[0]
        if kind == "text":
            out.append(node[1])
        elif kind == "scalar":
            out.append(_escape_cell(_as_text(_lookup(node[1], stack, missing))))
        elif kind == "include":
            out.append(_include(node[1], search_dirs))
        elif kind == "if":
            if _truthy(_lookup(node[1], stack, missing)):
                out.append(_render_nodes(node[2], stack, search_dirs, missing))
        elif kind == "each":
            rows = _lookup(node[1], stack, missing)
            if not isinstance(rows, list):
                raise RenderError(f"{{{{#each {node[1]}}}}} 绑定的不是列表，是 {type(rows).__name__}")
            rendered = [
                _render_nodes(node[2], [*stack, row], search_dirs, missing) for row in rows
            ]
            if rendered:
                out.append("\n".join(rendered))
                out.append("\n")
        else:  # pragma: no cover - 解析器不会产出别的类型
            raise RenderError(f"未知模板节点 {kind}")
    return "".join(out)


def _include(name: str, search_dirs: Sequence[Path]) -> str:
    """注入 references 下的静态段，去掉维护注释与文件级标题。

    先找模板所在目录（允许自带覆盖版），再回落到包内的 references 目录。
    """
    path = next((d / f"{name}.md" for d in search_dirs if (d / f"{name}.md").is_file()), None)
    if path is None:
        searched = "、".join(str(d) for d in search_dirs)
        raise RenderError(f"{{{{include: {name}}}}} 找不到文件，已找过：{searched}")
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"^\s*<!--.*?-->\s*", "", text, flags=re.S)
    lines = text.splitlines()
    while lines and not lines[0].strip():
        lines.pop(0)
    if lines and lines[0].startswith("# "):
        lines.pop(0)
    return "\n".join(lines).strip() + "\n"


# ------------------------------------------------------------------ 上下文


def _s(value: Any) -> str:
    return _as_text(value).strip()


def _int(value: Any) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return 0


def _join(parts: Sequence[str], sep: str = "；") -> str:
    kept = [p for p in parts if p]
    return sep.join(kept) if kept else "无"


def _strip_prefix(text: str, prefix: str) -> str:
    for chunk in re.split(r"[；;]", text):
        chunk = chunk.strip()
        if chunk.startswith(prefix):
            return chunk[len(prefix) :].strip()
    return ""


def _review(rows: Sequence[dict[str, str]], *, state: str, prefix: str) -> list[str]:
    out = []
    for row in rows:
        if state and _s(row.get("状态")) != state:
            continue
        got = _strip_prefix(_s(row.get("复核")), prefix)
        if got:
            out.append(got)
    return out


def _mr_number(url: str) -> str:
    tail = url.rstrip("/").rsplit("/", 1)[-1]
    return f"!{tail}" if tail.isdigit() else url


def build_context(
    stats: dict[str, Any],
    evidence_path: Path,
    manifest_path: Path | None,
    narrative: dict[str, Any],
) -> dict[str, Any]:
    evidence = load_evidence(evidence_path)
    dp_rows = evidence["逻辑点证据"]
    unit_rows = evidence["变更单元"]
    signal_rows = evidence["信号与问题"]

    metrics = stats["指标"]
    obj = stats["对象"]
    verdict = stats["定性"]
    state = stats["状态分布"]
    tiers = stats["断言档位分布"]
    gap = stats.get("证据缺口", {})
    degrade = stats.get("测试退化", {})

    total = _int(metrics["全部逻辑点"])
    decidable = _int(metrics["可判定逻辑点数"])
    undecidable = _int(metrics["无法判定逻辑点数"])

    def rate(name: str) -> str:
        return _s(metrics[name]["text"])

    assertion_total = sum(_int(v) for v in tiers.values())
    non_discriminating = _int(tiers.get("形状", 0)) + _int(tiers.get("存活", 0))

    attributed = sum(1 for row in dp_rows if "自查：归属成立" in _s(row.get("复核")))
    mismatched = sum(1 for row in dp_rows if "自查：归属不符" in _s(row.get("复核")))

    source = obj.get("证据来源分布", {})
    source_text = "含本地仓 {a} 个单元 / 仅 diff {b} / 无法获取 {c}".format(
        a=_int(source.get("含本地仓", 0)),
        b=_int(source.get("仅 diff", 0)),
        c=_int(source.get("无法获取", 0)),
    )

    density = metrics.get("新增DT密度", {})
    density_value = density.get("value")
    density_text = "不适用" if density_value is None else f"{density_value}"

    # ---- 分布表（位置摘要来自 narrative，其余全部来自 stats）
    summaries = narrative.get("位置摘要") or {}
    distribution = [
        {
            "类型": item["类型"],
            "位置摘要": _s(summaries.get(item["类型"])) or "—",
            "数量文本": item["数量文本"],
            "占比": item["占同类总数"],
            "严重度": item["严重度"],
            "升级理由": item.get("升级理由") or "—",
        }
        for item in stats["分布表"]
    ]

    # ---- Top3 / 问题清单：排序规则确定，内容由 narrative 提供
    top3 = [
        {**item, "序号": index}
        for index, item in enumerate(
            sorted(
                narrative.get("top3") or [],
                key=lambda item: IMPACT_RANK.get(_s(item.get("影响档")), 9),
            ),
            1,
        )
    ]

    def problems(section: str) -> list[dict[str, Any]]:
        items = (narrative.get("维度", {}).get(section) or {}).get("问题") or []
        ordered = sorted(items, key=lambda i: SEVERITY_RANK.get(_s(i.get("严重度")), 9))
        return [
            {
                "序号": index,
                "严重度": _s(i.get("严重度")),
                "类型": _s(i.get("类型")),
                "子类": _s(i.get("子类")),
                "位置": _s(i.get("位置")),
                "标题": _s(i.get("标题")),
                "现象": _s(i.get("现象")),
                "证据原文": _s(i.get("证据原文")),
                "后果": _s(i.get("后果")),
            }
            for index, i in enumerate(ordered, 1)
        ]

    def tips(section: str) -> list[dict[str, str]]:
        items = (narrative.get("维度", {}).get(section) or {}).get("建议") or []
        return [
            {"位置": _s(i.get("位置")), "动作": _s(i.get("动作"))}
            for i in items
            if _s(i.get("位置")) or _s(i.get("动作"))
        ]

    # ---- 2.3 未覆盖清单：编号与影响档来自 narrative，位置/状态/子类反查证据表
    dp_index = {_s(r.get("编号")): r for r in dp_rows}
    uncovered: list[dict[str, Any]] = []
    for item in (narrative.get("维度", {}).get("2.3") or {}).get("未覆盖") or []:
        dp_id = _s(item.get("编号"))
        if dp_id not in dp_index:
            raise RenderError(
                f"narrative 2.3 未覆盖引用了不存在的逻辑点「{dp_id}」；"
                "编号必须逐字取自 evidence.md 的「逻辑点证据」"
            )
        row = dp_index[dp_id]
        dp_state = _s(row.get("状态"))
        if dp_state not in ("无测试", "未断言"):
            raise RenderError(
                f"2.3 未覆盖只列「无测试 / 未断言」的逻辑点，"
                f"而「{dp_id}」的状态是「{dp_state}」；实现被替换请放在 2.2"
            )
        uncovered.append(
            {
                "编号": dp_id,
                "位置": _s(row.get("位置")) or "—",
                "状态": dp_state,
                "子类": "无测试" if dp_state == "无测试" else "分支未断言",
                "严重度": "严重",
                "影响档": _s(item.get("影响档")),
                "现象": _s(item.get("现象")),
                "证据": _s(item.get("证据")) or "—",
                "后果": _s(item.get("后果")),
            }
        )
    uncovered.sort(key=lambda i: IMPACT_RANK.get(i["影响档"], 9))
    for index, item in enumerate(uncovered, 1):
        item["序号"] = index

    # ---- 2.2 被替换清单：同一个替换者合并成一行
    replaced: dict[tuple[str, str], list[str]] = {}
    for row in dp_rows:
        if _s(row.get("状态")) != "实现被替换":
            continue
        key = (_s(row.get("被替换方法")), _s(row.get("断言证据")))
        replaced.setdefault(key, []).append(_s(row.get("编号")))
    replaced_rows = [
        {"逻辑点": "、".join(ids), "方法": method or "—", "替换者": where or "—"}
        for (method, where), ids in replaced.items()
    ]

    # ---- 3.1 表：变更单元 + manifest 汇总
    manifest_by_url: dict[str, dict[str, Any]] = {}
    if manifest_path and manifest_path.is_file():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for item in manifest.get("mrs", []):
            files = item.get("files") or []
            manifest_by_url[_s(item.get("web_url"))] = {
                "文件数": len(files),
                "新增": sum(_int(f.get("additions")) for f in files),
                "删除": sum(_int(f.get("deletions")) for f in files),
                "截断": sum(1 for f in files if f.get("truncated")),
            }
    unit_table = []
    unable_urls, diff_only_urls = [], []
    for row in unit_rows:
        url = _s(row.get("MR 地址"))
        tier = _s(row.get("来源档位"))
        if tier == "无法获取":
            unable_urls.append(url)
        elif tier == "仅 diff":
            diff_only_urls.append(url)
        info = manifest_by_url.get(url, {})
        truncated = _int(info.get("截断"))
        note = f"{truncated} 个文件 diff 被截断" if truncated else "无"
        unit_table.append(
            {
                "MR": _mr_number(url),
                "档位": tier,
                "文件数": str(info.get("文件数", "—")),
                "增删": (
                    f"+{info['新增']} / -{info['删除']}" if info else "—"
                ),
                "备注": note,
            }
        )

    # ---- 3.2 构成表
    composition = []
    for group in stats["变更构成"]["纳入分类"]:
        category = _s(group.get("分类"))
        language = _s(group.get("语言"))
        label = f"{language} {'生产' if category == '生产代码' else '测试'}"
        handling = (
            "受限：只做文件级结论，不枚举逻辑点"
            if language in ("C", "C++")
            else "纳入"
        )
        composition.append(
            {
                "分类": label,
                "数量": f"{_int(group.get('文件数'))} 个文件 / {_int(group.get('新增'))} 行",
                "处理": handling,
            }
        )
    for item in stats["变更构成"]["排除"]:
        composition.append(
            {
                "分类": f"排除：{_s(item.get('分类'))}",
                "数量": f"{_int(item.get('数量'))} 个文件",
                "处理": "排除",
            }
        )
    included_files = _int(stats["变更构成"]["纳入文件数"])
    excluded_files = sum(_int(i.get("数量")) for i in stats["变更构成"]["排除"])
    composition_total = str(included_files + excluded_files)

    # ---- 3.3 矩阵
    matrix = [
        {field: _s(row.get(field)) or "—" for field in MATRIX_FIELDS}
        for row in dp_rows
    ]

    # ---- 3.1 的四类清单
    undecidable_list = _review(dp_rows, state="无法判定", prefix="缺失：")

    def kind_tally(kind: str, unit: str) -> str:
        rows = [r for r in dp_rows if _s(r.get("类型")) == kind]
        if not rows:
            return f"0 {unit}"
        observed = sum(1 for r in rows if _s(r.get("状态")) in ("已断言", "断言偏弱"))
        return f"{len(rows)} {unit}，其中 {observed} 个已断言"

    cross_check = (
        f"全部 {total} − 无法判定 {undecidable} = 可判定 {decidable}；"
        f"{decidable} − {_int(state.get('已断言'))} − {_int(state.get('断言偏弱'))} = "
        f"{_int(state.get('实现被替换')) + _int(state.get('未断言')) + _int(state.get('无测试'))}"
        f" = 实现被替换 {_int(state.get('实现被替换'))} + 未断言 {_int(state.get('未断言'))}"
        f" + 无测试 {_int(state.get('无测试'))}"
    )

    ctx: dict[str, Any] = {
        # 对象
        "检测对象标识": _s(obj.get("对象标识")),
        "对象类型": _s(obj.get("对象类型")),
        "标识": _s(obj.get("对象标识")),
        "原始输入": _s(obj.get("原始输入")),
        "涉及代码仓": _s(obj.get("涉及仓")),
        "关联能力": _s(obj.get("关联能力")) or "—",
        "证据来源分布": source_text,
        # 体量
        "KLOC": _s(metrics.get("变更生产代码KLOC")),
        "新增DT数": _s(metrics.get("新增DT数")),
        "密度数值": density_text,
        # 定性
        "定性": _s(verdict.get("结论")),
        "定性依据": _s(verdict.get("依据")),
        # 1.2
        "未命中类数": _s(stats.get("未命中类数")),
        "断言覆盖率": rate("断言覆盖率"),
        "有效断言率": rate("有效断言率"),
        "实现被替换率": rate("实现被替换率"),
        # 2.1
        "新增用例数": _s(metrics.get("新增DT数")),
        "修改用例数": _s(metrics.get("修改DT数")),
        "既有用例数": _s(metrics.get("既有用例数")),
        "断言总数": str(assertion_total),
        "有效条数": str(_int(tiers.get("有效"))),
        "非有效条数": str(non_discriminating),
        "计入退化条数": str(_int(degrade.get("计入条数"))),
        "提示退化条数": str(_int(degrade.get("提示条数"))),
        "抽查数": str(attributed + mismatched),
        "归属成立数": str(attributed),
        "归属不符数": str(mismatched),
        "无断言用例数": str(_int((stats.get("断言质量") or {}).get("无断言用例数"))),
        "只用交互验证用例数": str(_int((stats.get("断言质量") or {}).get("只用交互验证用例数"))),
        # 2.2
        **{f"类{i}处数": str(_int(stats["Mock 情形分布"].get(f"类{i}"))) for i in range(1, 8)},
        "有被替换": bool(replaced_rows),
        "契约漂移数": str(_int(stats.get("契约漂移数"))),
        # 2.3
        "全部逻辑点": str(total),
        "可判定逻辑点": str(decidable),
        "无法判定逻辑点": str(undecidable),
        "已断言数": str(_int(state.get("已断言"))),
        "断言偏弱数": str(_int(state.get("断言偏弱"))),
        "实现被替换数": str(_int(state.get("实现被替换"))),
        "未断言数": str(_int(state.get("未断言"))),
        "无测试数": str(_int(state.get("无测试"))),
        "受限文件数": _s(metrics.get("C/C++受限文件数")),
        "无逻辑点": total == 0,
        "异常路径细分": kind_tally("异常处理", "个 catch 类逻辑点"),
        "边界比较细分": kind_tally("边界比较", "个"),
        # 3.x
        "证据完整度算式": (
            f"可判定 {decidable} / 全部 {total} = {_s(metrics['证据完整度']['text'])}"
        ),
        "证据缺口行": (
            "证据缺口：不适用（无逻辑点）"
            if total == 0
            else (
                f"证据缺口：{_int(gap.get('数量'))} 个逻辑点无法判定 ｜ "
                f"{_int(gap.get('无法获取单元数'))} 个 MR 无法获取 ｜ "
                f"证据完整度 {_s(metrics['证据完整度']['text'])}"
                f"（{_s(metrics['证据完整度']['档'])}）"
            )
        ),
        "是MR对象": _s(obj.get("对象类型")) == "MR",
        "是SR对象": _s(obj.get("对象类型")) != "MR",
        "算式校验": cross_check,
        "合计文件数": composition_total,
        "无法判定清单": _join(undecidable_list),
        "无法获取清单": _join(unable_urls),
        "仅diff清单": _join(diff_only_urls),
        "受限语言": (
            f"C/C++ 文件 {_int(metrics.get('C/C++受限文件数'))} 个"
            if _int(metrics.get("C/C++受限文件数"))
            else "无"
        ),
        "测试脱靶提示": (
            "有测试变更但全部逻辑点无测试"
            if any(_s(r.get("类型")) == "测试脱靶" for r in signal_rows)
            else ""
        ),
        # narrative
        "偏差声明": narrative.get("偏差声明"),
        "范围一句话": _s(narrative.get("范围一句话")),
        "一句话判断": _s(narrative.get("一句话判断")),
        "2.1总评": _s((narrative.get("维度", {}).get("2.1") or {}).get("总评")),
        "2.2总评": _s((narrative.get("维度", {}).get("2.2") or {}).get("总评")),
        "2.3总评": _s((narrative.get("维度", {}).get("2.3") or {}).get("总评")),
        "3.1取数": _s((narrative.get("3.1") or {}).get("取数")),
        "3.1去重": _s((narrative.get("3.1") or {}).get("去重")),
        "3.1额外限制": _s((narrative.get("3.1") or {}).get("额外限制")),
        # 数据集
        "关联MR清单": [
            {"MR地址": _s(r.get("MR 地址")), "标题": _s(r.get("标题")), "作者": _s(r.get("作者"))}
            for r in unit_rows
        ],
        "分布表": distribution,
        "top3": top3,
        "2.1问题": problems("2.1"),
        "2.1建议": tips("2.1"),
        "2.2问题": problems("2.2"),
        "2.2建议": tips("2.2"),
        "2.2被替换": replaced_rows,
        "2.3未覆盖": uncovered,
        "2.3建议": tips("2.3"),
        "3.1表": unit_table,
        "3.2表": composition,
        "3.3矩阵": matrix,
        # MR 单对象变体
        "MR地址": _s(unit_rows[0].get("MR 地址")) if len(unit_rows) == 1 else "",
        "MR标题": _s(unit_rows[0].get("标题")) if len(unit_rows) == 1 else "",
        "MR作者": _s(unit_rows[0].get("作者")) if len(unit_rows) == 1 else "",
    }

    # 证据完整度低于下限时删掉「一句话判断」
    if metrics["证据完整度"].get("低于下限"):
        ctx["一句话判断"] = ""

    return ctx


# ------------------------------------------------------------------ 校验


def _section(text: str, start: str, end: str | None = None) -> str:
    begin = text.find(start)
    if begin < 0:
        return ""
    stop = text.find(end, begin) if end else len(text)
    return text[begin : stop if stop >= 0 else len(text)]


def _table_rows(block: str) -> int:
    """数 Markdown 表格的数据行（去掉表头与分隔行）。"""
    lines = [line for line in block.splitlines() if line.strip().startswith("|")]
    return max(0, len(lines) - 2)


def check_report(text: str, ctx: dict[str, Any], stats: dict[str, Any]) -> list[str]:
    """回读生成的报告，做确定性校验。返回违规项，空列表表示通过。"""
    problems: list[str] = []

    # 1. 残留
    if "{{" in text or "}}" in text:
        problems.append("报告里残留了未替换的 {{ }}")
    if "<!--" in text or "-->" in text:
        problems.append("报告里残留了 HTML 注释")

    # 2. 3.4 逐字注入
    include_marker = "#### 3.4.1"
    include_tail = "#### 3.4.7"
    if include_marker not in text or include_tail not in text:
        problems.append("3.4 枚举释义没有完整注入（应包含 3.4.1 到 3.4.7）")

    # 3. 数字回读比对
    anchors = {
        "定性": f"DT 质量定性：{ctx['定性']}",
        "三个率": (
            f"断言覆盖率 {ctx['断言覆盖率']} ｜ 有效断言率 {ctx['有效断言率']} ｜ "
            f"实现被替换率 {ctx['实现被替换率']}"
        ),
        "未命中类数": f"其余 {ctx['未命中类数']} 类未命中",
        "证据缺口": ctx["证据缺口行"],
        "3.3 合计": (
            f"全部逻辑点 {ctx['全部逻辑点']} = 已断言 {ctx['已断言数']} + "
            f"断言偏弱 {ctx['断言偏弱数']} + 实现被替换 {ctx['实现被替换数']} + "
            f"未断言 {ctx['未断言数']} + 无测试 {ctx['无测试数']} + "
            f"无法判定 {ctx['无法判定逻辑点']}"
        ),
    }
    for label, needle in anchors.items():
        if needle not in text:
            problems.append(f"{label} 与 stats.json 对不上：报告里找不到「{needle}」")

    # 4. 行数
    matrix_rows = _table_rows(_section(text, "### 3.3", "**合计**"))
    expect_matrix = _int(ctx["全部逻辑点"])
    if matrix_rows != expect_matrix:
        problems.append(f"3.3 矩阵 {matrix_rows} 行，逻辑点应为 {expect_matrix} 个")

    dist_rows = _table_rows(_section(text, "**（1）问题分布表**", "其余 "))
    expect_dist = _int(stats.get("命中类数"))
    if dist_rows != expect_dist:
        problems.append(f"1.2 分布表 {dist_rows} 行，命中类数应为 {expect_dist}")

    # 5. Top3
    top_block = _section(text, "### 1.3", "## 二、")
    top_items = re.findall(r"^\d+\. \*\*`", top_block, flags=re.M)
    if len(top_items) > 3:
        problems.append(f"1.3 列了 {len(top_items)} 条，最多 3 条")
    impacts = re.findall(r"影响：([高中低])", top_block)
    ranks = [IMPACT_RANK.get(i, 9) for i in impacts]
    if ranks != sorted(ranks):
        problems.append("1.3 条目没有按影响档降序排列")

    # 7. 固定骨架
    for heading in ("## 一、整体结论", "## 二、DT 质量评估", "## 三、附录", "### 3.4 枚举释义"):
        if heading not in text:
            problems.append(f"报告缺少固定章节：{heading}")

    return problems


# ------------------------------------------------------------------ 入口


REQUIRED_NARRATIVE = (
    "偏差声明",
    "范围一句话",
    "一句话判断",
    "位置摘要",
    "top3",
    "维度",
    "3.1",
)
SECTION_RULES = {
    "2.1": ("总评", "问题", "建议"),
    "2.2": ("总评", "问题", "建议"),
    "2.3": ("总评", "未覆盖", "建议"),
}


def _validate_narrative(narrative: dict[str, Any]) -> None:
    """判断类字段不许留空；缺字段直接报错，避免装配出一份空壳报告。"""
    if not isinstance(narrative, dict):
        raise RenderError("narrative.json 顶层必须是对象")
    problems = [f"缺少字段「{name}」" for name in REQUIRED_NARRATIVE if name not in narrative]

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

    block31 = narrative.get("3.1")
    if isinstance(block31, dict):
        for field in ("取数", "去重", "额外限制"):
            if field not in block31:
                problems.append(f"缺少字段「3.1.{field}」")
    elif "3.1" in narrative:
        problems.append("narrative.3.1 必须是对象")

    for index, item in enumerate(narrative.get("top3") or [], 1):
        for field in ("位置", "问题", "影响档", "建议"):
            if not _s(item.get(field)):
                problems.append(f"top3 第 {index} 条缺少「{field}」")
        if _s(item.get("影响档")) not in IMPACT_VALUES:
            problems.append(f"top3 第 {index} 条影响档应为 高 / 中 / 低")

    for name in ("2.1", "2.2"):
        items = (sections.get(name) or {}).get("问题") or []
        for index, item in enumerate(items, 1):
            for field in ("严重度", "类型", "子类", "位置", "标题", "现象", "证据原文", "后果"):
                if not _s(item.get(field)):
                    problems.append(f"{name} 第 {index} 条问题缺少「{field}」")
            if _s(item.get("严重度")) not in ("严重", "高"):
                problems.append(f"{name} 第 {index} 条严重度应为 严重 / 高")

    for index, item in enumerate((sections.get("2.3") or {}).get("未覆盖") or [], 1):
        for field in ("编号", "影响档", "现象", "后果"):
            if not _s(item.get(field)):
                problems.append(f"2.3 未覆盖第 {index} 条缺少「{field}」")
        if _s(item.get("影响档")) not in IMPACT_VALUES:
            problems.append(f"2.3 未覆盖第 {index} 条影响档应为 高 / 中 / 低")

    for name in ("2.1", "2.2", "2.3"):
        for index, item in enumerate((sections.get(name) or {}).get("建议") or [], 1):
            if not _s(item.get("动作")):
                problems.append(f"{name} 建议第 {index} 条缺少「动作」")
            if not _s(item.get("位置")):
                problems.append(f"{name} 建议第 {index} 条缺少落点「位置」")

    if problems:
        raise RenderError("narrative.json 不合格：" + "；".join(problems[:8]))


def _tidy(text: str) -> str:
    """收拾块展开留下的空行：表格中间不留空行（会断开表格），连续空行压成一个。"""
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
    manifest_path: Path | None,
    narrative_path: Path,
) -> tuple[str, dict[str, Any], list[str]]:
    """渲染并校验，返回（报告文本、上下文、校验问题）。"""
    if not template.is_file():
        raise RenderError(f"模板不存在：{template}")
    stats = json.loads(stats_path.read_text(encoding="utf-8"))
    narrative = json.loads(narrative_path.read_text(encoding="utf-8"))
    _validate_narrative(narrative)
    ctx = build_context(stats, evidence_path, manifest_path, narrative)

    nodes = _parse(_tokenize(COMMENT.sub("", template.read_text(encoding="utf-8"))))
    missing: list[str] = []
    text = _render_nodes(nodes, [ctx], [template.parent, DEFAULT_REFERENCES], missing)
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
    manifest: Path | None,
    narrative: Path,
    out: Path | None = None,
    check: bool = False,
) -> dict[str, Any]:
    text, ctx, problems = render_report(
        template=template,
        stats_path=stats,
        evidence_path=evidence,
        manifest_path=manifest,
        narrative_path=narrative,
    )
    target = out or evidence.with_name("DT-AUDIT-REPORT.md")
    if check and problems:
        raise RenderError(
            f"报告校验未通过（{len(problems)} 项）：" + "；".join(problems[:10])
        )
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return {
        "报告路径": str(target),
        "校验": {"通过": not problems, "问题": problems},
        "定性": ctx["定性"],
    }

