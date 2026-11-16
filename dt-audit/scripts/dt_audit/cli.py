"""``dt_audit`` 命令行入口。

对外契约：
- stdout 只输出 JSON，人读信息走 stderr。
- 退出码 0 表示成功（``warnings`` 可非空），2 表示未找到，3 表示上游不可达。
"""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any, NoReturn, Sequence

from .contracts import SCHEMA_VERSION
from .detail import fetch_mr_change_detail
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details


class AuditError(Exception):
    """脚本可预期错误的基类。"""

    exit_code = 1


class NotFoundError(AuditError):
    """SR / MR / 仓不存在。"""

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
    return dict(fetch_sr_mr_index(args.sr))


def _cmd_mr(args: argparse.Namespace) -> dict[str, Any]:
    return dict(fetch_mr_details(args.url))


def _cmd_detail(args: argparse.Namespace) -> dict[str, Any]:
    return dict(
        fetch_mr_change_detail(
            args.mr,
            project_path=args.project,
            context_lines=args.context,
        )
    )


def build_parser() -> argparse.ArgumentParser:
    parser = _Parser(prog="dt_audit", description="DT 质量审计取数脚本")
    parser.add_argument("--version", action="version", version=f"%(prog)s {SCHEMA_VERSION}")
    sub = parser.add_subparsers(dest="command", required=True)

    p_index = sub.add_parser("index", help="查询 SR 关联的 MR 索引")
    p_index.add_argument("--sr", required=True, help="SR 编号，如 SR-1234")
    p_index.add_argument("--pretty", action="store_true", help="缩进输出")
    p_index.set_defaults(handler=_cmd_index)

    p_mr = sub.add_parser("mr", help="按 MR 地址批量查询详情")
    p_mr.add_argument("--url", action="append", required=True, help="MR 地址，可重复")
    p_mr.add_argument("--pretty", action="store_true", help="缩进输出")
    p_mr.set_defaults(handler=_cmd_mr)

    p_detail = sub.add_parser("detail", help="本地仓不可达时取 MR 文件级变更")
    p_detail.add_argument("--mr", required=True, help="MR 编号")
    p_detail.add_argument("--project", default=None, help="仓的规范路径")
    p_detail.add_argument("--context", type=int, default=10, help="diff 上下文行数")
    p_detail.add_argument("--pretty", action="store_true", help="缩进输出")
    p_detail.set_defaults(handler=_cmd_detail)

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    _force_utf8()
    args = build_parser().parse_args(argv)

    try:
        payload = args.handler(args)
    except NotImplementedError as exc:
        print(f"not implemented: {exc}", file=sys.stderr)
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
