"""证据表装配：骨架、分片合并、裁定回填。

只做确定性变换：分类结果、片 JSON、裁定 JSON 都是结构化输入，这里负责去重、编号、
合并覆盖用例并写成 Markdown 表格。判断本身仍由 Skill 完成。

三个模式：

- ``init``   从 manifest / scope / index 生成 ``evidence.md`` 骨架。
- ``merge``  把枚举片的 JSON 合并进证据表：去重、统一编号、合并覆盖用例，
  已有的点会保留，片里的判定字段（问题 / 原因 / 说明）一律忽略。
- ``judge``  把裁定片的 JSON 回填到逻辑点。

证据表只有五段：变更概览、变更单元、范围、用例清单、逻辑点证据。
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

UNIT_HEADER = ("MR 地址", "标题", "作者", "仓", "来源档位")
SCOPE_HEADER = ("路径", "分类", "语言", "来源档位", "新增", "删除")
CASE_HEADER = ("用例", "位置", "变更类型")
POINT_HEADER = (
    "编号",
    "位置",
    "条件摘要",
    "来源",
    "覆盖用例",
    "问题",
    "原因",
    "说明",
)

TIER_VALUES = ("含本地仓", "仅 diff", "无法获取")


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
        end = -2
        # GitLab 的地址形如 group/project/-/merge_requests/7，其中的 "-" 不是仓名
        if len(parts) >= 5 and parts[-3] == "-":
            end = -3
        return "/".join(parts[1:end])
    if len(parts) >= 3:
        return "/".join(parts[1:-1])
    return url


def _unit(url: str, title: str, author: str, repo: str, tier: str) -> dict[str, str]:
    return {
        "MR 地址": url,
        "标题": title,
        "作者": author,
        "仓": repo,
        "来源档位": tier,
    }


def _units_from_index(index: dict[str, Any], tier: str) -> list[dict[str, str]]:
    units: list[dict[str, str]] = []
    for mr in index.get("mrs") or []:
        repo = mr.get("repo") or {}
        url = str(mr.get("web_url", ""))
        units.append(
            _unit(
                url,
                str(mr.get("title", "")),
                str(mr.get("author", "")),
                str(repo.get("project_path") or _repo_of(url)),
                tier,
            )
        )
    return units


def _units_from_manifest(manifest: dict[str, Any], tier: str) -> list[dict[str, str]]:
    units: list[dict[str, str]] = []
    for mr in manifest.get("mrs") or []:
        url = str(mr.get("web_url", ""))
        units.append(_unit(url, "", "", _repo_of(url), tier))
    return units


def _normalize_object_id(raw: str, object_type: str) -> str:
    """SR / AR 归一成 ``SR1234``；MR 是地址，原样保留。"""
    value = (raw or "").strip()
    if not value:
        return ""
    if value.lower().startswith(("http://", "https://")):
        return value
    value = re.sub(r"[\s\-_]", "", value).upper()
    if re.fullmatch(r"\d+", value) and object_type in ("SR", "AR"):
        return f"{object_type}{value}"
    return value


def _infer_object_type(raw: str) -> str:
    value = (raw or "").strip()
    if value.lower().startswith(("http://", "https://")):
        return "MR"
    upper = value.upper()
    for name in ("SR", "AR", "MR"):
        if upper.startswith(name) and upper[len(name) :].strip("-_ ").isdigit():
            return name
    return ""


def _overview(
    *,
    object_type: str,
    object_id: str,
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
        "变更单元数": str(len(units)),
        "涉及仓": involved,
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


def _scope_rows(scope: dict[str, Any], units: Sequence[dict[str, str]]) -> list[dict[str, Any]]:
    tier_of = {unit["MR 地址"]: unit.get("来源档位", "") for unit in units}
    rank = {name: index for index, name in enumerate(TIER_VALUES)}
    rows: list[dict[str, Any]] = []
    for item in scope.get("files") or []:
        if item.get("verdict") != "纳入":
            continue
        urls = item.get("mrs") or ([item.get("mr")] if item.get("mr") else [])
        tiers = [tier_of.get(str(url), "") for url in urls]
        best = min(tiers, key=lambda t: rank.get(t, len(rank))) if tiers else ""
        rows.append(
            {
                "路径": item.get("path", ""),
                "分类": item.get("category", ""),
                "语言": item.get("language", ""),
                "来源档位": best,
                "新增": item.get("additions", 0),
                "删除": item.get("deletions", 0),
            }
        )
    return rows


def _write_evidence(
    *,
    evidence: Path,
    overview: dict[str, str],
    units: Sequence[dict[str, Any]],
    scope_rows: Sequence[dict[str, Any]],
    cases: Sequence[dict[str, Any]],
    points: Sequence[dict[str, Any]],
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
    lines += _table(SCOPE_HEADER, [[r.get(h, "") for h in SCOPE_HEADER] for r in scope_rows])
    lines += [""]

    lines += ["## 用例清单", ""]
    lines += _table(CASE_HEADER, [[c.get(h, "") for h in CASE_HEADER] for c in cases])
    lines += [""]

    lines += ["## 逻辑点证据", ""]
    lines += _table(POINT_HEADER, [[p.get(h, "") for h in POINT_HEADER] for p in points])
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
    raw_id = object_id or str(source.get("object_id", ""))
    object_type = (
        object_type
        or str(source.get("object_type", ""))
        or _infer_object_type(raw_id)
        or "MR"
    )
    object_id = _normalize_object_id(raw_id, object_type)

    warnings: list[str] = list(scope.get("warnings") or [])
    if not object_id:
        warnings.append(
            "缺少对象标识：stats 会报「变更概览缺少必需键 对象标识」。"
            "用 --object-id 指定，或让 --index / --meta 自带 object_id"
        )
    if not index and not meta:
        warnings.append("未提供 --index / --meta：变更单元的标题、作者留空，报告 3.2 会留白")

    metrics = scope.get("metrics") or {}
    excluded = (scope.get("counts") or {}).get("排除") or {}
    overview = _overview(
        object_type=object_type,
        object_id=object_id,
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
        points=[],
        memo=["- 无。"],
    )
    return {
        "证据表": str(evidence),
        "变更单元": len(units),
        "纳入文件": metrics.get("纳入文件数", 0),
        "生产改动行": metrics.get("生产改动行", 0),
        "warnings": warnings,
    }


def _module_key(path: str) -> str:
    """模块缩写取自最深层目录；该目录字母不足 3 个时回退到文件名。"""
    parts = path.replace("\\", "/").split("/")
    directory = parts[-2] if len(parts) >= 2 else ""
    if len(re.sub(r"[^A-Za-z]", "", directory)) >= 3:
        return f"dir:{directory}"
    return f"file:{parts[-1]}"


def _module_abbr(key: str) -> str:
    name = key.split(":", 1)[1]
    letters = re.sub(r"[^A-Za-z]", "", name if key.startswith("dir:") else name.rsplit(".", 1)[0])
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
        and str(row.get("语言", "")) in ("Java", "Python")
    }
    covered_files: set[str] = set()
    for shard in shard_data:
        for path in shard.get("files") or []:
            covered_files.add(str(path))
    for path in sorted(expected_files - covered_files):
        warnings.append(f"枚举片没有覆盖生产文件：{path}")

    cases: dict[str, dict[str, Any]] = {
        str(row.get("用例", "")): {
            "用例": str(row.get("用例", "")),
            "位置": str(row.get("位置", "")),
            "变更类型": str(row.get("变更类型", "")),
        }
        for row in parsed["用例清单"]
        if str(row.get("用例", ""))
    }
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
                }
            else:
                if not existing["位置"]:
                    existing["位置"] = str(raw.get("位置", ""))
                if not existing["变更类型"]:
                    existing["变更类型"] = str(raw.get("变更类型", ""))

    position_of_case = {name: row["位置"] for name, row in cases.items()}

    def identity(condition: str) -> str:
        """去掉「修改」来源内联的全角括号注记，避免同一分支因注记不同被拆成两条。"""
        return re.sub(r"\s*（[^（）]*）\s*$", "", condition).strip()

    existing_points: dict[tuple[str, str], dict[str, str]] = {}
    for row in parsed["逻辑点证据"]:
        key = (str(row.get("位置", "")), identity(str(row.get("条件摘要", ""))))
        existing_points[key] = row

    merged: dict[tuple[str, str], dict[str, Any]] = {}
    for key, row in existing_points.items():
        merged[key] = {
            "位置": str(row.get("位置", "")),
            "条件摘要": str(row.get("条件摘要", "")),
            "来源": str(row.get("来源", "")),
            "覆盖用例": str(row.get("覆盖用例", "")),
            "问题": str(row.get("问题", "")),
            "原因": str(row.get("原因", "")),
            "说明": str(row.get("说明", "")),
        }

    for shard in shard_data:
        for raw in shard.get("points") or []:
            position = str(raw.get("位置", ""))
            condition = str(raw.get("条件摘要", ""))
            if not position:
                warnings.append(
                    f"枚举片里有逻辑点缺少位置，已跳过：{condition[:40] or '（无条件摘要）'}"
                )
                continue
            key = (position, identity(condition))
            candidates = raw.get("触达候选")
            if candidates is None:
                candidates = []
            if not isinstance(candidates, list):
                warnings.append(f"枚举片 {position} 的「触达候选」不是数组，已忽略")
                candidates = []
            refs = []
            for name in candidates:
                if not isinstance(name, str):
                    continue
                where = position_of_case.get(name, "")
                refs.append(f"{name}({where})" if where else name)

            point = merged.get(key)
            if point is None:
                point = merged[key] = {
                    "位置": position,
                    "条件摘要": condition,
                    "来源": str(raw.get("来源", "")),
                    "覆盖用例": "",
                    "问题": "",
                    "原因": "",
                    "说明": "",
                }
            else:
                if not point["条件摘要"]:
                    point["条件摘要"] = condition
                if not point["来源"]:
                    point["来源"] = str(raw.get("来源", ""))

            existing_refs = [part for part in point["覆盖用例"].split("、") if part]
            for ref in refs:
                if ref not in existing_refs:
                    existing_refs.append(ref)
            point["覆盖用例"] = "、".join(existing_refs)

    points = _number_points(list(merged.values()))
    if existing_points:
        warnings.append("合并已按本次片与已有内容重建逻辑点，编号会重新分配")

    _write_evidence(
        evidence=evidence,
        overview=parsed["_overview"],
        units=parsed["变更单元"],
        scope_rows=parsed["范围"],
        cases=list(cases.values()),
        points=points,
        memo=_read_memo(evidence),
    )
    return {
        "证据表": str(evidence),
        "逻辑点": len(points),
        "用例": len(cases),
        "warnings": warnings,
    }


def assemble_judge(*, evidence: Path, judges: Sequence[Path]) -> dict[str, Any]:
    if not judges:
        raise AuditError("assemble judge 至少需要一个 --judges")
    parsed = load_evidence(evidence)
    points = {str(row.get("编号", "")): dict(row) for row in parsed["逻辑点证据"]}
    warnings: list[str] = []

    for path in judges:
        judge = _load_json(path, "裁定 JSON")
        for item in judge.get("points") or []:
            dp_id = str(item.get("编号", ""))
            point = points.get(dp_id)
            if point is None:
                warnings.append(f"裁定片引用了不存在的逻辑点：{dp_id}")
                continue
            for field in ("覆盖用例", "问题", "原因", "说明"):
                if field in item:
                    point[field] = str(item.get(field, ""))

    _write_evidence(
        evidence=evidence,
        overview=parsed["_overview"],
        units=parsed["变更单元"],
        scope_rows=parsed["范围"],
        cases=parsed["用例清单"],
        points=list(points.values()),
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
            tier=tier,
        )
    if mode == "merge":
        return assemble_merge(evidence=evidence, shards=shards)
    if mode == "judge":
        return assemble_judge(evidence=evidence, judges=judges)
    raise AuditError(f"未知 assemble 模式：{mode}")
