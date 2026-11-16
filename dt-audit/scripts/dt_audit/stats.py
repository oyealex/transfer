"""证据表统计与一致性校验。

读取 ``.dt-audit/<对象>/evidence.md``，按 ``references/报告结构.md`` 的指标定义算出
全部指标、问题分布与占比、严重度升级，并校验合计与交叉关系，写出 ``stats.json``。

这一层**不做任何判断**：只解析固定表头、做算术、查结构矛盾。判断（观测状态、
断言档位、Mock 类别）都已在 Skill 里完成，写进了证据表。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Sequence

SCHEMA_VERSION = "1.0"

# ------------------------------------------------------------------ 枚举

STATES = ("已观测", "部分观测", "实现被替换", "未观测", "无测试", "无法判定")
TIERS = ("判别性", "结构性", "存在性")
CASE_KINDS = ("新增", "修改", "既有")
SEVERITIES = ("严重", "高", "中")
# 决策点行允许「提示」：A4 是置信度提示，不是质量问题，不进分布表。
DP_SEVERITIES = SEVERITIES + ("提示",)
SOURCE_TIERS = ("本地精确", "本地推导", "无法获取")
MOCK_KINDS = tuple(str(i) for i in range(1, 8))
EXCLUDE_PREFIX = "排除："

# 状态 → 由它推出的问题类型。状态是唯一来源，问题类型列只是冗余标注。
PROBLEM_OF_STATE = {
    "无测试": "A1",
    "实现被替换": "C1",
    "未观测": "D1",
    "无法判定": "A4",
    "已观测": "",
    "部分观测": "",
}

PROBLEM_NAMES = {
    "A1": "无测试",
    "A2": "测试脱靶",
    "A3": "证据被削弱",
    "A4": "证据不足",
    "B1": "无断言用例",
    "B2": "断言不判别",
    "B3": "只用交互验证",
    "C1": "实现被替换",
    "C2": "契约漂移风险",
    "D1": "分支未观测",
}

PROBLEM_UNITS = {
    "A1": "个决策点",
    "A2": "个对象",
    "A3": "条信号",
    "A4": "个决策点",
    "B1": "个用例",
    "B2": "条断言",
    "B3": "个用例",
    "C1": "个决策点",
    "C2": "处",
    "D1": "个决策点",
}

BASE_SEVERITY = {
    "A1": "严重",
    "A2": "严重",
    "A3": "严重",
    "A4": "提示",
    "B1": "高",
    "B2": "中",
    "B3": "高",
    "C1": "高",
    "C2": "高",
    "D1": "中",
}

# 占比分母：可判定 / 全部决策点 / 用例总数 / 断言总数；None 表示不填占比。
DIVISOR = {
    "A1": "可判定",
    "A4": "全部",
    "B1": "用例",
    "B2": "断言",
    "B3": "用例",
    "C1": "可判定",
    "D1": "可判定",
}

# 判定水位。数值与 references/报告结构.md §二、references/问题类型.md 保持同步。
EVIDENCE_FLOOR = 0.60
EVIDENCE_GOOD = 0.90
EVIDENCE_PARTIAL = 0.60
GOOD_OBSERVED = 0.70
GOOD_DISCRIMINATING = 0.70
COVER_FLOOR = 0.40
DISCRIMINATING_FLOOR = 0.50
C1_POINT_FLOOR = 3
C1_RATE_FLOOR = 0.15
DENSITY_LOW = 5.0
DENSITY_HIGH = 30.0

# 分布表排序：严重度降序，同严重度按计数单位优先级，再按数量降序。
SEVERITY_RANK = {"严重": 0, "高": 1, "中": 2, "提示": 3}
UNIT_RANK = {"个决策点": 0, "个用例": 1, "个对象": 2, "条断言": 3, "条信号": 4, "处": 5}
DISTRIBUTION_CLASSES = 9  # A1–A3、B1–B3、C1、C2、D1；A4 单列，不计入

# ------------------------------------------------------------------ 结构

REQUIRED_SECTIONS = {
    "变更概览": ["项", "值"],
    "范围": ["路径", "分类", "来源档位", "新增", "删除"],
    "用例清单": ["用例", "位置", "变更类型", "断言数", "B3"],
    "断言清单": ["用例", "断言行号", "档位", "绑定决策点"],
    "Mock 清单": ["类型", "位置", "被替换方法", "情形类", "问题类型"],
    "决策点证据": [
        "编号",
        "位置",
        "类型",
        "条件摘要",
        "来源",
        "断言随动",
        "触达用例",
        "断言证据",
        "状态",
        "问题类型",
        "严重度",
        "复核",
    ],
    "信号与问题": ["类型", "位置", "说明"],
}

EMPTY_MARKS = ("", "—", "-", "–")


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
        except EvidenceError:
            problems.append(f"## {name} 下没有表格")
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
        return {"ratio": None, "text": "不适用"}
    ratio = numerator / denominator
    return {"ratio": round(ratio, 4), "text": f"{ratio * 100:.1f}%"}


def _grade_completeness(ratio: float | None) -> str:
    if ratio is None:
        return "不适用"
    if ratio >= EVIDENCE_GOOD:
        return "完整"
    if ratio >= EVIDENCE_PARTIAL:
        return "部分"
    return "不足"


def _distribution_table(distribution: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """1.2 分布表用的行：只取命中项，A4 单列不进来，按模板规定排序。"""
    hits = [item for item in distribution if item["命中"] and item["编号"] != "A4"]
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

    # ---- 概览
    row_count = _as_int(
        overview.get("变更生产代码行数", ""), where="变更概览.变更生产代码行数", errors=errors
    )
    limited_files = _as_int(
        overview.get("C/C++ 受限文件数", ""), where="变更概览.C/C++ 受限文件数", errors=errors
    )

    # ---- 范围（只列纳入的文件；排除项在概览里按分类计数）
    scope_rows = parsed["范围"]
    included_by_category: dict[str, dict[str, int]] = {}
    for idx, row in enumerate(scope_rows, start=1):
        where = f"范围第 {idx} 行"
        if not _norm(row.get("路径", "")):
            errors.append(f"{where}：路径为空")
        tier = _norm(row.get("来源档位", ""))
        if tier and tier not in SOURCE_TIERS:
            errors.append(f"{where}：来源档位应为 {('/'.join(SOURCE_TIERS))}，实际「{tier}」")
        additions = _as_int(_norm(row.get("新增", "")) or "0", where=f"{where}.新增", errors=errors)
        deletions = _as_int(_norm(row.get("删除", "")) or "0", where=f"{where}.删除", errors=errors)
        bucket = included_by_category.setdefault(
            _norm(row.get("分类", "")) or "未分类", {"文件数": 0, "新增": 0, "删除": 0}
        )
        bucket["文件数"] += 1
        bucket["新增"] += additions
        bucket["删除"] += deletions

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
    case_names: set[str] = set()
    b1 = b3 = 0
    new_cases = modified_cases = 0
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
        assertions = _as_int(_norm(row.get("断言数", "")) or "0", where=f"{where}.断言数", errors=errors)
        b3_flag = _norm(row.get("B3", ""))
        if b3_flag not in ("", "是", "否"):
            errors.append(f"{where}：B3 只填「是」或留空，实际「{b3_flag}」")
        counted = kind in ("新增", "修改")
        if counted and assertions == 0:
            b1 += 1
        if counted and b3_flag == "是":
            b3 += 1
    new_cases = case_kind_count["新增"]
    modified_cases = case_kind_count["修改"]
    case_total = new_cases + modified_cases

    # ---- 断言
    assertion_rows = parsed["断言清单"]
    tier_count = {tier: 0 for tier in TIERS}
    for idx, row in enumerate(assertion_rows, start=1):
        where = f"断言清单第 {idx} 行"
        tier = _norm(row.get("档位", ""))
        if tier not in TIERS:
            errors.append(f"{where}：档位应为 {('/'.join(TIERS))}，实际「{tier}」")
        else:
            tier_count[tier] += 1
        case = _norm(row.get("用例", ""))
        if case and case not in case_names:
            errors.append(f"{where}：用例「{case}」不在用例清单里")
        if not case:
            errors.append(f"{where}：用例为空")
    assertion_total = len(assertion_rows)

    # ---- Mock
    mock_rows = parsed["Mock 清单"]
    mock_kind_count = {kind: 0 for kind in MOCK_KINDS}
    c2 = 0
    for idx, row in enumerate(mock_rows, start=1):
        where = f"Mock 清单第 {idx} 行"
        kind = _norm(row.get("情形类", ""))
        if kind not in MOCK_KINDS:
            errors.append(f"{where}：情形类应为 1–7，实际「{kind}」")
        else:
            mock_kind_count[kind] += 1
        problem = _norm(row.get("问题类型", ""))
        if problem and problem != "C2":
            errors.append(f"{where}：问题类型只填 C2 或留空，实际「{problem}」")
        if problem == "C2":
            c2 += 1

    # ---- 决策点
    dp_rows = parsed["决策点证据"]
    state_count = {state: 0 for state in STATES}
    dp_ids: set[str] = set()
    dp_severities: dict[str, str] = {}
    for idx, row in enumerate(dp_rows, start=1):
        where = f"决策点证据第 {idx} 行"
        dp_id = _norm(row.get("编号", ""))
        if not dp_id:
            errors.append(f"{where}：编号为空")
        elif dp_id in dp_ids:
            errors.append(f"{where}：编号「{dp_id}」重复")
        dp_ids.add(dp_id)

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

        severity = _norm(row.get("严重度", ""))
        if severity and severity not in DP_SEVERITIES:
            errors.append(f"{where}（{dp_id}）：严重度应为 严重/高/中/提示，实际「{severity}」")
        dp_severities[dp_id] = severity

        reach = _norm(row.get("触达用例", ""))
        if state == "无测试" and reach:
            errors.append(f"{where}（{dp_id}）：状态是无测试，但触达用例写了「{reach}」")
        if state in ("已观测", "部分观测", "未观测") and not reach:
            errors.append(f"{where}（{dp_id}）：状态是「{state}」，触达用例不能为空")

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
                review = _norm(row.get("复核", ""))
                if state == "已观测" and not review:
                    errors.append(
                        f"{where}（{dp_id}）：断言未随实现变更却判已观测，「复核」列必须写明理由"
                    )
                elif state in ("已观测", "部分观测") and review:
                    warnings.append(f"{dp_id}：断言未随实现变更，已按复核列说明放行")
        elif follow:
            errors.append(f"{where}（{dp_id}）：来源是新增，断言随动应留空，实际「{follow}」")

    # ---- 信号
    signal_rows = parsed["信号与问题"]
    a2 = a3 = 0
    for idx, row in enumerate(signal_rows, start=1):
        where = f"信号与问题第 {idx} 行"
        kind = _norm(row.get("类型", ""))
        if kind not in ("A2", "A3"):
            errors.append(f"{where}：类型只填 A2 或 A3，实际「{kind}」")
        elif kind == "A2":
            a2 += 1
        else:
            a3 += 1

    # ---- 决策点合计
    total = len(dp_rows)
    undecidable = state_count["无法判定"]
    decidable = total - undecidable
    if total and sum(state_count.values()) != total:
        errors.append(
            f"决策点状态合计 {sum(state_count.values())} 与总行数 {total} 不一致"
        )
    unnoticed = state_count["实现被替换"] + state_count["无测试"] + state_count["未观测"]
    if decidable != state_count["已观测"] + state_count["部分观测"] + unnoticed:
        errors.append(
            "可判定决策点数与各状态之和不符："
            f"{decidable} != 已观测 {state_count['已观测']} + 部分观测 {state_count['部分观测']}"
            f" + 未被观测 {unnoticed}"
        )
    if a2 and state_count["无测试"] != total:
        warnings.append("A2 命中时，全部决策点应都是「无测试」；当前不是，请核对")

    # ---- 指标
    thousand_lines = row_count / 1000.0
    density: dict[str, Any]
    if thousand_lines:
        density_value = new_cases / thousand_lines
        density = {"value": round(density_value, 1), "text": f"{density_value:.1f} 个/千行"}
    else:
        density = {"value": None, "text": "不适用"}

    observed_rate = _rate(state_count["已观测"] + state_count["部分观测"], decidable)
    intercept_rate = _rate(state_count["已观测"], total)
    discriminating_rate = _rate(tier_count["判别性"], assertion_total)
    mocked_rate = _rate(state_count["实现被替换"], decidable)
    completeness_ratio = (decidable / total) if total else None
    completeness = {
        "ratio": round(completeness_ratio, 4) if completeness_ratio is not None else None,
        "text": f"{completeness_ratio * 100:.1f}%" if completeness_ratio is not None else "不适用",
        "档": _grade_completeness(completeness_ratio),
        "低于下限": bool(completeness_ratio is not None and completeness_ratio < EVIDENCE_FLOOR),
    }

    metrics = {
        "变更生产代码行数": row_count,
        "变更生产代码千行": round(thousand_lines, 3),
        "新增DT数": new_cases,
        "修改DT数": modified_cases,
        "既有用例数": case_kind_count["既有"],
        "用例总数": case_total,
        "新增DT密度": density,
        "全部决策点": total,
        "可判定决策点数": decidable,
        "无法判定决策点数": undecidable,
        "观测率": observed_rate,
        "可拦截率": intercept_rate,
        "判别性断言占比": discriminating_rate,
        "架空率": mocked_rate,
        "证据完整度": completeness,
        "C/C++受限文件数": limited_files,
    }

    # ---- 问题分布
    counts = {
        "A1": state_count["无测试"],
        "A2": a2,
        "A3": a3,
        "A4": undecidable,
        "B1": b1,
        "B2": tier_count["结构性"] + tier_count["存在性"],
        "B3": b3,
        "C1": state_count["实现被替换"],
        "C2": c2,
        "D1": state_count["未观测"],
    }
    denominators = {"可判定": decidable, "全部": total, "用例": case_total, "断言": assertion_total}

    distribution: list[dict[str, Any]] = []
    for problem_id in ("A1", "A2", "A3", "A4", "B1", "B2", "B3", "C1", "C2", "D1"):
        count = counts[problem_id]
        divisor_key = DIVISOR.get(problem_id)
        if divisor_key is None:
            share = None
        else:
            denominator = denominators[divisor_key]
            share = _rate(count, denominator) if denominator else None
        severity = BASE_SEVERITY[problem_id]
        upgrade: str | None = None
        if problem_id == "C1" and counts["C1"] >= C1_POINT_FLOOR and mocked_rate["ratio"] is not None:
            if mocked_rate["ratio"] >= C1_RATE_FLOOR:
                severity, upgrade = "严重", (
                    f"被替换 {counts['C1']} 个 ≥ {C1_POINT_FLOOR}，"
                    f"架空率 {mocked_rate['text']} ≥ {C1_RATE_FLOOR * 100:.0f}%"
                )
        if problem_id == "B2" and discriminating_rate["ratio"] is not None:
            if discriminating_rate["ratio"] < DISCRIMINATING_FLOOR:
                severity, upgrade = "高", (
                    f"判别性断言占比 {discriminating_rate['text']} "
                    f"< {DISCRIMINATING_FLOOR * 100:.0f}%"
                )
        if problem_id == "D1" and observed_rate["ratio"] is not None:
            if observed_rate["ratio"] < COVER_FLOOR:
                severity, upgrade = "高", (
                    f"观测率 {observed_rate['text']} < {COVER_FLOOR * 100:.0f}%"
                )
        distribution.append(
            {
                "编号": problem_id,
                "类型": PROBLEM_NAMES[problem_id],
                "数量": count,
                "单位": PROBLEM_UNITS[problem_id],
                "占同类总数": share["text"] if share else "—",
                "基础严重度": BASE_SEVERITY[problem_id],
                "严重度": severity,
                "升级理由": upgrade,
                "命中": count > 0,
            }
        )

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
                "本地精确": overview.get("本地精确单元数", ""),
                "本地推导": overview.get("本地推导单元数", ""),
                "无法获取": overview.get("无法获取单元数", ""),
            },
        },
        "变更构成": {
            "纳入文件数": len(scope_rows),
            "纳入分类": [
                {"分类": category, **counts}
                for category, counts in sorted(included_by_category.items())
            ],
            "排除": excluded,
        },
        "指标": metrics,
        "状态分布": state_count,
        "断言档位分布": tier_count,
        "Mock 情形分布": {f"类{kind}": mock_kind_count[kind] for kind in MOCK_KINDS},
        "问题分布": distribution,
        "分布表": _distribution_table(distribution),
        "命中类数": sum(
            1 for item in distribution if item["命中"] and item["编号"] != "A4"
        ),
        "未命中类数": DISTRIBUTION_CLASSES
        - sum(1 for item in distribution if item["命中"] and item["编号"] != "A4"),
        "A4命中": any(item["命中"] and item["编号"] == "A4" for item in distribution),
        "证据": completeness,
        "水位": {
            "证据完整度下限": EVIDENCE_FLOOR,
            "证据完整度完整档": EVIDENCE_GOOD,
            "证据完整度部分档": EVIDENCE_PARTIAL,
            "观测率良好": GOOD_OBSERVED,
            "判别性断言占比良好": GOOD_DISCRIMINATING,
            "覆盖低水位": COVER_FLOOR,
            "判别性断言占比下限": DISCRIMINATING_FLOOR,
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
