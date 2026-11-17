"""变更文件的范围收敛。

把 ``manifest.json`` 里的文件清单按 [范围规则](../../references/范围规则.md) 分类，
输出范围表数据、排除计数与分片/短路要用的体量指标。

只做**路径与扩展名**能判的部分：内容特征（``@Test``、生成代码文件头）需要读文件，
留给 Skill 在步骤 1 复核时补判。判不准的文件按「其余」排除，不猜。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from .errors import AuditError

LANGUAGE_BY_EXT = {
    ".java": "Java",
    ".py": "Python",
    ".c": "C",
    ".h": "C++",  # 同目录实现判不出时按 C++ 处理，见范围规则
    ".cc": "C++",
    ".cpp": "C++",
    ".cxx": "C++",
    ".hpp": "C++",
    ".hh": "C++",
    ".hxx": "C++",
}

CONFIG_EXTS = {".yaml", ".yml", ".json", ".xml", ".properties", ".ini", ".conf", ".toml", ".env"}
DOC_EXTS = {".md", ".txt", ".adoc", ".rst"}
SCRIPT_EXTS = {".sh", ".bat", ".ps1", ".cmd"}

BUILD_NAMES = {
    "cmakelists.txt",
    "makefile",
    "pom.xml",
    "build.gradle",
    "build.gradle.kts",
    "setup.py",
    "pyproject.toml",
    "meson.build",
}

GENERATED_MARKERS = ("/generated/", "/gen/", "/build/", "/target/", "/out/")
GENERATED_SUFFIXES = (".pb.h", ".pb.cc", ".g.cs")
GENERATED_PATTERNS = (
    re.compile(r"_generated\.[^/]+$", re.I),
    re.compile(r"Mapper\.xml$"),
)

TEST_PATTERNS = {
    "Java": (
        re.compile(r"(^|/)src/test/", re.I),
        re.compile(r"(^|/)src/it/", re.I),
        re.compile(r"(Test|Tests|IT|Spec)\.java$"),
    ),
    "Python": (
        re.compile(r"(^|/)tests?/", re.I),
        re.compile(r"^test_.*\.py$", re.I),
        re.compile(r".*_test\.py$", re.I),
        re.compile(r"^conftest\.py$", re.I),
    ),
    "C": (
        re.compile(r"(^|/)(test|tests|unittest)/", re.I),
        re.compile(r"_test\.c$", re.I),
    ),
    "C++": (
        re.compile(r"(^|/)(test|tests|unittest)/", re.I),
        re.compile(r"_test\.(cpp|cc|cxx)$", re.I),
        re.compile(r"Test\.(cpp|cc|cxx)$"),
        re.compile(r"_unittest\.cc$", re.I),
    ),
}


def _norm_path(path: str) -> str:
    return path.replace("\\", "/")


def _ext(path: str) -> str:
    name = path.rsplit("/", 1)[-1]
    dot = name.rfind(".")
    return name[dot:].lower() if dot > 0 else ""


def _language(path: str) -> str:
    return LANGUAGE_BY_EXT.get(_ext(path), "")


def _is_test(path: str) -> bool:
    language = _language(path)
    patterns = TEST_PATTERNS.get(language, ())
    return any(pattern.search(path) for pattern in patterns)


def _is_generated(path: str) -> bool:
    lower = "/" + path.lower().lstrip("/")
    if any(marker in lower for marker in GENERATED_MARKERS):
        return True
    if lower.endswith(GENERATED_SUFFIXES):
        return True
    return any(pattern.search(path) for pattern in GENERATED_PATTERNS)


def _exclude_category(path: str) -> str:
    ext = _ext(path)
    if ext in CONFIG_EXTS:
        return "配置 / 资源"
    if ext in DOC_EXTS:
        return "文档"
    if ext in SCRIPT_EXTS:
        return "脚本"
    return "其他语言"


def classify(path: str, *, is_binary: bool = False) -> tuple[str, str, str]:
    """按范围规则的优先级返回 ``(分类, 语言, 结论)``。"""
    name = path.rsplit("/", 1)[-1].lower()
    if is_binary:
        return "二进制 / 子模块", _language(path), "排除"
    if _is_generated(path):
        return "生成代码", _language(path), "排除"
    if name in BUILD_NAMES:
        return "构建文件", _language(path), "排除"

    language = _language(path)
    if language:
        if _is_test(path):
            return "测试代码", language, "纳入"
        return "生产代码", language, "纳入"
    category = _exclude_category(path)
    return category, "", "排除"


def analyze_scope(manifest_path: Path) -> dict[str, Any]:
    """读 manifest，返回范围表数据、排除计数与体量指标。"""
    if not manifest_path.is_file():
        raise AuditError(f"manifest 不存在：{manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise AuditError(f"manifest 不是合法 JSON：{exc}") from exc

    mrs = manifest.get("mrs")
    if not isinstance(mrs, list):
        raise AuditError("manifest 缺少 mrs 列表")

    warnings: list[str] = []
    files: list[dict[str, Any]] = []
    seen: dict[str, dict[str, Any]] = {}
    for mr in mrs:
        url = str(mr.get("web_url", ""))
        for raw in mr.get("files") or []:
            path = _norm_path(str(raw.get("path", "")))
            if not path:
                continue
            if path in seen:
                previous = seen[path]
                previous["additions"] = max(previous["additions"], int(raw.get("additions") or 0))
                previous["deletions"] = max(previous["deletions"], int(raw.get("deletions") or 0))
                warnings.append(f"同一文件出现在多个 MR：{path}，按较大量计一次")
                continue
            category, language, verdict = classify(
                path, is_binary=bool(raw.get("is_binary"))
            )
            item = {
                "mr": url,
                "path": path,
                "old_path": raw.get("old_path"),
                "change_type": str(raw.get("change_type", "")),
                "language": language,
                "category": category,
                "verdict": verdict,
                "additions": int(raw.get("additions") or 0),
                "deletions": int(raw.get("deletions") or 0),
                "truncated": bool(raw.get("truncated")),
                "is_binary": bool(raw.get("is_binary")),
            }
            files.append(item)
            seen[path] = item

    excluded: dict[str, int] = {}
    for item in files:
        if item["verdict"] == "排除":
            excluded[item["category"]] = excluded.get(item["category"], 0) + 1

    production = [f for f in files if f["category"] == "生产代码"]
    tests = [f for f in files if f["category"] == "测试代码"]
    limited = [f for f in production if f["language"] in ("C", "C++")]
    countable = [
        f for f in production if f["language"] in ("Java", "Python")
    ]
    prod_change = sum(f["additions"] + f["deletions"] for f in countable)
    prod_additions = sum(f["additions"] for f in countable)
    metrics = {
        "生产改动行": prod_change,
        "变更生产代码行数": prod_additions,
        "KLOC": round(prod_additions / 1000.0, 3),
        "生产文件数": len(production),
        "测试文件数": len(tests),
        "纳入文件数": len(production) + len(tests),
        "C/C++ 受限文件数": len(limited),
    }
    return {
        "schema": "scope/1",
        "files": files,
        "counts": {"排除": excluded, **metrics},
        "metrics": metrics,
        "warnings": warnings,
    }


def run_scope(*, manifest: Path, out: Path | None = None) -> dict[str, Any]:
    payload = analyze_scope(manifest)
    if out is not None:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    return payload
