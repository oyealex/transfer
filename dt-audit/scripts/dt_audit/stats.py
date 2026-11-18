"""证据表统计与一致性校验。

读取 ``.dt-audit/<对象>/evidence.md``，按「问题分类」算出五类判定的数量与占比、
用例侧派生名单与定性结论，写出 ``stats.json``。

这一层**不做任何判断**：只解析固定表头、做算术、查结构矛盾。判断（每个逻辑点归哪
一类、原因是什么）都已在 Skill 里完成，写进了证据表。

定量口径：**分母一律是全部逻辑点**。

    全部逻辑点 = 有效 + 未覆盖 + 无效 + 低价值 + 不确定

定性按序判定：证据不足前置 → 零逻辑点 → 证据不足 → 待改进 → 优秀 → 合格。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Sequence

from .contracts import SCHEMA_VERSION

# ------------------------------------------------------------------ 枚举

#: 问题分类。有效不是问题，但占分母，参与合计核对。
PROBLEM_OK = "有效"
PROBLEMS = ("有效", "未覆盖", "无效", "低价值", "不确定")
DISTRIBUTION = ("未覆盖", "无效", "低价值", "不确定", PROBLEM_OK)

#: 原因枚举。只解释「为什么归这一类」，不计数。
REASONS: dict[str, tuple[str, ...]] = {
    PROBLEM_OK: (),
    "未覆盖": ("无对应用例", "原有用例不再执行"),
    "无效": (
        "无断言",
        "断言没看这个点的结果",
        "只验交互",
        "只测假实现",
        "断言镜像实现",
        "断言被注释",
    ),
    "低价值": ("只判形态", "断言被弱化"),
    "不确定": ("证据不足", "断言归属判不清", "结构不可解析"),
}

#: 方向原因：本次改弱。报告里要写清改掉了哪一行。
DIRECTION_REASONS = ("原有用例不再执行", "断言被注释", "断言被弱化")
DIRECTION_OF = {
    "原有用例不再执行": "未覆盖",
    "断言被注释": "无效",
    "断言被弱化": "低价值",
}

CASE_KINDS = ("新增", "修改", "既有")
SOURCE_TIERS = ("含本地仓", "仅 diff", "无法获取")
SCOPE_CATEGORIES = ("生产代码", "测试代码")
LANGUAGES = ("Java", "Python", "C", "C++")
POINT_SOURCES = ("新增", "修改")
CATEGORY_ORDER = {name: index for index, name in enumerate(SCOPE_CATEGORIES)}
LANGUAGE_ORDER = {name: index for index, name in enumerate(LANGUAGES)}
EXCLUDE_PREFIX = "排除："

# ------------------------------------------------------------------ 定性

VERDICT_GOOD = "优秀"
VERDICT_PASS = "合格"
VERDICT_IMPROVE = "待改进"
VERDICT_INSUFFICIENT = "证据不足"
VERDICTS = (VERDICT_GOOD, VERDICT_PASS, VERDICT_IMPROVE, VERDICT_INSUFFICIENT)

UNCERTAIN_BAD = 0.40  # 不确定超过这个占比判证据不足
COVERAGE_OR_INVALID_BAD = 0.30  # 未覆盖或无效达到这个占比判待改进
EFFECTIVE_BAD = 0.50  # 有效低于这个占比判待改进
EFFECTIVE_GOOD = 0.75  # 优秀要求的最低有效占比
UNCERTAIN_GOOD = 0.10  # 优秀允许的不确定占比上限
EXCELLENT_MIN_POINTS = 8  # 优秀要求的最小逻辑点数

#: 浮点比较的容差，避免 3/10 与 0.30 的二进制误差。
EPS = 1e-9

# ------------------------------------------------------------------ 结构

SECTIONS: dict[str, tuple[str, ...]] = {
    "变更概览": ("项", "值"),
    "变更单元": ("MR 地址", "标题", "作者", "仓", "来源档位"),
    "范围": ("路径", "分类", "语言", "来源档位", "新增", "删除"),
    "用例清单": ("用例", "位置", "变更类型"),
    "逻辑点证据": (
        "编号",
        "位置",
        "条件摘要",
        "来源",
        "覆盖用例",
        "问题",
        "原因",
        "说明",
    ),
}

REQUIRED_OVERVIEW_KEYS = (
    "对象类型",
    "对象标识",
    "变更单元数",
    "涉及仓",
    "含本地仓单元数",
    "仅 diff 单元数",
    "无法获取单元数",
    "变更生产代码行数",
    "生产文件数",
    "纳入文件数",
    "测试文件数",
    "C/C++ 受限文件数",
)

CASE_REF = re.compile(r"(?P<name>[^（()）、,，\s]+)\s*[（(]\s*(?P<where>[^()（）]+?:\d+)\s*[)）]")
POSITION = re.compile(r".+:\d+$")
#: 说明里出现「文件:行号」即可，不要求出现在句尾
POSITION_ANY = re.compile(r"[^\s:：()（）、，,]+:\d+")
POINT_ID = re.compile(r"^[A-Z][A-Z0-9]{0,5}-\d+$")
OBJECT_ID_SR_AR = re.compile(r"^(SR|AR)\d+$")
MR_URL = re.compile(r"^https?://\S+$")


class EvidenceError(Exception):
    """证据表不满足结构或口径要求。"""


# ------------------------------------------------------------------ 解析


def _split_row(line: str) -> list[str]:
    """按未转义的管道符拆一行，还原 ``\\|``。"""
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    cells = re.split(r"(?<!\\)\|", s)
    return [c.replace("\\|", "|").strip() for c in cells]


def _is_separator(cells: Sequence[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r":?-{2,}:?", c.strip()) for c in cells)


def _norm(value: str) -> str:
    v = (value or "").strip()
    return "" if v in ("", "—", "-", "N/A") else v


def _split_ids(value: str) -> list[str]:
    """把「A、B；C」这类多值单元格拆开。

    注意不能按 ``/`` 拆：用例引用形如 ``testA(test/T.java:12)``，路径里带斜杠。
    """
    v = _norm(value)
    if not v:
        return []
    return [p.strip() for p in re.split(r"[、,，；;]+", v) if p.strip()]


def _as_int(value: str, *, where: str, errors: list[str]) -> int:
    v = _norm(value)
    if not v:
        return 0
    try:
        return int(v)
    except ValueError:
        errors.append(f"{where}：应为整数，实际是「{value}」")
        return 0


def _split_sections(text: str) -> dict[str, list[str]]:
    sections: dict[str, list[str]] = {}
    current: str | None = None
    for raw in text.splitlines():
        line = raw.rstrip()
        if line.startswith("## "):
            current = line[3:].strip()
            sections[current] = []
        elif current is not None:
            sections[current].append(line)
    return sections


def _parse_table(lines: Sequence[str]) -> tuple[list[str], list[dict[str, str]]]:
    header: list[str] | None = None
    rows: list[dict[str, str]] = []
    interrupted_at: int | None = None
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped.startswith("|"):
            if header is not None and rows and interrupted_at is None:
                interrupted_at = index
            continue
        if interrupted_at is not None:
            raise EvidenceError(
                f"表格在第 {interrupted_at + 1} 行被非表格行打断，之后又出现表格行"
                f"（第 {index + 1} 行）：表格中间不要插空行或说明文字。原行：{stripped[:80]}"
            )
        cells = _split_row(stripped)
        if header is None:
            header = cells
            continue
        if _is_separator(cells):
            continue
        if len(cells) > len(header):
            raise EvidenceError(
                "有一行的单元格比表头多 "
                f"{len(cells) - len(header)} 个（表头 {len(header)} 列，该行 {len(cells)} 列）："
                f"多半是单元格内出现了未转义的 `|`。自由文本里的管道符必须写成 `\\|`。"
                f"原行：{stripped[:120]}"
            )
        if len(cells) < len(header):
            cells = cells + [""] * (len(header) - len(cells))
        rows.append(dict(zip(header, cells[: len(header)])))
    if header is None:
        raise EvidenceError("缺少表格")
    return header, rows


def load_evidence(path: Path) -> dict[str, Any]:
    """解析证据表，返回各段的表头与行；结构不对直接抛 ``EvidenceError``。"""
    if not path.is_file():
        raise EvidenceError(f"证据表不存在：{path}")

    text = path.read_text(encoding="utf-8")
    sections = _split_sections(text)

    parsed: dict[str, Any] = {}
    problems: list[str] = []
    for name, expected in SECTIONS.items():
        if name not in sections:
            problems.append(f"缺少段：## {name}")
            continue
        try:
            header, rows = _parse_table(sections[name])
        except EvidenceError as exc:
            problems.append(f"## {name}：{exc}")
            continue
        if header != list(expected):
            problems.append(
                f"## {name} 表头不符，期望 {' | '.join(expected)}，实际 {' | '.join(header)}"
            )
        parsed[name] = rows
    if problems:
        raise EvidenceError("；".join(problems))

    overview = {_norm(row.get("项", "")): _norm(row.get("值", "")) for row in parsed["变更概览"]}
    parsed["_overview"] = overview
    return parsed


# ------------------------------------------------------------------ 工具


def _rate(numerator: float, denominator: float) -> dict[str, Any]:
    if not denominator:
        return {"count": numerator, "ratio": None, "text": "不适用"}
    ratio = numerator / denominator
    return {"count": numerator, "ratio": round(ratio, 4), "text": f"{ratio * 100:.1f}%"}


def _share_texts(counts: dict[str, int], total: int) -> dict[str, str]:
    """五类占比，保留一位小数，并用最大余额法让合计恰为 100.0%。"""
    if not total:
        return {}
    raw = {name: counts[name] / total * 1000 for name in PROBLEMS}
    tenths = {name: int(raw[name]) for name in PROBLEMS}
    remainder = 1000 - sum(tenths.values())
    order = sorted(PROBLEMS, key=lambda n: (-(raw[n] - tenths[n]), PROBLEMS.index(n)))
    for name in order[:remainder]:
        tenths[name] += 1
    return {name: f"{tenths[name] / 10:.1f}%" for name in PROBLEMS}


# ------------------------------------------------------------------ 指标


def _point_rows(parsed: dict[str, Any]) -> list[dict[str, str]]:
    return parsed["逻辑点证据"]


def _case_of_ref(text: str) -> list[tuple[str, str]]:
    out: list[tuple[str, str]] = []
    for chunk in _split_ids(text):
        match = CASE_REF.search(chunk)
        if match:
            out.append((match.group("name"), match.group("where")))
    return out


def _aggregate_files(rows: Sequence[dict[str, str]]) -> list[dict[str, Any]]:
    """按文件聚合问题点数，供 1.3 取前 3。"""
    buckets: dict[str, dict[str, Any]] = {}
    for row in rows:
        problem = _norm(row.get("问题", ""))
        if problem == PROBLEM_OK or problem == "不确定":
            continue
        where = _norm(row.get("位置", ""))
        path = where.rsplit(":", 1)[0] if ":" in where else where
        bucket = buckets.setdefault(
            path, {"文件": path, "点数": 0, "未覆盖": 0, "无效": 0, "低价值": 0, "编号": []}
        )
        bucket["点数"] += 1
        bucket[problem] = bucket.get(problem, 0) + 1
        bucket["编号"].append(_norm(row.get("编号", "")))
    return sorted(buckets.values(), key=lambda b: (-b["点数"], b["文件"]))


def _verdict(
    *, total: int, counts: dict[str, int], declared_units: int, unable_units: int
) -> dict[str, Any]:
    """按序判定。返回取值、规则码、依据与参与判定的比例。

    规则码与报告 3.4.3 的「序号」一一对应：R0 前置证据不足、R1 零逻辑点、
    R2 证据不足、R3 待改进、R4 优秀、R5 合格。
    """
    if not declared_units or (unable_units and unable_units >= declared_units):
        return {
            "结论": VERDICT_INSUFFICIENT,
            "规则": "R0",
            "依据": "没有变更单元或全部「无法获取」，不产出质量结论",
            "比例": {},
        }
    if total == 0:
        return {
            "结论": VERDICT_PASS,
            "规则": "R1",
            "依据": "没有可判的逻辑点",
            "比例": {},
        }

    x = counts["不确定"] / total
    u = counts["未覆盖"] / total
    v = counts["无效"] / total
    ok = counts[PROBLEM_OK] / total

    if x > UNCERTAIN_BAD + EPS:
        return {
            "结论": VERDICT_INSUFFICIENT,
            "规则": "R2",
            "依据": f"不确定 {x * 100:.1f}% > {UNCERTAIN_BAD * 100:.0f}%，可见样本不足以定性",
            "比例": {"不确定": _rate(counts["不确定"], total)},
        }
    if u >= COVERAGE_OR_INVALID_BAD - EPS or v >= COVERAGE_OR_INVALID_BAD - EPS or ok < EFFECTIVE_BAD - EPS:
        hits = []
        if u >= COVERAGE_OR_INVALID_BAD - EPS:
            hits.append(f"未覆盖 {u * 100:.1f}% ≥ {COVERAGE_OR_INVALID_BAD * 100:.0f}%")
        if v >= COVERAGE_OR_INVALID_BAD - EPS:
            hits.append(f"无效 {v * 100:.1f}% ≥ {COVERAGE_OR_INVALID_BAD * 100:.0f}%")
        if ok < EFFECTIVE_BAD - EPS:
            hits.append(f"有效 {ok * 100:.1f}% < {EFFECTIVE_BAD * 100:.0f}%")
        return {
            "结论": VERDICT_IMPROVE,
            "规则": "R3",
            "依据": "；".join(hits),
            "比例": {
                "有效": _rate(counts[PROBLEM_OK], total),
                "未覆盖": _rate(counts["未覆盖"], total),
                "无效": _rate(counts["无效"], total),
            },
        }
    if (
        ok >= EFFECTIVE_GOOD - EPS
        and counts["未覆盖"] == 0
        and counts["无效"] == 0
        and x <= UNCERTAIN_GOOD + EPS
        and total >= EXCELLENT_MIN_POINTS
    ):
        return {
            "结论": VERDICT_GOOD,
            "规则": "R4",
            "依据": (
                f"有效 {ok * 100:.1f}% ≥ {EFFECTIVE_GOOD * 100:.0f}%，"
                f"无未覆盖与无效，不确定 {x * 100:.1f}% ≤ {UNCERTAIN_GOOD * 100:.0f}%，"
                f"逻辑点 {total} ≥ {EXCELLENT_MIN_POINTS}"
            ),
            "比例": {"有效": _rate(counts[PROBLEM_OK], total)},
        }
    reasons = []
    if ok < EFFECTIVE_GOOD - EPS:
        reasons.append(f"有效 {ok * 100:.1f}% 未达 {EFFECTIVE_GOOD * 100:.0f}%")
    if counts["未覆盖"]:
        reasons.append(f"有 {counts['未覆盖']} 个点未覆盖")
    if counts["无效"]:
        reasons.append(f"有 {counts['无效']} 个点无效")
    if x > UNCERTAIN_GOOD + EPS:
        reasons.append(f"不确定 {x * 100:.1f}% 偏高")
    if total < EXCELLENT_MIN_POINTS:
        reasons.append(f"逻辑点 {total} 不足 {EXCELLENT_MIN_POINTS}，未评优秀")
    return {
        "结论": VERDICT_PASS,
        "规则": "R5",
        "依据": "；".join(reasons) or "未触发待改进条件",
        "比例": {"有效": _rate(counts[PROBLEM_OK], total)},
    }


# ------------------------------------------------------------------ 主流程


def analyze(path: Path) -> dict[str, Any]:
    """解析、校验、算指标。返回 ``stats.json`` 的完整内容。"""
    parsed = load_evidence(path)
    overview: dict[str, str] = parsed["_overview"]
    errors: list[str] = []
    warnings: list[str] = []

    # ---- 概览必需键
    for key in REQUIRED_OVERVIEW_KEYS:
        if not _norm(overview.get(key, "")):
            errors.append(f"变更概览缺少必需键「{key}」")
    object_type = _norm(overview.get("对象类型", ""))
    if object_type and object_type not in ("SR", "AR", "MR"):
        errors.append(f"变更概览.对象类型 应为 SR / AR / MR，实际「{object_type}」")
    object_id = _norm(overview.get("对象标识", ""))
    if object_id:
        if object_type == "MR":
            if not MR_URL.match(object_id):
                errors.append(f"变更概览.对象标识 为 MR 时应填 MR 地址，实际「{object_id}」")
        elif object_type in ("SR", "AR"):
            if not OBJECT_ID_SR_AR.match(object_id):
                errors.append(f"变更概览.对象标识 应形如 SR1234 / AR77，实际「{object_id}」")
            elif not object_id.startswith(object_type):
                errors.append(
                    f"变更概览.对象标识「{object_id}」与对象类型「{object_type}」不一致"
                )

    declared_units = _as_int(
        overview.get("变更单元数", ""), where="变更概览.变更单元数", errors=errors
    )
    unable_units = _as_int(
        overview.get("无法获取单元数", ""), where="变更概览.无法获取单元数", errors=errors
    )
    declared_prod_lines = _as_int(
        overview.get("变更生产代码行数", ""), where="变更概览.变更生产代码行数", errors=errors
    )
    limited_files = _as_int(
        overview.get("C/C++ 受限文件数", ""), where="变更概览.C/C++ 受限文件数", errors=errors
    )

    # ---- 变更单元
    unit_rows = parsed["变更单元"]
    for idx, row in enumerate(unit_rows, start=1):
        where = f"变更单元第 {idx} 行"
        if not _norm(row.get("MR 地址", "")):
            errors.append(f"{where}：MR 地址为空")
        tier = _norm(row.get("来源档位", ""))
        if tier not in SOURCE_TIERS:
            errors.append(f"{where}：来源档位应为 {' / '.join(SOURCE_TIERS)}，实际「{tier}」")
        for field in ("标题", "作者", "仓"):
            if not _norm(row.get(field, "")):
                warnings.append(f"{where}：{field} 为空，报告 3.2 会留白")
    if declared_units and declared_units != len(unit_rows):
        warnings.append(
            f"变更单元数写的是 {declared_units}，变更单元段实际有 {len(unit_rows)} 行，请核对"
        )

    # ---- 范围
    scope_rows = parsed["范围"]
    included_by_category: dict[tuple[str, str], dict[str, int]] = {}
    kloc_additions = 0
    prod_change_lines = 0
    cpp_files = 0
    for idx, row in enumerate(scope_rows, start=1):
        where = f"范围第 {idx} 行"
        path_text = _norm(row.get("路径", ""))
        if not path_text:
            errors.append(f"{where}：路径为空")
        category = _norm(row.get("分类", ""))
        if category not in SCOPE_CATEGORIES:
            errors.append(f"{where}：分类应为 生产代码 / 测试代码，实际「{category}」")
        language = _norm(row.get("语言", ""))
        if language not in LANGUAGES:
            errors.append(f"{where}：语言应为 {' / '.join(LANGUAGES)}，实际「{language}」")
        tier = _norm(row.get("来源档位", ""))
        if tier not in SOURCE_TIERS:
            errors.append(f"{where}：来源档位应为 {' / '.join(SOURCE_TIERS)}，实际「{tier}」")
        additions = _as_int(row.get("新增", ""), where=f"{where}.新增", errors=errors)
        deletions = _as_int(row.get("删除", ""), where=f"{where}.删除", errors=errors)
        bucket = included_by_category.setdefault(
            (category, language), {"文件数": 0, "新增": 0, "删除": 0}
        )
        bucket["文件数"] += 1
        bucket["新增"] += additions
        bucket["删除"] += deletions
        if category == "生产代码" and language in ("Java", "Python"):
            kloc_additions += additions
            prod_change_lines += additions + deletions
        elif category == "生产代码" and language in ("C", "C++"):
            cpp_files += 1

    prod_files = sum(v["文件数"] for (c, _), v in included_by_category.items() if c == "生产代码")
    test_files = sum(v["文件数"] for (c, _), v in included_by_category.items() if c == "测试代码")
    declared_included = _as_int(
        overview.get("纳入文件数", ""), where="变更概览.纳入文件数", errors=errors
    )
    if declared_included and declared_included != len(scope_rows):
        warnings.append(f"纳入文件数写的是 {declared_included}，范围段实际有 {len(scope_rows)} 行")

    # ---- 用例清单
    case_rows = parsed["用例清单"]
    case_names: dict[str, str] = {}
    for idx, row in enumerate(case_rows, start=1):
        where = f"用例清单第 {idx} 行"
        name = _norm(row.get("用例", ""))
        if not name:
            errors.append(f"{where}：用例为空")
            continue
        if name in case_names:
            errors.append(f"{where}：用例重复「{name}」")
        case_names[name] = where
        position = _norm(row.get("位置", ""))
        if not position:
            errors.append(f"{where}：位置为空")
        elif not POSITION.match(position):
            errors.append(f"{where}：位置应形如「文件:行号」，实际「{position}」")
        kind = _norm(row.get("变更类型", ""))
        if kind not in CASE_KINDS:
            errors.append(f"{where}：变更类型应为 新增 / 修改 / 既有，实际「{kind}」")

    # ---- 逻辑点证据
    point_rows = _point_rows(parsed)
    seen_ids: set[str] = set()
    counts = {name: 0 for name in PROBLEMS}
    for idx, row in enumerate(point_rows, start=1):
        dp_id = _norm(row.get("编号", ""))
        where = f"逻辑点第 {idx} 行" + (f"（{dp_id}）" if dp_id else "")
        if not dp_id:
            errors.append(f"{where}：编号为空")
        elif not POINT_ID.match(dp_id):
            errors.append(f"{where}：编号应形如「模块缩写-序号」，实际「{dp_id}」")
        elif dp_id in seen_ids:
            errors.append(f"{where}：编号重复")
        seen_ids.add(dp_id)

        position = _norm(row.get("位置", ""))
        if not position:
            errors.append(f"{where}：位置为空")
        elif not POSITION.match(position):
            errors.append(f"{where}：位置应形如「文件:行号」，实际「{position}」")

        source = _norm(row.get("来源", ""))
        if source not in POINT_SOURCES:
            errors.append(f"{where}：来源应为 新增 / 修改，实际「{source}」")

        problem = _norm(row.get("问题", ""))
        if problem not in PROBLEMS:
            errors.append(f"{where}：问题应为 {' / '.join(PROBLEMS)}，实际「{problem}」")
            continue
        counts[problem] += 1

        reason = _norm(row.get("原因", ""))
        allowed = REASONS[problem]
        if problem == PROBLEM_OK:
            if reason:
                errors.append(f"{where}：问题是「有效」时原因必须留空，实际「{reason}」")
        elif reason not in allowed:
            errors.append(
                f"{where}：原因是「{reason}」，不属于「{problem}」；"
                f"可选：{' / '.join(allowed)}"
            )

        refs = _case_of_ref(row.get("覆盖用例", ""))
        if problem == "未覆盖":
            if refs:
                errors.append(f"{where}：未覆盖的点不应写覆盖用例，实际「{_norm(row.get('覆盖用例', ''))}」")
        elif problem != "不确定" and not refs:
            errors.append(f"{where}：问题是「{problem}」时必须写出覆盖用例，形如「用例名(文件:行号)」")
        for name, _ in refs:
            if name not in case_names:
                errors.append(f"{where}：覆盖用例「{name}」不在用例清单里")

        note = _norm(row.get("说明", ""))
        if reason in DIRECTION_REASONS:
            if not POSITION_ANY.search(note):
                errors.append(f"{where}：原因是「{reason}」（方向原因），说明里必须写清改动的「文件:行号」")
        if problem in ("无效", "低价值") and refs and not note:
            warnings.append(f"{where}：建议在说明里补一句现象")

    if len(point_rows) != sum(counts.values()):
        errors.append("逻辑点计数与问题分类之和不一致")

    total = len(point_rows)
    case_total = len(case_rows)
    new_cases = sum(1 for row in case_rows if _norm(row.get("变更类型", "")) == "新增")
    modified_cases = sum(1 for row in case_rows if _norm(row.get("变更类型", "")) == "修改")
    existing_cases = sum(1 for row in case_rows if _norm(row.get("变更类型", "")) == "既有")

    # ---- 范围对账：概览里的行数只算新增，与 kloc_additions 同口径
    if total and declared_prod_lines != kloc_additions:
        warnings.append(
            f"变更生产代码行数写的是 {declared_prod_lines}，按范围表累加（仅新增）为 {kloc_additions}"
        )
    declared_test_files = _as_int(
        overview.get("测试文件数", ""), where="变更概览.测试文件数", errors=errors
    )
    if declared_test_files and declared_test_files != test_files:
        warnings.append(f"测试文件数写的是 {declared_test_files}，范围表里测试代码有 {test_files} 行")
    if limited_files != cpp_files:
        warnings.append(
            f"C/C++ 受限文件数写的是 {limited_files}，范围表里 C/C++ 生产文件有 {cpp_files} 个"
        )

    # ---- 定性
    verdict = _verdict(
        total=total,
        counts=counts,
        declared_units=declared_units,
        unable_units=unable_units,
    )

    # ---- 分布表
    shares = _share_texts(counts, total)
    distribution: list[dict[str, Any]] = []
    for name in DISTRIBUTION:
        count = counts[name]
        hits = [row for row in point_rows if _norm(row.get("问题", "")) == name]
        if name == PROBLEM_OK:
            anchor = ""
        else:
            anchor = ""
            if hits:
                first = _norm(hits[0].get("位置", ""))
                anchor = first if len(hits) == 1 else f"{first} 等 {len(hits)} 处"
        distribution.append(
            {
                "类型": name,
                "数量": count,
                "占比": shares.get(name, "0.0%"),
                "占比原值": (count / total) if total else None,
                "位置摘要": anchor,
                "命中": count > 0,
            }
        )
    if total == 0:
        distribution = []

    # ---- 用例侧派生名单
    invalid_cases = [name for name in case_names if _case_invalid(name, point_rows)]
    weak_cases = [name for name in case_names if _case_weak(name, point_rows)]
    touched = {name for row in point_rows for name, _ in _case_of_ref(row.get("覆盖用例", ""))}
    untouched_cases = [name for name in case_names if name not in touched]

    payload: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "对象": {
            "对象类型": object_type,
            "对象标识": object_id,
            "变更单元数": _norm(overview.get("变更单元数", "")),
            "涉及仓": _norm(overview.get("涉及仓", "")),
            "证据来源分布": {
                "含本地仓": _norm(overview.get("含本地仓单元数", "")),
                "仅 diff": _norm(overview.get("仅 diff 单元数", "")),
                "无法获取": _norm(overview.get("无法获取单元数", "")),
            },
        },
        "变更单元": [
            {
                "MR 地址": _norm(row.get("MR 地址", "")),
                "标题": _norm(row.get("标题", "")),
                "作者": _norm(row.get("作者", "")),
                "仓": _norm(row.get("仓", "")),
                "来源档位": _norm(row.get("来源档位", "")),
            }
            for row in unit_rows
        ],
        "变更构成": {
            "纳入文件数": len(scope_rows),
            "纳入分类": [
                {"分类": category, "语言": language, **bucket}
                for (category, language), bucket in sorted(
                    included_by_category.items(),
                    key=lambda kv: (
                        CATEGORY_ORDER.get(kv[0][0], 9),
                        LANGUAGE_ORDER.get(kv[0][1], 9),
                        kv[0],
                    ),
                )
            ],
            "排除": {
                key[len(EXCLUDE_PREFIX) :]: value
                for key, value in overview.items()
                if key.startswith(EXCLUDE_PREFIX)
            },
            "KLOC 口径新增行数": kloc_additions,
        },
        "指标": {
            "变更生产代码行数": declared_prod_lines,
            "生产改动行": prod_change_lines,
            "变更生产代码KLOC": round(kloc_additions / 1000, 3),
            "新增DT数": new_cases,
            "修改DT数": modified_cases,
            "既有用例数": existing_cases,
            "用例总数": case_total,
            "新增DT密度": _density(new_cases, kloc_additions),
            "生产文件数": prod_files,
            "测试文件数": test_files,
            "C/C++受限文件数": cpp_files,
            "全部逻辑点": total,
            **{name: counts[name] for name in PROBLEMS},
        },
        "定性": verdict,
        "问题分布": distribution,
        "缺口文件": _aggregate_files(point_rows),
        "用例名单": {
            "无效": invalid_cases,
            "低价值": weak_cases,
            "未涉及改动": [] if total == 0 else untouched_cases,
        },
        "短路": prod_change_lines == 0,
        "C/C++受限文件数": cpp_files,
        "校验": {"通过": not errors, "错误": errors, "警告": warnings},
        "水位": {
            "不确定判证据不足": UNCERTAIN_BAD,
            "未覆盖或无效判待改进": COVERAGE_OR_INVALID_BAD,
            "有效判待改进": EFFECTIVE_BAD,
            "有效判优秀": EFFECTIVE_GOOD,
            "不确定判优秀上限": UNCERTAIN_GOOD,
            "优秀最少逻辑点": EXCELLENT_MIN_POINTS,
        },
    }
    payload["命中类数"] = sum(
        1 for item in distribution if item["类型"] in ("未覆盖", "无效", "低价值") and item["命中"]
    )
    return payload


def _density(new_cases: int, kloc_additions: int) -> dict[str, Any]:
    if not kloc_additions:
        return {"value": None, "text": "不适用"}
    value = new_cases / (kloc_additions / 1000)
    return {"value": round(value, 2), "text": f"{value:.2f}"}


def _case_invalid(name: str, rows: Sequence[dict[str, str]]) -> bool:
    problems = [
        _norm(row.get("问题", ""))
        for row in rows
        if name in [ref for ref, _ in _case_of_ref(row.get("覆盖用例", ""))]
    ]
    return bool(problems) and all(p == "无效" for p in problems)


def _case_weak(name: str, rows: Sequence[dict[str, str]]) -> bool:
    """低价值用例：覆盖的点里没有「有效」，且至少有一个「低价值」。"""
    problems = [
        _norm(row.get("问题", ""))
        for row in rows
        if name in [ref for ref, _ in _case_of_ref(row.get("覆盖用例", ""))]
    ]
    return bool(problems) and PROBLEM_OK not in problems and "低价值" in problems


def run_stats(*, evidence: Path, out: Path | None = None) -> dict[str, Any]:
    """算完写 ``stats.json``；校验不通过时抛 ``EvidenceError``（文件仍然写出）。"""
    payload = analyze(evidence)
    target = out or evidence.with_name("stats.json")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not payload["校验"]["通过"]:
        raise EvidenceError(
            f"证据表校验未通过（{len(payload['校验']['错误'])} 项），详见 {target}："
            + "；".join(payload["校验"]["错误"][:10])
        )
    return payload
