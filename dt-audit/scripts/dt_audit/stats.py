"""证据表统计与一致性校验。

读取 ``.dt-audit/<对象>/evidence.md``，按 ``scripts/README.md`` 的「报告口径」算出
全部指标、问题分布与占比、严重度升级，并校验合计与交叉关系，写出 ``stats.json``。

这一层**不做任何判断**：只解析固定表头、做算术、查结构矛盾。判断（状态、
断言档位、Mock 类别）都已在 Skill 里完成，写进了证据表。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Sequence

from .contracts import SCHEMA_VERSION

# ------------------------------------------------------------------ 枚举

STATES = ("已断言", "断言偏弱", "实现被替换", "未断言", "无测试", "无法判定")
TIERS = ("有效", "形状", "存活")
TIER_RANK = {"有效": 2, "形状": 1, "存活": 0}
CASE_KINDS = ("新增", "修改", "既有")
SCOPE_VERDICTS = ("纳入", "无法判定")
LANGUAGES = ("Java", "Python", "C", "C++")
CATEGORY_ORDER = {"生产代码": 0, "测试代码": 1}
LANGUAGE_ORDER = {name: index for index, name in enumerate(LANGUAGES)}
SOURCE_TIERS = ("含本地仓", "仅 diff", "无法获取")
MOCK_KINDS = tuple(str(i) for i in range(1, 8))
LOGIC_KINDS = (
    "条件分支",
    "否则分支",
    "三元取值",
    "多路选择",
    "提前返回",
    "异常处理",
    "参数校验",
    "边界比较",
    "兜底分支",
    "不可解析",
)
EXCLUDE_PREFIX = "排除："

# 问题类型：4 类 + 1 个提示。子类只进问题详情，不进分布表。
CLASS_COVERAGE = "场景覆盖不足"
CLASS_ASSERTION = "断言质量低"
CLASS_OFFSET = "测试对象偏移"
CLASS_DEGRADE = "测试退化"
HINT_EVIDENCE = "证据缺口"
PROBLEM_ORDER = (CLASS_COVERAGE, CLASS_ASSERTION, CLASS_OFFSET, CLASS_DEGRADE)

PROBLEM_UNITS = {
    CLASS_COVERAGE: "个逻辑点",
    CLASS_ASSERTION: "个用例",
    CLASS_OFFSET: "个逻辑点",
    CLASS_DEGRADE: "条信号",
}

BASE_SEVERITY = {
    CLASS_COVERAGE: "严重",
    CLASS_ASSERTION: "高",
    CLASS_OFFSET: "高",
    CLASS_DEGRADE: "严重",
}

# 占比分母：逻辑点类一律用「全部逻辑点」，用例类用「新增 + 修改用例」；None 表示不填占比。
DIVISOR = {
    CLASS_COVERAGE: "全部",
    CLASS_ASSERTION: "用例",
    CLASS_OFFSET: "全部",
}

# 状态 → 由它推出的问题类型。状态是唯一来源，问题类型列只是冗余标注。
PROBLEM_OF_STATE = {
    "无测试": CLASS_COVERAGE,
    "未断言": CLASS_COVERAGE,
    "实现被替换": CLASS_OFFSET,
    "无法判定": HINT_EVIDENCE,
    "已断言": "",
    "断言偏弱": "",
}

# 信号表两种类型：测试退化按条计，测试脱靶是对象级提示。
SIGNAL_DEGRADE = CLASS_DEGRADE
SIGNAL_MISS = "测试脱靶"
SIGNAL_KINDS = (SIGNAL_DEGRADE, SIGNAL_MISS)

# Mock 清单只标契约漂移，按处计。
MOCK_PROBLEM = "契约漂移"

# 定性：优秀 / 合格 / 待改进 / 证据不足。数值与 scripts/README.md「定性」同步。
VERDICT_GOOD = "优秀"
VERDICT_PASS = "合格"
VERDICT_IMPROVE = "待改进"
VERDICT_INSUFFICIENT = "证据不足"
VERDICT_BLOCK_RATIO = 0.50
VERDICT_EXCELLENT_RATIO = 0.75
VERDICT_COMPLETENESS_FLOOR = 0.60
VERDICT_MIN_POINTS = 8
VERDICT_DEGRADE_LIMIT = 2  # 计入的测试退化超过这个条数才判待改进

# 判定水位。数值与 scripts/README.md「水位」、references/问题类型.md 保持同步。
EVIDENCE_FLOOR = 0.60
EVIDENCE_GOOD = 0.90
EVIDENCE_PARTIAL = 0.60
GOOD_COVERAGE = 0.70  # 断言覆盖率良好水位
COVER_FLOOR = 0.40  # 断言覆盖率低水位
REPLACED_POINT_FLOOR = 3  # 测试对象偏移升级：被替换逻辑点数
REPLACED_RATE_FLOOR = 0.15  # 测试对象偏移升级：实现被替换率
ASSERTION_CASE_FLOOR = 3  # 断言质量低升级：命中用例数
ASSERTION_CASE_RATE = 0.50  # 断言质量低升级：占新增+修改用例的比例
DENSITY_LOW = 5.0
DENSITY_HIGH = 30.0

# 分布表排序：严重度降序，同严重度按计数单位优先级，再按数量降序。
SEVERITY_RANK = {"严重": 0, "高": 1}
UNIT_RANK = {"个逻辑点": 0, "个用例": 1, "条信号": 2}
DISTRIBUTION_CLASSES = 4  # 场景覆盖不足、断言质量低、测试对象偏移、测试退化；证据缺口单列

# ------------------------------------------------------------------ 结构

REQUIRED_SECTIONS = {
    "变更概览": ["项", "值"],
    "变更单元": ["MR 地址", "标题", "作者", "仓", "来源档位", "首个提交", "末提交"],
    "范围": ["路径", "分类", "语言", "来源档位", "新增", "删除", "结论"],
    "用例清单": ["用例", "位置", "变更类型", "断言数", "只验交互"],
    "断言清单": ["用例", "断言行号", "原文", "档位", "绑定逻辑点"],
    "Mock 清单": ["类型", "位置", "被替换方法", "情形类", "问题类型"],
    "逻辑点证据": [
        "编号",
        "位置",
        "类型",
        "条件摘要",
        "来源",
        "断言随动",
        "触达用例",
        "断言证据",
        "被替换方法",
        "状态",
        "问题类型",
        "复核",
    ],
    "信号与问题": ["类型", "信号", "位置", "引用生产符号", "计入", "说明"],
}

EMPTY_MARKS = ("", "—")

# 变更概览必须逐字提供的键（缺一即报错，避免静默取空把 KLOC 读成 0）
REQUIRED_OVERVIEW_KEYS = (
    "对象类型",
    "对象标识",
    "原始输入",
    "变更单元数",
    "涉及仓",
    "关联能力",
    "含本地仓单元数",
    "仅 diff 单元数",
    "无法获取单元数",
    "变更生产代码行数",
    "生产文件数",
    "纳入文件数",
    "测试文件数",
    "C/C++ 受限文件数",
)


class EvidenceError(Exception):
    """证据表结构或口径校验不通过。"""


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
    return "" if v in EMPTY_MARKS else v


def _split_ids(value: str) -> list[str]:
    """把「A、B / C」这类多值单元格拆开。"""
    v = _norm(value)
    if not v:
        return []
    return [p.strip() for p in re.split(r"[、,，/\s]+", v) if p.strip()]


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
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("|"):
            if header is not None and rows:
                break
            continue
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
    for name, expected in REQUIRED_SECTIONS.items():
        if name not in sections:
            problems.append(f"缺少段：## {name}")
            continue
        try:
            header, rows = _parse_table(sections[name])
        except EvidenceError as exc:
            # 带上原始原因：列数超出时真正的问题是「单元格里有未转义的 |」，
            # 只报「没有表格」会把读者引到错误方向。
            problems.append(f"## {name}：{exc}")
            continue
        if header != expected:
            problems.append(
                f"## {name} 表头不符，期望 {' | '.join(expected)}，实际 {' | '.join(header)}"
            )
        parsed[name] = rows
    if problems:
        raise EvidenceError("；".join(problems))

    overview = {_norm(row.get("项", "")): _norm(row.get("值", "")) for row in parsed["变更概览"]}
    parsed["_overview"] = overview
    return parsed


# ------------------------------------------------------------------ 计算


def _rate(numerator: float, denominator: float) -> dict[str, Any]:
    if not denominator:
        return {"ratio": None, "raw": None, "text": "不适用"}
    ratio = numerator / denominator
    return {"ratio": round(ratio, 4), "raw": ratio, "text": f"{ratio * 100:.1f}%"}


def _grade_completeness(ratio: float | None) -> str:
    if ratio is None:
        return "不适用"
    if ratio >= EVIDENCE_GOOD:
        return "完整"
    if ratio >= EVIDENCE_PARTIAL:
        return "较完整"
    return "不完整"


def _display_ratio(raw: float | None) -> float | None:
    """把比率舍到与报告展示一致的精度（百分比 1 位小数 = 比率 3 位小数）。

    判定必须用这个精度，否则会出现「显示 59.6% 却按 60.0% 判」这类自相矛盾。
    """
    return None if raw is None else round(raw, 3)


def _decide_verdict(
    *,
    total: int,
    new_or_modified_cases: int,
    completeness: dict[str, Any],
    effective_rate: dict[str, Any],
    degrade_signals: int,
    declared_units: int = 0,
    unable_units: int = 0,
    limited_files: int = 0,
    prod_change_lines: int = 0,
) -> dict[str, Any]:
    """按顺序判定 DT 质量定性，命中即停。返回 结论 / 规则 / 依据。"""
    # 判定与展示同精度：报告按「百分比 1 位小数」展示，判定一律先舍到 3 位小数，
    # 避免出现「显示 59.6% 却按 60.0% 判」这种自相矛盾。
    completeness_ratio = _display_ratio(completeness.get("raw"))
    effective = _display_ratio(effective_rate.get("raw"))
    comp_text = completeness.get("text", "不适用")

    # 前置：全部单元取数失败时根本没有证据，不能落「合格」
    if declared_units and unable_units >= declared_units:
        return {
            "结论": VERDICT_INSUFFICIENT,
            "规则": "前置",
            "依据": (
                f"全部 {declared_units} 个变更单元都记为「无法获取」，"
                "没有可分析的内容，不产出质量结论"
            ),
        }

    if total == 0:
        if prod_change_lines == 0:
            note = "本次变更没有生产代码改动（新增 + 删除 0 行），不涉及分支，直接记合格"
            if limited_files:
                note += (
                    f"；另有 C/C++ 受限文件 {limited_files} 个，不据此判断有无行为分支"
                )
            return {"结论": VERDICT_PASS, "规则": "R0", "依据": note}
        if limited_files:
            return {
                "结论": VERDICT_PASS,
                "规则": "R0",
                "依据": (
                    f"未枚举出 Java/Python 逻辑点；另有 C/C++ 受限文件 {limited_files} 个，"
                    "不据此判断有无行为分支"
                ),
            }
        return {
            "结论": VERDICT_PASS,
            "规则": "R0",
            "依据": "无可枚举分支（逻辑点 0 个）",
        }

    if (
        new_or_modified_cases == 0
        and completeness_ratio is not None
        and completeness_ratio < VERDICT_COMPLETENESS_FLOOR
    ):
        return {
            "结论": VERDICT_IMPROVE,
            "规则": "R1",
            "依据": (
                f"没有新增或修改任何用例，且证据完整度 {comp_text} "
                f"< {VERDICT_COMPLETENESS_FLOOR * 100:.0f}%，无法确认既有用例是否覆盖"
            ),
        }

    if completeness_ratio is not None and completeness_ratio < VERDICT_COMPLETENESS_FLOOR:
        return {
            "结论": VERDICT_INSUFFICIENT,
            "规则": "R2",
            "依据": f"证据完整度 {comp_text} < {VERDICT_COMPLETENESS_FLOOR * 100:.0f}%，可见样本不足以定性",
        }

    blocks: list[str] = []
    if effective is not None and effective < VERDICT_BLOCK_RATIO:
        blocks.append(f"有效断言率 {effective_rate['text']} < {VERDICT_BLOCK_RATIO * 100:.0f}%")
    if degrade_signals > VERDICT_DEGRADE_LIMIT:
        blocks.append(f"测试退化 {degrade_signals} 条 > {VERDICT_DEGRADE_LIMIT}")
    if blocks:
        return {"结论": VERDICT_IMPROVE, "规则": "R3", "依据": "；".join(blocks)}

    gaps: list[str] = []
    if effective is None or effective < VERDICT_EXCELLENT_RATIO:
        gaps.append(f"有效断言率 {effective_rate['text']} < {VERDICT_EXCELLENT_RATIO * 100:.0f}%")
    if total < VERDICT_MIN_POINTS:
        gaps.append(f"逻辑点 {total} 个 < {VERDICT_MIN_POINTS}")
    if degrade_signals:
        gaps.append(f"测试退化 {degrade_signals} 条")
    if gaps:
        return {
            "结论": VERDICT_PASS,
            "规则": "R5",
            "依据": "；".join(gaps) + "；未触发待改进条件",
        }

    return {
        "结论": VERDICT_GOOD,
        "规则": "R4",
        "依据": (
            f"有效断言率 {effective_rate['text']}、逻辑点 {total} 个、"
            f"无测试退化，三项均达标"
        ),
    }


def _distribution_table(distribution: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """1.2 分布表用的行：只取命中项，按模板规定排序。"""
    hits = [item for item in distribution if item["命中"]]
    return sorted(
        hits,
        key=lambda item: (
            SEVERITY_RANK.get(item["严重度"], 9),
            UNIT_RANK.get(item["单位"], 9),
            -item["数量"],
        ),
    )


def analyze(path: Path) -> dict[str, Any]:
    parsed = load_evidence(path)
    overview: dict[str, str] = parsed["_overview"]

    errors: list[str] = []
    warnings: list[str] = []

    # ---- 概览必需键（缺键静默取 0 会把 KLOC 读成 0，必须报错）
    for key in REQUIRED_OVERVIEW_KEYS:
        if not _norm(overview.get(key, "")):
            errors.append(f"变更概览缺少必需键「{key}」")
    object_type = _norm(overview.get("对象类型", ""))
    if object_type and object_type not in ("SR", "AR", "MR"):
        errors.append(f"变更概览.对象类型 应为 SR / AR / MR，实际「{object_type}」")

    # ---- 概览
    declared_row_count = _as_int(
        overview.get("变更生产代码行数", ""), where="变更概览.变更生产代码行数", errors=errors
    )
    limited_files = _as_int(
        overview.get("C/C++ 受限文件数", ""), where="变更概览.C/C++ 受限文件数", errors=errors
    )

    # ---- 变更单元（MR 级）
    unit_rows = parsed["变更单元"]
    for idx, row in enumerate(unit_rows, start=1):
        where = f"变更单元第 {idx} 行"
        if not _norm(row.get("MR 地址", "")):
            errors.append(f"{where}：MR 地址为空")
        tier = _norm(row.get("来源档位", ""))
        if tier not in SOURCE_TIERS:
            errors.append(f"{where}：来源档位应为 {('/'.join(SOURCE_TIERS))}，实际「{tier}」")
        # 报告 1.1 会原样列出这些字段；缺了就是空白格，按 warning 提示补全
        for field in ("标题", "作者", "仓", "首个提交", "末提交"):
            if not _norm(row.get(field, "")):
                warnings.append(f"{where}：{field} 为空，报告 1.1 会留白")
    declared_units = _as_int(
        overview.get("变更单元数", ""), where="变更概览.变更单元数", errors=errors
    )
    if declared_units and declared_units != len(unit_rows):
        warnings.append(
            f"变更单元数写的是 {declared_units}，变更单元段实际有 {len(unit_rows)} 行，请核对"
        )
    unable_units = _as_int(
        overview.get("无法获取单元数", ""), where="变更概览.无法获取单元数", errors=errors
    )
    if unable_units and declared_units and unable_units >= declared_units:
        warnings.append(
            "全部变更单元都是「无法获取」，不应产出质量结论；请按 SKILL 步骤 1 终止流程"
        )

    # ---- 范围（只列纳入的文件；排除项在概览里按分类计数）
    scope_rows = parsed["范围"]
    included_by_category: dict[tuple[str, str], dict[str, int]] = {}
    kloc_additions = 0
    prod_change_lines = 0
    for idx, row in enumerate(scope_rows, start=1):
        where = f"范围第 {idx} 行"
        if not _norm(row.get("路径", "")):
            errors.append(f"{where}：路径为空")
        tier = _norm(row.get("来源档位", ""))
        if tier not in SOURCE_TIERS:
            errors.append(f"{where}：来源档位应为 {('/'.join(SOURCE_TIERS))}，实际「{tier}」")
        category = _norm(row.get("分类", ""))
        if not category:
            errors.append(f"{where}：分类为空，每个纳入文件都必须有分类")
        language = _norm(row.get("语言", ""))
        if language not in LANGUAGES:
            errors.append(f"{where}：语言应为 {('/'.join(LANGUAGES))}，实际「{language}」")
        verdict = _norm(row.get("结论", ""))
        if verdict not in SCOPE_VERDICTS:
            errors.append(f"{where}：结论应为 {('/'.join(SCOPE_VERDICTS))}，实际「{verdict}」")
        additions = _as_int(_norm(row.get("新增", "")) or "0", where=f"{where}.新增", errors=errors)
        deletions = _as_int(_norm(row.get("删除", "")) or "0", where=f"{where}.删除", errors=errors)
        if category == "生产代码" and language in ("Java", "Python"):
            kloc_additions += additions
            prod_change_lines += additions + deletions
        bucket = included_by_category.setdefault(
            (category or "未分类", language or "未标注"),
            {"文件数": 0, "新增": 0, "删除": 0},
        )
        bucket["文件数"] += 1
        bucket["新增"] += additions
        bucket["删除"] += deletions

    if (declared_row_count or kloc_additions) and declared_row_count != kloc_additions:
        warnings.append(
            f"变更生产代码行数写的是 {declared_row_count}，按范围表（生产代码 + Java/Python）累加得 "
            f"{kloc_additions}，请核对"
        )
    # 指标以范围表累加值为准：概览是申报值，只用于对账
    row_count = kloc_additions

    # 生产/测试文件数与范围表对账（两个键只作对账用，不进报告正文）
    declared_prod = _as_int(
        overview.get("生产文件数", ""), where="变更概览.生产文件数", errors=errors
    )
    declared_test = _as_int(
        overview.get("测试文件数", ""), where="变更概览.测试文件数", errors=errors
    )
    actual_prod = sum(
        counts["文件数"]
        for (category, _language), counts in included_by_category.items()
        if category == "生产代码"
    )
    actual_test = sum(
        counts["文件数"]
        for (category, _language), counts in included_by_category.items()
        if category == "测试代码"
    )
    if declared_prod and declared_prod != actual_prod:
        warnings.append(
            f"生产文件数写的是 {declared_prod}，范围表里分类为生产代码的有 {actual_prod} 行"
        )
    if declared_test and declared_test != actual_test:
        warnings.append(
            f"测试文件数写的是 {declared_test}，范围表里分类为测试代码的有 {actual_test} 行"
        )

    actual_limited = sum(
        counts["文件数"]
        for (_category, language), counts in included_by_category.items()
        if language in ("C", "C++")
    )
    if limited_files and limited_files != actual_limited:
        warnings.append(
            f"C/C++ 受限文件数写的是 {limited_files}，"
            f"范围表里语言为 C/C++ 的有 {actual_limited} 行"
        )

    excluded = [
        {
            "分类": key[len(EXCLUDE_PREFIX) :],
            "数量": _as_int(value, where=f"变更概览.{key}", errors=errors),
        }
        for key, value in overview.items()
        if key.startswith(EXCLUDE_PREFIX) and len(key) > len(EXCLUDE_PREFIX)
    ]
    declared_included = _as_int(
        overview.get("纳入文件数", ""), where="变更概览.纳入文件数", errors=errors
    )
    if declared_included and declared_included != len(scope_rows):
        warnings.append(
            f"纳入文件数写的是 {declared_included}，范围段实际有 {len(scope_rows)} 行，请核对"
        )

    # ---- 用例
    case_rows = parsed["用例清单"]
    case_kind_count = {kind: 0 for kind in CASE_KINDS}
    case_kind_of: dict[str, str] = {}
    case_names: set[str] = set()
    new_cases = modified_cases = 0
    case_assertions: dict[str, int] = {}
    case_interaction: dict[str, str] = {}
    for idx, row in enumerate(case_rows, start=1):
        where = f"用例清单第 {idx} 行"
        name = _norm(row.get("用例", ""))
        if not name:
            errors.append(f"{where}：用例名为空")
        if name in case_names:
            errors.append(f"{where}：用例「{name}」重复")
        case_names.add(name)
        kind = _norm(row.get("变更类型", ""))
        if kind not in CASE_KINDS:
            errors.append(f"{where}：变更类型应为 {('/'.join(CASE_KINDS))}，实际「{kind}」")
            continue
        case_kind_count[kind] += 1
        case_kind_of[name] = kind
        assertions = _as_int(_norm(row.get("断言数", "")) or "0", where=f"{where}.断言数", errors=errors)
        interaction_only = _norm(row.get("只验交互", ""))
        if interaction_only not in ("", "是", "否"):
            errors.append(f"{where}：只验交互只填「是」「否」或留空，实际「{interaction_only}」")
        case_assertions[name] = assertions
        case_interaction[name] = interaction_only
    new_cases = case_kind_count["新增"]
    modified_cases = case_kind_count["修改"]
    case_total = new_cases + modified_cases

    # ---- 断言
    # 一条断言的身份是「用例 + 断言行号」：同一身份只计一次，档位取最高档。
    # 这样无论写成一行绑多个逻辑点、还是拆成多行，有效断言条数都相同。
    assertion_rows = parsed["断言清单"]
    assertion_tier: dict[tuple[str, str], str] = {}
    assertion_bindings: list[tuple[str, list[str]]] = []
    for idx, row in enumerate(assertion_rows, start=1):
        where = f"断言清单第 {idx} 行"
        tier = _norm(row.get("档位", ""))
        case = _norm(row.get("用例", ""))
        line_no = _norm(row.get("断言行号", ""))
        assertion_bindings.append((where, _split_ids(row.get("绑定逻辑点", ""))))
        if not case:
            errors.append(f"{where}：用例为空")
        elif case not in case_names:
            errors.append(f"{where}：用例「{case}」不在用例清单里")
        elif case_kind_of.get(case) == "既有":
            errors.append(
                f"{where}：用例「{case}」是既有用例，其断言不进断言清单"
                "（既有断言可在步骤 3 支撑状态，但不进有效断言条数）"
            )
        if not line_no:
            errors.append(f"{where}：断言行号为空")
        bindings = _split_ids(row.get("绑定逻辑点", ""))
        if not tier:
            # 未绑定逻辑点的断言没有对账对象，档位允许留空，并且不计入档位分布
            if bindings:
                errors.append(f"{where}：已绑定逻辑点，档位不能为空")
            continue
        if tier not in TIERS:
            errors.append(f"{where}：档位应为 {('/'.join(TIERS))}，实际「{tier}」")
            continue
        if not case or not line_no:
            continue
        key = (case, line_no)
        previous = assertion_tier.get(key)
        if previous is None or TIER_RANK[tier] > TIER_RANK[previous]:
            assertion_tier[key] = tier
    tier_count = {tier: 0 for tier in TIERS}
    for tier in assertion_tier.values():
        tier_count[tier] += 1
    assertion_total = len(assertion_tier)

    # ---- Mock
    mock_rows = parsed["Mock 清单"]
    mock_kind_count = {kind: 0 for kind in MOCK_KINDS}
    contract_drifts = 0
    for idx, row in enumerate(mock_rows, start=1):
        where = f"Mock 清单第 {idx} 行"
        kind = _norm(row.get("情形类", ""))
        if kind not in MOCK_KINDS:
            errors.append(f"{where}：情形类应为 1–7，实际「{kind}」")
        else:
            mock_kind_count[kind] += 1
        problem = _norm(row.get("问题类型", ""))
        if problem and problem != MOCK_PROBLEM:
            errors.append(
                f"{where}：问题类型只填「{MOCK_PROBLEM}」或留空，实际「{problem}」"
            )
        if problem == MOCK_PROBLEM:
            contract_drifts += 1

    # ---- 逻辑点
    dp_rows = parsed["逻辑点证据"]
    state_count = {state: 0 for state in STATES}
    dp_ids: set[str] = set()
    for idx, row in enumerate(dp_rows, start=1):
        where = f"逻辑点证据第 {idx} 行"
        dp_id = _norm(row.get("编号", ""))
        if not dp_id:
            errors.append(f"{where}：编号为空")
        elif dp_id in dp_ids:
            errors.append(f"{where}：编号「{dp_id}」重复")
        dp_ids.add(dp_id)

        kind = _norm(row.get("类型", ""))
        if kind not in LOGIC_KINDS:
            errors.append(
                f"{where}（{dp_id}）：类型应为 {(' / '.join(LOGIC_KINDS))}，实际「{kind}」"
            )

        state = _norm(row.get("状态", ""))
        if state not in STATES:
            errors.append(f"{where}（{dp_id}）：状态应为 {('/'.join(STATES))}，实际「{state}」")
            continue
        state_count[state] += 1

        expected_problem = PROBLEM_OF_STATE[state]
        problem = _norm(row.get("问题类型", ""))
        if problem and problem != expected_problem:
            errors.append(
                f"{where}（{dp_id}）：状态「{state}」对应的问题类型应是"
                f"「{expected_problem or '空'}」，实际「{problem}」"
            )

        reach = _norm(row.get("触达用例", ""))
        evidence = _norm(row.get("断言证据", ""))
        replaced_method = _norm(row.get("被替换方法", ""))
        review = _norm(row.get("复核", ""))

        if state == "无测试" and reach:
            errors.append(f"{where}（{dp_id}）：状态是无测试，但触达用例写了「{reach}」")
        if state in ("已断言", "断言偏弱", "未断言") and not reach:
            errors.append(f"{where}（{dp_id}）：状态是「{state}」，触达用例不能为空")
        if state in ("已断言", "断言偏弱") and not evidence:
            errors.append(
                f"{where}（{dp_id}）：状态是「{state}」，断言证据必须写「用例 + 断言行号 + 读到的产出项」"
            )
        if state == "实现被替换":
            if not replaced_method:
                errors.append(
                    f"{where}（{dp_id}）：状态是实现被替换，「被替换方法」不能为空"
                )
            if not reach:
                errors.append(
                    f"{where}（{dp_id}）：状态是实现被替换，至少要有一条覆盖用例；"
                    "零覆盖应落「无测试」"
                )
        if state == "无法判定" and "缺失：" not in review:
            errors.append(
                f"{where}（{dp_id}）：状态是无法判定，「复核」列必须写 `缺失：…` 说明缺什么"
            )

        if reach:
            for name in re.split(r"[、,，;；]\s*", reach):
                if name and name not in case_names:
                    errors.append(
                        f"{where}（{dp_id}）：触达用例「{name}」不在用例清单里"
                    )
        for part in re.split(r"[；;]", review):
            part = part.strip()
            if part and not part.startswith(("随动：", "缺失：", "自查：")):
                errors.append(
                    f"{where}（{dp_id}）：复核列只能用 `随动：` / `缺失：` / `自查：` 前缀，"
                    f"实际「{part[:30]}」"
                )
        if state in ("已断言", "断言偏弱") and evidence:
            if not any(name in evidence for name in case_names):
                errors.append(
                    f"{where}（{dp_id}）：断言证据必须包含用例清单里的用例名"
                )
            elif not re.search(r":\d+", evidence):
                errors.append(
                    f"{where}（{dp_id}）：断言证据必须带断言行号（形如 `文件:行号`）"
                )

        source = _norm(row.get("来源", ""))
        if source not in ("新增", "修改"):
            errors.append(f"{where}（{dp_id}）：来源应为 新增 / 修改，实际「{source}」")
        follow = _norm(row.get("断言随动", ""))
        if source == "修改":
            if follow not in ("是", "否"):
                errors.append(
                    f"{where}（{dp_id}）：来源是修改，断言随动应填「是」或「否」，实际「{follow}」"
                )
            elif follow == "否":
                if "随动：" not in review:
                    errors.append(
                        f"{where}（{dp_id}）：断言未随实现变更，「复核」列必须写 `随动：…` 理由"
                    )
                elif state in ("已断言", "断言偏弱"):
                    warnings.append(f"{dp_id}：断言未随实现变更，已按复核列说明放行")
        elif follow:
            errors.append(f"{where}（{dp_id}）：来源是新增，断言随动应留空，实际「{follow}」")

    # 断言绑定的逻辑点必须真实存在（逻辑点表在这一段之后，所以这里才校验）
    for where, bindings in assertion_bindings:
        for dp_id in bindings:
            if dp_id not in dp_ids:
                errors.append(f"{where}：绑定逻辑点「{dp_id}」不在逻辑点证据里")

    # ---- 信号
    signal_rows = parsed["信号与问题"]
    degrade_signals = 0  # 计入 = 是
    degrade_hints = 0  # 计入 = 否，只作提示
    miss_signals = 0
    seen_signals: set[tuple[str, str]] = set()
    for idx, row in enumerate(signal_rows, start=1):
        where = f"信号与问题第 {idx} 行"
        kind = _norm(row.get("类型", ""))
        if kind not in SIGNAL_KINDS:
            errors.append(
                f"{where}：类型只填「{SIGNAL_DEGRADE}」或「{SIGNAL_MISS}」，实际「{kind}」"
            )
            continue
        if kind == SIGNAL_MISS:
            miss_signals += 1
            continue
        signal = _norm(row.get("信号", ""))
        position = _norm(row.get("位置", ""))
        if not signal:
            errors.append(f"{where}：测试退化要填「信号」名，如 断言弱化 / 测试文件删除 / 新增禁用")
        if not position:
            errors.append(f"{where}：位置为空")
        if signal and position:
            key = (position, signal)
            if key in seen_signals:
                errors.append(f"{where}：同一位置同一信号重复登记（{position} / {signal}）")
            seen_signals.add(key)
        if not _norm(row.get("引用生产符号", "")):
            errors.append(
                f"{where}：要填「引用生产符号」，三段计入条件的第 2 段靠它落点"
            )
        counted = _norm(row.get("计入", ""))
        if counted not in ("是", "否"):
            errors.append(f"{where}：测试退化的「计入」应填 是 或 否，实际「{counted}」")
        elif counted == "是":
            degrade_signals += 1
        else:
            degrade_hints += 1
            if not _norm(row.get("说明", "")):
                errors.append(f"{where}：计入=否 时「说明」要写清缺了哪一段要件")

    # ---- 逻辑点合计
    total = len(dp_rows)
    undecidable = state_count["无法判定"]
    decidable = total - undecidable
    # 步骤 4 的抽样清单：3 个已断言/断言偏弱 + 全部未断言，机械可算
    positive_ids = [
        _norm(row.get("编号", ""))
        for row in dp_rows
        if _norm(row.get("状态", "")) in ("已断言", "断言偏弱")
    ]
    unasserted_ids = [
        _norm(row.get("编号", ""))
        for row in dp_rows
        if _norm(row.get("状态", "")) == "未断言"
    ]
    sample_ids = positive_ids[:3] + unasserted_ids
    sampling = {
        "数量": len(sample_ids),
        "编号": sample_ids,
        "说明": "3 个已断言 / 断言偏弱项 + 全部未断言项",
    }
    if total and sum(state_count.values()) != total:
        errors.append(
            f"逻辑点状态合计 {sum(state_count.values())} 与总行数 {total} 不一致"
        )
    unnoticed = state_count["实现被替换"] + state_count["无测试"] + state_count["未断言"]
    if decidable != state_count["已断言"] + state_count["断言偏弱"] + unnoticed:
        errors.append(
            "可判定逻辑点数与各状态之和不符："
            f"{decidable} != 已断言 {state_count['已断言']} + 断言偏弱 {state_count['断言偏弱']}"
            f" + 未被断言 {unnoticed}"
        )
    if miss_signals and state_count["无测试"] != total:
        errors.append("测试脱靶命中时，全部逻辑点应都是「无测试」；当前不是，请核对")
    if miss_signals and new_cases + modified_cases == 0:
        errors.append("测试脱靶要求本对象有新增或修改用例；当前一个都没有")

    # ---- 指标
    # 三个率与证据完整度共用同一个分母「全部逻辑点」：核心要看护的是业务代码里
    # 所有需要断言的分支，读不到的那部分不能从分母里消失，否则会虚高。
    thousand_lines = row_count / 1000.0
    density: dict[str, Any]
    if thousand_lines:
        density_value = new_cases / thousand_lines
        density = {"value": round(density_value, 1), "text": f"{density_value:.1f} 个/KLOC"}
    else:
        density = {"value": None, "text": "不适用"}

    coverage_rate = _rate(state_count["已断言"] + state_count["断言偏弱"], total)
    effective_rate = _rate(state_count["已断言"], total)
    replaced_rate = _rate(state_count["实现被替换"], total)
    completeness_ratio = (decidable / total) if total else None
    completeness_graded = _display_ratio(completeness_ratio)
    completeness = {
        "ratio": round(completeness_ratio, 4) if completeness_ratio is not None else None,
        "raw": completeness_ratio,
        "text": f"{completeness_ratio * 100:.1f}%" if completeness_ratio is not None else "不适用",
        "档": _grade_completeness(completeness_graded),
        "低于下限": bool(
            completeness_graded is not None and completeness_graded < EVIDENCE_FLOOR
        ),
    }

    # 三个率共用分母，必然有这条链：有效断言率 ≤ 断言覆盖率 ≤ 证据完整度
    for name, value in (
        ("有效断言率", effective_rate["raw"]),
        ("断言覆盖率", coverage_rate["raw"]),
    ):
        if value is not None and completeness_ratio is not None and value > completeness_ratio + 1e-9:
            errors.append(f"{name} 不能高于证据完整度（分母都是全部逻辑点）")

    # 三个率共用分母，只有「全部逻辑点 = 0」才不适用；可判定为 0 时照常给 0.0%，
    # 由 R2 的「证据不足」兜底，不再单独抹成「不适用」。
    metrics = {
        "变更生产代码行数": row_count,
        "变更生产代码KLOC": round(thousand_lines, 3),
        "新增DT数": new_cases,
        "修改DT数": modified_cases,
        "既有用例数": case_kind_count["既有"],
        "用例总数": case_total,
        "新增DT密度": density,
        "断言总数": assertion_total,
        "生产改动行": prod_change_lines,
        "全部逻辑点": total,
        "可判定逻辑点数": decidable,
        "无法判定逻辑点数": undecidable,
        "断言覆盖率": coverage_rate,
        "有效断言率": effective_rate,
        "实现被替换率": replaced_rate,
        "证据完整度": completeness,
        "C/C++受限文件数": limited_files,
    }

    verdict = _decide_verdict(
        total=total,
        new_or_modified_cases=new_cases + modified_cases,
        prod_change_lines=prod_change_lines,
        completeness=completeness,
        effective_rate=effective_rate,
        degrade_signals=degrade_signals,
        declared_units=declared_units,
        unable_units=unable_units,
        limited_files=limited_files,
    )

    # ---- 问题计数
    # 断言质量低只算「触达了本次逻辑点」的用例：与本次改动无关的新增测试不进结论。
    # 逻辑点为 0 时 touched 为空，用例类与信号类命中自然归零，不会出现「合格 + 命中」并存。
    touched_cases: set[str] = set()
    for row in dp_rows:
        for name in re.split(r"[、,，;；]\s*", _norm(row.get("触达用例", ""))):
            if name:
                touched_cases.add(name)
    b1 = sum(
        1
        for name, kind in case_kind_of.items()
        if kind in ("新增", "修改")
        and name in touched_cases
        and case_assertions.get(name, 0) == 0
    )
    b3 = sum(
        1
        for name, kind in case_kind_of.items()
        if kind in ("新增", "修改")
        and name in touched_cases
        and case_interaction.get(name) == "是"
    )
    effective_per_case: dict[str, int] = {}
    for (case_name, _line_no), tier in assertion_tier.items():
        if tier == "有效":
            effective_per_case[case_name] = effective_per_case.get(case_name, 0) + 1
    weak_cases = [
        name
        for name, kind in case_kind_of.items()
        if kind in ("新增", "修改")
        and name in touched_cases
        and effective_per_case.get(name, 0) == 0
    ]
    degrade_signals_eff = degrade_signals if total else 0
    degrade_hints_eff = degrade_hints if total else 0

    counts = {
        CLASS_COVERAGE: state_count["无测试"] + state_count["未断言"],
        CLASS_ASSERTION: len(weak_cases),
        CLASS_OFFSET: state_count["实现被替换"],
        CLASS_DEGRADE: degrade_signals_eff,
    }
    notes = {
        CLASS_COVERAGE: "另有 1 个对象全部脱靶" if miss_signals else "",
        CLASS_ASSERTION: "",
        CLASS_OFFSET: f"另有 {contract_drifts} 处契约漂移" if contract_drifts else "",
        CLASS_DEGRADE: "",
    }
    details = {
        CLASS_COVERAGE: f"无测试 {state_count['无测试']}、分支未断言 {state_count['未断言']}",
        CLASS_ASSERTION: (
            f"无断言用例 {b1}、只用交互验证 {b3}、"
            f"仅形状或存活档断言 {tier_count['形状'] + tier_count['存活']} 条"
        ),
        CLASS_OFFSET: (
            f"实现被替换 {state_count['实现被替换']}"
            + (f"、契约漂移 {contract_drifts} 处" if contract_drifts else "")
        ),
        CLASS_DEGRADE: (
            f"计入 {degrade_signals_eff} 条、仅提示 {degrade_hints_eff} 条"
            if (degrade_signals_eff or degrade_hints_eff)
            else ""
        ),
    }
    denominators = {"全部": total, "用例": case_total}

    distribution: list[dict[str, Any]] = []
    for problem_cls in PROBLEM_ORDER:
        count = counts[problem_cls]
        divisor_key = DIVISOR.get(problem_cls)
        if divisor_key is None:
            share = None
        else:
            denominator = denominators[divisor_key]
            share = _rate(count, denominator) if denominator else None
        severity = BASE_SEVERITY[problem_cls]
        upgrade: str | None = None
        if problem_cls == CLASS_ASSERTION and count and case_total:
            case_share = count / case_total
            if count >= ASSERTION_CASE_FLOOR and case_share >= ASSERTION_CASE_RATE:
                severity, upgrade = "严重", (
                    f"命中 {count} 个用例 ≥ {ASSERTION_CASE_FLOOR}，"
                    f"且占新增/修改用例 {case_share * 100:.1f}% ≥ {ASSERTION_CASE_RATE * 100:.0f}%"
                )
        if problem_cls == CLASS_OFFSET and counts[CLASS_OFFSET] >= REPLACED_POINT_FLOOR:
            if replaced_rate.get("raw") is not None and replaced_rate["raw"] >= REPLACED_RATE_FLOOR:
                severity, upgrade = "严重", (
                    f"被替换 {counts[CLASS_OFFSET]} 个 ≥ {REPLACED_POINT_FLOOR}，"
                    f"实现被替换率 {replaced_rate['text']} ≥ {REPLACED_RATE_FLOOR * 100:.0f}%"
                )
        quantity = f"{count} {PROBLEM_UNITS[problem_cls]}"
        if notes[problem_cls]:
            quantity += f"（{notes[problem_cls]}）"
        distribution.append(
            {
                "类型": problem_cls,
                "数量": count,
                "单位": PROBLEM_UNITS[problem_cls],
                "数量文本": quantity,
                "明细": details[problem_cls],
                "占同类总数": share["text"] if share else "—",
                "基础严重度": BASE_SEVERITY[problem_cls],
                "严重度": severity,
                "升级理由": upgrade,
                "命中": count > 0,
            }
        )

    # 零逻辑点：问题分布留空（README「零值的唯一口径」），否则会出现「合格」与命中问题并存
    if total == 0:
        distribution.clear()

    payload: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "对象": {
            "对象类型": overview.get("对象类型", ""),
            "对象标识": overview.get("对象标识", ""),
            "原始输入": overview.get("原始输入", ""),
            "变更单元数": overview.get("变更单元数", ""),
            "涉及仓": overview.get("涉及仓", ""),
            "关联能力": overview.get("关联能力", ""),
            "证据来源分布": {
                "含本地仓": overview.get("含本地仓单元数", ""),
                "仅 diff": overview.get("仅 diff 单元数", ""),
                "无法获取": overview.get("无法获取单元数", ""),
            },
        },
        "变更单元": [
            {
                "MR 地址": _norm(row.get("MR 地址", "")),
                "标题": _norm(row.get("标题", "")),
                "作者": _norm(row.get("作者", "")),
                "仓": _norm(row.get("仓", "")),
                "来源档位": _norm(row.get("来源档位", "")),
                "首个提交": _norm(row.get("首个提交", "")),
                "末提交": _norm(row.get("末提交", "")),
            }
            for row in unit_rows
        ],
        "变更构成": {
            "纳入文件数": len(scope_rows),
            "纳入分类": [
                {"分类": category, "语言": language, **counts}
                for (category, language), counts in sorted(
                    included_by_category.items(),
                    key=lambda kv: (
                        CATEGORY_ORDER.get(kv[0][0], 9),
                        LANGUAGE_ORDER.get(kv[0][1], 9),
                        kv[0],
                    ),
                )
            ],
            "排除": excluded,
            "KLOC口径新增行数": kloc_additions,
        },
        "指标": metrics,
        "定性": verdict,
        "状态分布": state_count,
        "断言档位分布": tier_count,
        "Mock 情形分布": {f"类{kind}": mock_kind_count[kind] for kind in MOCK_KINDS},
        "问题分布": distribution,
        "分布表": _distribution_table(distribution),
        "证据缺口": {
            "数量": undecidable,
            "占全部": _rate(undecidable, total)["text"],
            "无法获取单元数": unable_units,
        },
        "测试退化": {
            "计入条数": degrade_signals_eff,
            "提示条数": degrade_hints_eff,
        },
        "断言质量": {
            "无断言用例数": b1,
            "只用交互验证用例数": b3,
        },
        "契约漂移数": contract_drifts if total else 0,
        "短路": prod_change_lines == 0,
        "抽样": sampling,
        "命中类数": sum(1 for item in distribution if item["命中"]),
        "未命中类数": DISTRIBUTION_CLASSES - sum(1 for item in distribution if item["命中"]),
        "水位": {
            "证据完整度下限": EVIDENCE_FLOOR,
            "证据完整度完整档": EVIDENCE_GOOD,
            "证据完整度部分档": EVIDENCE_PARTIAL,
            "断言覆盖率良好": GOOD_COVERAGE,
            "断言覆盖率低": COVER_FLOOR,
            "实现被替换率高档": REPLACED_RATE_FLOOR,
            "实现被替换率低档": 0,
            "实现被替换率低档前提": "可判定逻辑点数 > 0",
            "密度参考带": [DENSITY_LOW, DENSITY_HIGH],
        },
        "校验": {"通过": not errors, "错误": errors, "警告": warnings},
    }
    return payload


def run_stats(*, evidence: Path, out: Path | None = None) -> dict[str, Any]:
    """算完写 ``stats.json``；校验不通过时抛 ``EvidenceError``（文件仍然写出）。"""
    payload = analyze(evidence)
    target = out or evidence.with_name("stats.json")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    if not payload["校验"]["通过"]:
        raise EvidenceError(
            f"证据表校验未通过（{len(payload['校验']['错误'])} 项），详见 {target}："
            + "；".join(payload["校验"]["错误"][:10])
        )
    return payload
