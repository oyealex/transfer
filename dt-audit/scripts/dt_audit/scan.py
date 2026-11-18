"""方向原因的字面扫描。

只做**字面能判**的四条线索：新增禁用、断言被注释、断言弱化（疑似）、测试文件删除。
用例方法体留空、测试输入弱化需要读语义，留给 Skill。

输出是线索，不是结论：`计入` 一律填「待定」，由步骤 3 判断是否构成方向原因。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from .errors import AuditError
from .scope import classify

DISABLE_PATTERNS = (
    re.compile(r"@Disabled\b"),
    re.compile(r"@Ignore\b"),
    re.compile(r"@pytest\.mark\.skip(if)?\b"),
    re.compile(r"\bGTEST_SKIP\b"),
    re.compile(r"\bTEST_IGNORE\b"),
    re.compile(r"\bSKIP\s*\("),
)

ASSERTION = re.compile(r"\b(assert[A-Za-z]*|verify|expect|should|check)\s*\(")
STRONG = re.compile(
    r"\b(assertEquals|assertNotEquals|assertSame|assertThat|assertArrayEquals)\s*\("
)
WEAK = re.compile(
    r"\b(assertNotNull|assertNull|assertTrue|assertFalse|assertDoesNotThrow)\s*\("
)
BROAD_THROW = re.compile(r"assertThrows\s*\(\s*(Exception|Throwable)\s*\.class")

HUNK = re.compile(r"^@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")
COMMENT_PREFIXES = ("//", "#", "/*", "*", "<!--", "--")


def _strip_comment(text: str) -> str:
    stripped = text.strip()
    for prefix in COMMENT_PREFIXES:
        if stripped.startswith(prefix):
            return stripped[len(prefix) :].strip()
    return stripped


def _is_comment(text: str) -> bool:
    return text.strip().startswith(COMMENT_PREFIXES)


def _normalize(text: str) -> str:
    return re.sub(r"\s+", "", text)


def scan_hunks(text: str) -> list[dict[str, list[tuple[int, str]]]]:
    """按 hunk 切分 diff，记录新增/删除行及其行号。"""
    hunks: list[dict[str, list[tuple[int, str]]]] = []
    current: dict[str, list[tuple[int, str]]] | None = None
    new_line = old_line = 0
    for raw in text.splitlines():
        match = HUNK.match(raw)
        if match:
            old_line = int(raw.split(" ")[1].split(",")[0].lstrip("-"))
            new_line = int(match.group(2))
            current = {"added": [], "removed": []}
            hunks.append(current)
            continue
        if current is None or raw.startswith("+++") or raw.startswith("---"):
            continue
        if raw.startswith("+"):
            current["added"].append((new_line, raw[1:]))
            new_line += 1
        elif raw.startswith("-"):
            current["removed"].append((old_line, raw[1:]))
            old_line += 1
        elif raw.startswith(" "):
            new_line += 1
            old_line += 1
    return hunks


def _scan_file(path: str, diff_text: str) -> list[dict[str, str]]:
    found: list[dict[str, str]] = []
    for hunk in scan_hunks(diff_text):
        added, removed = hunk["added"], hunk["removed"]

        for line_no, text in added:
            if any(pattern.search(text) for pattern in DISABLE_PATTERNS):
                found.append(
                    {
                        "类型": "测试退化",
                        "信号": "新增禁用",
                        "位置": f"{path}:{line_no}",
                        "引用生产符号": "",
                        "计入": "待定",
                        "说明": f"新增行含禁用标记：{text.strip()[:80]}",
                    }
                )

        removed_assertions = {
            _normalize(_strip_comment(text)): (line, text)
            for line, text in removed
            if ASSERTION.search(text)
        }
        for line_no, text in added:
            if not _is_comment(text):
                continue
            body = _normalize(_strip_comment(text))
            hit = removed_assertions.get(body)
            if hit:
                old_line, old_text = hit
                found.append(
                    {
                        "类型": "测试退化",
                        "信号": "断言被注释",
                        "位置": f"{path}:{line_no}",
                        "引用生产符号": "",
                        "计入": "待定",
                        "说明": f"原断言在第 {old_line} 行，新增侧被注释：{old_text.strip()[:80]}",
                    }
                )

        for old_line, old_text in removed:
            strong = bool(STRONG.search(old_text)) or (
                "assertThrows" in old_text and not BROAD_THROW.search(old_text)
            )
            if not strong:
                continue
            for new_line, new_text in added:
                weak = bool(WEAK.search(new_text)) or bool(BROAD_THROW.search(new_text))
                if not weak:
                    continue
                found.append(
                    {
                        "类型": "测试退化",
                        "信号": "断言弱化",
                        "位置": f"{path}:{new_line}",
                        "引用生产符号": "",
                        "计入": "待定",
                        "说明": (
                            f"删除侧第 {old_line} 行偏精确，新增侧改为宽泛断言："
                            f"{new_text.strip()[:80]}（疑似，需人工确认）"
                        ),
                    }
                )
                break
    return found


def scan_manifest(manifest_path: Path) -> dict[str, Any]:
    if not manifest_path.is_file():
        raise AuditError(f"manifest 不存在：{manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise AuditError(f"manifest 不是合法 JSON：{exc}") from exc

    root = manifest_path.parent
    warnings: list[str] = []
    candidates: list[dict[str, str]] = []
    deleted_tests: list[str] = []
    added_tests: list[str] = []

    for mr in manifest.get("mrs") or []:
        for raw in mr.get("files") or []:
            path = str(raw.get("path", "")).replace("\\", "/")
            if not path:
                continue
            category, _language, _verdict = classify(
                path, is_binary=bool(raw.get("is_binary"))
            )
            if category != "测试代码":
                continue
            change_type = str(raw.get("change_type", ""))
            if change_type == "deleted":
                deleted_tests.append(path)
                continue
            if change_type == "added":
                added_tests.append(path)
            diff_path = raw.get("diff_path")
            if not diff_path:
                continue
            diff_file = root / str(diff_path)
            if not diff_file.is_file():
                warnings.append(f"diff 不存在，跳过扫描：{diff_file}")
                continue
            candidates.extend(
                _scan_file(path, diff_file.read_text(encoding="utf-8", errors="replace"))
            )

    for path in deleted_tests:
        if added_tests:
            note = f"测试文件被删，但本次新增了 {len(added_tests)} 个测试文件，按规则只作提示"
        else:
            note = "测试文件被删，且本次没有新增测试文件"
        candidates.append(
            {
                "类型": "测试退化",
                "信号": "测试文件删除",
                "位置": path,
                "引用生产符号": "",
                "计入": "待定",
                "说明": note,
            }
        )

    return {"candidates": candidates, "warnings": warnings}


def run_scan(*, manifest: Path, out: Path | None = None) -> dict[str, Any]:
    payload = scan_manifest(manifest)
    if out is not None:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    return payload
