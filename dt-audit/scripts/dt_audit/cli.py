"""``dt_audit`` 命令行入口。

对外契约：

- stdout 只输出 JSON，人读信息走 stderr。
- 退出码 0 表示成功（``warnings`` 可非空），1 表示校验或内部失败，
  2 表示未找到，3 表示上游不可达，64 表示命令行参数错误。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, NoReturn, Sequence

from .contracts import SCHEMA_VERSION
from .fetch import fetch_mr_changes
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details
from .stats import EvidenceError, run_stats


class AuditError(Exception):
    """脚本可预期错误的基类。"""

    exit_code = 1


class NotFoundError(AuditError):
    """SR / AR / MR / 仓不存在。"""

    exit_code = 2


class UpstreamError(AuditError):
    """平台不可达或鉴权失败。"""

    exit_code = 3


class _Parser(argparse.ArgumentParser):
    """参数错误走独立退出码 64，避免与「未找到」的 2 冲突。"""

    def error(self, message: str) -> NoReturn:
        self.print_usage(sys.stderr)
        print(f"usage error: {message}", file=sys.stderr)
        raise SystemExit(64)


def _force_utf8() -> None:
    """Windows 控制台默认不是 UTF-8，中文 JSON 会乱码或抛 UnicodeEncodeError。"""
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8")


def _cmd_index(args: argparse.Namespace) -> dict[str, Any]:
    return dict(fetch_sr_mr_index(args.id))


def _cmd_mr(args: argparse.Namespace) -> dict[str, Any]:
    return dict(fetch_mr_details(args.url))


def _cmd_fetch(args: argparse.Namespace) -> dict[str, Any]:
    return dict(fetch_mr_changes(args.url, Path(args.out)))


def _cmd_stats(args: argparse.Namespace) -> dict[str, Any]:
    return dict(
        run_stats(
            evidence=Path(args.evidence),
            out=Path(args.out) if args.out else None,
        )
    )


def build_parser() -> argparse.ArgumentParser:
    parser = _Parser(prog="dt_audit", description="DT 质量审计取数脚本")
    parser.add_argument("--version", action="version", version=f"%(prog)s {SCHEMA_VERSION}")
    sub = parser.add_subparsers(dest="command", required=True)

    p_index = sub.add_parser("index", help="按 SR / AR 编号查关联 MR 清单（只给元数据）")
    p_index.add_argument("--id", required=True, help="SR 或 AR 编号，如 SR-1234 / AR-77")
    p_index.add_argument("--pretty", action="store_true", help="缩进输出")
    p_index.set_defaults(handler=_cmd_index)

    p_mr = sub.add_parser("mr", help="按 MR 地址查元数据（只给元数据）")
    p_mr.add_argument("--url", action="append", required=True, help="MR 地址，可重复")
    p_mr.add_argument("--pretty", action="store_true", help="缩进输出")
    p_mr.set_defaults(handler=_cmd_mr)

    p_fetch = sub.add_parser("fetch", help="批量取 MR 修改内容，diff 落盘")
    p_fetch.add_argument("--url", action="append", required=True, help="MR 地址，可重复")
    p_fetch.add_argument("--out", required=True, help="落盘目录，如 .dt-audit/<对象>/_diffs")
    p_fetch.add_argument("--pretty", action="store_true", help="缩进输出")
    p_fetch.set_defaults(handler=_cmd_fetch)

    p_stats = sub.add_parser("stats", help="解析证据表，算指标并校验")
    p_stats.add_argument("--evidence", required=True, help="evidence.md 路径")
    p_stats.add_argument("--out", default=None, help="stats.json 输出路径，默认与证据表同目录")
    p_stats.add_argument("--pretty", action="store_true", help="缩进输出")
    p_stats.set_defaults(handler=_cmd_stats)

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    _force_utf8()
    args = build_parser().parse_args(argv)

    try:
        payload = args.handler(args)
    except NotImplementedError as exc:
        print(f"not implemented: {exc}", file=sys.stderr)
        return 1
    except EvidenceError as exc:
        print(f"evidence error: {exc}", file=sys.stderr)
        return 1
    except NotFoundError as exc:
        print(f"not found: {exc}", file=sys.stderr)
        return exc.exit_code
    except UpstreamError as exc:
        print(f"upstream error: {exc}", file=sys.stderr)
        return exc.exit_code
    except AuditError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return exc.exit_code
    except Exception as exc:  # noqa: BLE001 - 命令行边界，统一转成退出码
        print(f"error: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1

    json.dump(
        payload,
        sys.stdout,
        ensure_ascii=False,
        indent=2 if getattr(args, "pretty", False) else None,
    )
    sys.stdout.write("\n")
    return 0
