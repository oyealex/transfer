"""证据表装配：骨架、分片合并、裁定回填。

只做确定性变换：分类结果、片 JSON、裁定 JSON 都是结构化输入，这里负责去重、编号、
补齐「断言随动」并写成 Markdown 表格。判断本身仍由 Skill 完成。

三个模式：

- ``init``   从 manifest / scope / index 生成 ``evidence.md`` 骨架。
- ``merge``  把枚举片的 JSON 合并进证据表，统一编号。
- ``judge``  把裁定片的 JSON 回填到逻辑点与断言清单。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Sequence

from .errors import AuditError
from .scope import analyze_scope
from .stats import load_evidence

OVERVIEW_KEYS = (
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

UNIT_HEADER = ("MR 地址", "标题", "作者", "仓", "来源档位", "首个提交", "末提交")
SCOPE_HEADER = ("路径", "分类", "语言", "来源档位", "新增", "删除", "结论")
CASE_HEADER = ("用例", "位置", "变更类型", "断言数", "只验交互")
ASSERT_HEADER = ("用例", "断言行号", "原文", "档位", "绑定逻辑点")
MOCK_HEADER = ("类型", "位置", "被替换方法", "情形类", "问题类型")
POINT_HEADER = (
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
)
SIGNAL_HEADER = ("类型", "信号", "位置", "引用生产符号", "计入", "说明")

TIER_VALUES = ("含本地仓", "仅 diff", "无法获取")
PROBLEM_OF_STATE = {
    "无测试": "场景覆盖不足",
    "未断言": "场景覆盖不足",
    "实现被替换": "测试对象偏移",
    "无法判定": "证据缺口",
    "已断言": "",
    "断言偏弱": "",
}


def _int_text(value: Any) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return 0


def _cell(value: Any) -> str:
    text = "" if value is None else str(value)
    return text.replace("|", "\\|").replace("\n", " ").strip()


def _table(header: Sequence[str], rows: Sequence[Sequence[Any]]) -> list[str]:
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    for row in rows:
        lines.append("| " + " | ".join(_cell(c) for c in row) + " |")
    return lines


def _load_json(path: Path, label: str) -> dict[str, Any]:
    if not path.is_file():
        raise AuditError(f"{label} 不存在：{path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise AuditError(f"{label} 不是合法 JSON：{exc}") from exc
    if not isinstance(data, dict):
        raise AuditError(f"{label} 顶层必须是对象")
    return data


def _repo_of(url: str) -> str:
    tail = url.rstrip("/").split("://", 1)[-1]
    parts = tail.split("/")
    if len(parts) >= 4 and parts[-2] in ("merge_requests", "pull", "pulls"):
        return "/".join(parts[1:-2])
    if len(parts) >= 3:
        return "/".join(parts[1:-1])
    return url


def _units_from_index(index: dict[str, Any], tier: str) -> list[dict[str, str]]:
    units: list[dict[str, str]] = []
    for mr in index.get("mrs") or []:
        repo = mr.get("repo") or {}
        first = (mr.get("first_commit") or {}).get("short_sha", "")
        last = (mr.get("last_commit") or {}).get("short_sha", "")
        units.append(
            {
                "MR 地址": str(mr.get("web_url", "")),
                "标题": str(mr.get("title", "")),
                "作者": str(mr.get("author", "")),
                "仓": str(repo.get("project_path") or _repo_of(str(mr.get("web_url", "")))),
                "来源档位": tier,
                "首个提交": first,
                "末提交": last,
            }
        )
    return units


def _units_from_manifest(manifest: dict[str, Any], tier: str) -> list[dict[str, str]]:
    units: list[dict[str, str]] = []
    for mr in manifest.get("mrs") or []:
        url = str(mr.get("web_url", ""))
        units.append(
            {
                "MR 地址": url,
                "标题": "",
                "作者": "",
                "仓": _repo_of(url),
                "来源档位": tier,
                "首个提交": "",
                "末提交": "",
            }
        )
    return units


def _overview(
    *,
    object_type: str,
    object_id: str,
    raw_input: str,
    ability: str,
    units: Sequence[dict[str, str]],
    metrics: dict[str, Any],
    excluded: dict[str, int],
) -> dict[str, str]:
    tiers = {tier: 0 for tier in TIER_VALUES}
    for unit in units:
        tier = unit.get("来源档位", "")
        if tier in tiers:
            tiers[tier] += 1
    repos = sorted({unit.get("仓", "") for unit in units if unit.get("仓")})
    involved = f"{len(repos)} 个（{'、'.join(repos)}）" if repos else "0 个"
    overview = {
        "对象类型": object_type,
        "对象标识": object_id,
        "原始输入": raw_input,
        "变更单元数": str(len(units)),
        "涉及仓": involved,
        "关联能力": ability,
        "含本地仓单元数": str(tiers["含本地仓"]),
        "仅 diff 单元数": str(tiers["仅 diff"]),
        "无法获取单元数": str(tiers["无法获取"]),
        "变更生产代码行数": str(metrics.get("变更生产代码行数", 0)),
        "生产文件数": str(metrics.get("生产文件数", 0)),
        "纳入文件数": str(metrics.get("纳入文件数", 0)),
        "测试文件数": str(metrics.get("测试文件数", 0)),
        "C/C++ 受限文件数": str(metrics.get("C/C++ 受限文件数", 0)),
    }
    for category in sorted(excluded):
        overview[f"排除：{category}"] = str(excluded[category])
    return overview


def _scope_rows(scope: dict[str, Any], units: Sequence[dict[str, str]]) -> list[list[Any]]:
    tier_of = {unit["MR 地址"]: unit.get("来源档位", "") for unit in units}
    rows: list[list[Any]] = []
    for item in scope.get("files") or []:
        if item.get("verdict") != "纳入":
            continue
        rows.append(
            [
                item.get("path", ""),
                item.get("category", ""),
                item.get("language", ""),
                tier_of.get(item.get("mr", ""), ""),
                item.get("additions", 0),
                item.get("deletions", 0),
                "纳入",
            ]
        )
    return rows


def _write_evidence(
    *,
    evidence: Path,
    overview: dict[str, str],
    units: Sequence[dict[str, Any]],
    scope_rows: Sequence[Sequence[Any]],
    cases: Sequence[dict[str, Any]],
    assertions: Sequence[dict[str, Any]],
    mocks: Sequence[dict[str, Any]],
    points: Sequence[dict[str, Any]],
    signals: Sequence[dict[str, Any]],
    memo: Sequence[str],
) -> None:
    lines: list[str] = []

    lines += ["## 变更概览", ""]
    keys = [key for key in OVERVIEW_KEYS if key in overview]
    keys += sorted(key for key in overview if key not in OVERVIEW_KEYS)
    lines += _table(("项", "值"), [[key, overview[key]] for key in keys])
    lines += [""]

    lines += ["## 变更单元", ""]
    lines += _table(UNIT_HEADER, [[u.get(h, "") for h in UNIT_HEADER] for u in units])
    lines += [""]

    lines += ["## 范围", ""]
    lines += _table(SCOPE_HEADER, scope_rows)
    lines += [""]

    lines += ["## 用例清单", ""]
    lines += _table(CASE_HEADER, [[c.get(h, "") for h in CASE_HEADER] for c in cases])
    lines += [""]

    lines += ["## 断言清单", ""]
    lines += _table(ASSERT_HEADER, [[a.get(h, "") for h in ASSERT_HEADER] for a in assertions])
    lines += [""]

    lines += ["## Mock 清单", ""]
    lines += _table(MOCK_HEADER, [[m.get(h, "") for h in MOCK_HEADER] for m in mocks])
    lines += [""]

    lines += ["## 逻辑点证据", ""]
    lines += _table(POINT_HEADER, [[p.get(h, "") for h in POINT_HEADER] for p in points])
    lines += [""]

    lines += ["## 信号与问题", ""]
    lines += _table(SIGNAL_HEADER, [[s.get(h, "") for h in SIGNAL_HEADER] for s in signals])
    lines += [""]

    if memo:
        lines += ["## 取数备忘", ""]
        lines += [str(line) for line in memo]
        lines += [""]

    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def _read_memo(evidence: Path) -> list[str]:
    if not evidence.is_file():
        return []
    text = evidence.read_text(encoding="utf-8")
    match = re.search(r"^## 取数备忘\s*$([\s\S]*?)(?=^## |\Z)", text, re.M)
    if not match:
        return []
    return [line for line in match.group(1).strip().splitlines() if line.strip()]


def assemble_init(
    *,
    evidence: Path,
    manifest_path: Path,
    scope_path: Path | None = None,
    index_path: Path | None = None,
    meta_path: Path | None = None,
    object_type: str = "",
    object_id: str = "",
    raw_input: str = "",
    ability: str = "静态级",
    tier: str = "仅 diff",
) -> dict[str, Any]:
    manifest = _load_json(manifest_path, "manifest")
    scope = _load_json(scope_path, "scope") if scope_path else analyze_scope(manifest_path)
    index = _load_json(index_path, "index") if index_path else None
    meta = _load_json(meta_path, "meta") if meta_path else None

    if meta and meta.get("units"):
        units = [dict(unit) for unit in meta["units"]]
    elif index:
        units = _units_from_index(index, tier)
    else:
        units = _units_from_manifest(manifest, tier)

    source = meta or index or {}
    object_type = object_type or str(source.get("object_type", "")) or "MR"
    object_id = object_id or str(source.get("object_id", "")) or ""
    raw_input = raw_input or object_id

    metrics = scope.get("metrics") or {}
    excluded = (scope.get("counts") or {}).get("排除") or {}
    overview = _overview(
        object_type=object_type,
        object_id=object_id,
        raw_input=raw_input,
        ability=ability,
        units=units,
        metrics=metrics,
        excluded=excluded,
    )
    _write_evidence(
        evidence=evidence,
        overview=overview,
        units=units,
        scope_rows=_scope_rows(scope, units),
        cases=[],
        assertions=[],
        mocks=[],
        points=[],
        signals=[],
        memo=["- 无。"],
    )
    return {
        "证据表": str(evidence),
        "变更单元": len(units),
        "纳入文件": metrics.get("纳入文件数", 0),
        "生产改动行": metrics.get("生产改动行", 0),
        "warnings": scope.get("warnings") or [],
    }


def _module_key(path: str) -> str:
    parts = path.replace("\\", "/").split("/")
    for directory in reversed(parts[:-1]):
        letters = re.sub(r"[^A-Za-z]", "", directory)
        if len(letters) >= 3:
            return f"dir:{directory}"
    stem = re.sub(r"[^A-Za-z]", "", parts[-1].rsplit(".", 1)[0])
    return f"file:{parts[-1]}"


def _module_abbr(key: str) -> str:
    name = key.split(":", 1)[1]
    if key.startswith("dir:"):
        letters = re.sub(r"[^A-Za-z]", "", name)
    else:
        letters = re.sub(r"[^A-Za-z]", "", name.rsplit(".", 1)[0])
    return (letters[:3] or "DP").upper()


def _point_key(point: dict[str, Any]) -> tuple[str, int, str]:
    position = str(point.get("位置", ""))
    path, _, line = position.rpartition(":")
    try:
        line_no = int(line)
    except ValueError:
        path, line_no = position, 0
    return (path, line_no, str(point.get("条件摘要", "")))


def _number_points(points: Sequence[dict[str, Any]]) -> list[dict[str, Any]]:
    ordered = sorted(points, key=_point_key)
    abbr_of_key: dict[str, str] = {}
    used: set[str] = set()
    counters: dict[str, int] = {}
    result: list[dict[str, Any]] = []
    for point in ordered:
        path = str(point.get("位置", "")).rsplit(":", 1)[0]
        key = _module_key(path)
        if key not in abbr_of_key:
            base = _module_abbr(key)
            candidate = base
            index = 2
            while candidate in used:
                candidate = f"{base}{index}"
                index += 1
            abbr_of_key[key] = candidate
            used.add(candidate)
        counters[key] = counters.get(key, 0) + 1
        abbr = abbr_of_key[key]
        point = dict(point)
        point["编号"] = f"{abbr}-{counters[key]:02d}"
        result.append(point)
    return result


def _merge_assertions(
    shards: Sequence[dict[str, Any]], id_of_position: dict[str, str]
) -> list[dict[str, Any]]:
    merged: dict[tuple[str, str], dict[str, Any]] = {}
    for shard in shards:
        for raw in shard.get("assertions") or []:
            if str(raw.get("进清单", "是")) != "是":
                continue
            case = str(raw.get("用例", ""))
            line = str(raw.get("断言行号", ""))
            if not case or not line:
                continue
            key = (case, line)
            item = merged.setdefault(
                key,
                {
                    "用例": case,
                    "断言行号": line,
                    "原文": str(raw.get("原文", "")),
                    "档位": "",
                    "绑定逻辑点": [],
                    "行变更": str(raw.get("行变更", "")),
                },
            )
            for position in raw.get("绑定位置") or []:
                dp_id = id_of_position.get(str(position))
                if dp_id and dp_id not in item["绑定逻辑点"]:
                    item["绑定逻辑点"].append(dp_id)
    result = []
    for item in merged.values():
        item["绑定逻辑点"] = "、".join(item.pop("绑定逻辑点"))
        result.append(item)
    result.sort(key=lambda a: (a["用例"], a["断言行号"]))
    return result


def assemble_merge(*, evidence: Path, shards: Sequence[Path]) -> dict[str, Any]:
    if not shards:
        raise AuditError("assemble merge 至少需要一个 --shards")
    parsed = load_evidence(evidence)
    shard_data = [_load_json(path, "分片 JSON") for path in shards]
    warnings: list[str] = []
    expected_files = {
        str(row.get("路径", ""))
        for row in parsed["范围"]
        if str(row.get("分类", "")) == "生产代码"
    }
    covered_files: set[str] = set()
    for shard in shard_data:
        for path in shard.get("files") or []:
            covered_files.add(str(path))
    for path in sorted(expected_files - covered_files):
        warnings.append(f"枚举片没有覆盖生产文件：{path}")

    points: list[dict[str, Any]] = []
    seen_points: set[tuple[str, str]] = set()
    for shard in shard_data:
        for raw in shard.get("points") or []:
            position = str(raw.get("位置", ""))
            condition = str(raw.get("条件摘要", ""))
            if not position:
                continue
            key = (position, condition)
            if key in seen_points:
                continue
            seen_points.add(key)
            points.append(
                {
                    "位置": position,
                    "类型": str(raw.get("类型", "")),
                    "条件摘要": condition,
                    "来源": str(raw.get("来源", "")),
                    "触达用例": "、".join(str(x) for x in raw.get("触达候选") or []),
                    "断言证据": "",
                    "被替换方法": "",
                    "状态": "",
                    "问题类型": "",
                    "复核": "",
                }
            )
    points = _number_points(points)
    id_of_position = {str(p["位置"]): str(p["编号"]) for p in points}

    cases: dict[str, dict[str, Any]] = {}
    for shard in shard_data:
        for raw in shard.get("cases") or []:
            name = str(raw.get("用例", ""))
            if not name:
                continue
            existing = cases.get(name)
            if existing is None:
                cases[name] = {
                    "用例": name,
                    "位置": str(raw.get("位置", "")),
                    "变更类型": str(raw.get("变更类型", "")),
                    "断言数": str(raw.get("断言数", 0)),
                    "只验交互": "",
                }
            else:
                existing["断言数"] = str(
                    max(_int_text(existing["断言数"]), _int_text(raw.get("断言数", 0)))
                )

    mocks: list[dict[str, Any]] = []
    seen_mocks: set[tuple[str, str, str]] = set()
    for shard in shard_data:
        for raw in shard.get("mocks") or []:
            key = (
                str(raw.get("类型", "")),
                str(raw.get("位置", "")),
                str(raw.get("被替换方法", "")),
            )
            if key in seen_mocks:
                continue
            seen_mocks.add(key)
            mocks.append(
                {
                    "类型": key[0],
                    "位置": key[1],
                    "被替换方法": key[2],
                    "情形类": str(raw.get("情形类", "")),
                    "问题类型": "",
                }
            )

    assertions = _merge_assertions(shard_data, id_of_position)
    by_point: dict[str, list[dict[str, Any]]] = {}
    for item in assertions:
        for dp_id in str(item["绑定逻辑点"]).split("、"):
            if dp_id:
                by_point.setdefault(dp_id, []).append(item)
    for point in points:
        if point["来源"] != "修改":
            point["断言随动"] = ""
            continue
        covering = by_point.get(str(point["编号"]), [])
        if not covering:
            point["断言随动"] = "否"
            point["复核"] = "随动：没有覆盖该点的断言"
        elif any(str(item.get("行变更", "")) == "是" for item in covering):
            point["断言随动"] = "是"
        else:
            point["断言随动"] = "否"

    _write_evidence(
        evidence=evidence,
        overview=parsed["_overview"],
        units=parsed["变更单元"],
        scope_rows=[[row.get(h, "") for h in SCOPE_HEADER] for row in parsed["范围"]],
        cases=list(cases.values()),
        assertions=assertions,
        mocks=mocks,
        points=points,
        signals=[[row.get(h, "") for h in SIGNAL_HEADER] for row in parsed["信号与问题"]],
        memo=_read_memo(evidence),
    )
    return {
        "证据表": str(evidence),
        "逻辑点": len(points),
        "用例": len(cases),
        "断言": len(assertions),
        "Mock": len(mocks),
        "warnings": warnings,
    }


def assemble_judge(*, evidence: Path, judges: Sequence[Path]) -> dict[str, Any]:
    if not judges:
        raise AuditError("assemble judge 至少需要一个 --judges")
    parsed = load_evidence(evidence)
    points = {str(row.get("编号", "")): dict(row) for row in parsed["逻辑点证据"]}
    assertions = {
        (str(row.get("用例", "")), str(row.get("断言行号", ""))): dict(row)
        for row in parsed["断言清单"]
    }
    cases = {str(row.get("用例", "")): dict(row) for row in parsed["用例清单"]}
    tier_rank = {"有效": 2, "形状": 1, "存活": 0}
    warnings: list[str] = []

    for path in judges:
        judge = _load_json(path, "裁定 JSON")
        for item in judge.get("points") or []:
            dp_id = str(item.get("编号", ""))
            point = points.get(dp_id)
            if point is None:
                warnings.append(f"裁定片引用了不存在的逻辑点：{dp_id}")
                continue
            state = str(item.get("状态", ""))
            point["状态"] = state
            point["断言证据"] = str(item.get("断言证据", ""))
            point["复核"] = str(item.get("复核", ""))
            point["问题类型"] = PROBLEM_OF_STATE.get(state, "")
            for tier_item in item.get("档位") or []:
                key = (str(tier_item.get("用例", "")), str(tier_item.get("断言行号", "")))
                tier = str(tier_item.get("档位", ""))
                assertion = assertions.get(key)
                if assertion is None:
                    warnings.append(f"裁定片给出的断言不在断言清单：{key[0]} / {key[1]}")
                    continue
                previous = str(assertion.get("档位", ""))
                if tier in tier_rank and (
                    previous == "" or tier_rank[tier] > tier_rank.get(previous, -1)
                ):
                    assertion["档位"] = tier
        for item in judge.get("cases") or []:
            name = str(item.get("用例", ""))
            case = cases.get(name)
            if case is None:
                warnings.append(f"裁定片引用了不存在的用例：{name}")
                continue
            if str(item.get("只验交互", "")) == "是":
                case["只验交互"] = "是"
            elif not case.get("只验交互"):
                case["只验交互"] = str(item.get("只验交互", ""))

    _write_evidence(
        evidence=evidence,
        overview=parsed["_overview"],
        units=parsed["变更单元"],
        scope_rows=[[row.get(h, "") for h in SCOPE_HEADER] for row in parsed["范围"]],
        cases=list(cases.values()),
        assertions=list(assertions.values()),
        mocks=[[row.get(h, "") for h in MOCK_HEADER] for row in parsed["Mock 清单"]],
        points=list(points.values()),
        signals=[[row.get(h, "") for h in SIGNAL_HEADER] for row in parsed["信号与问题"]],
        memo=_read_memo(evidence),
    )
    return {"证据表": str(evidence), "逻辑点": len(points), "warnings": warnings}


def run_assemble(
    *,
    mode: str,
    evidence: Path,
    manifest: Path | None = None,
    scope: Path | None = None,
    index: Path | None = None,
    meta: Path | None = None,
    object_type: str = "",
    object_id: str = "",
    raw_input: str = "",
    ability: str = "静态级",
    tier: str = "仅 diff",
    shards: Sequence[Path] = (),
    judges: Sequence[Path] = (),
) -> dict[str, Any]:
    if mode == "init":
        if manifest is None:
            raise AuditError("assemble init 需要 --manifest")
        return assemble_init(
            evidence=evidence,
            manifest_path=manifest,
            scope_path=scope,
            index_path=index,
            meta_path=meta,
            object_type=object_type,
            object_id=object_id,
            raw_input=raw_input,
            ability=ability,
            tier=tier,
        )
    if mode == "merge":
        return assemble_merge(evidence=evidence, shards=shards)
    if mode == "judge":
        return assemble_judge(evidence=evidence, judges=judges)
    raise AuditError(f"未知 assemble 模式：{mode}")
