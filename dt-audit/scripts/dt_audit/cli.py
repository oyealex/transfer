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

from .assemble import run_assemble
from .contracts import SCHEMA_VERSION
from .errors import AuditError, NotFoundError, UpstreamError
from .fetch import fetch_mr_changes
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details
from .render import RenderError, run_render
from .scan import run_scan
from .scope import run_scope
from .stats import EvidenceError, run_stats

DEFAULT_TEMPLATE = Path(__file__).resolve().parents[2] / "references" / "报告模板.md"


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


def _cmd_render(args: argparse.Namespace) -> dict[str, Any]:
    return run_render(
        template=Path(args.template),
        stats=Path(args.stats),
        evidence=Path(args.evidence),
        narrative=Path(args.narrative),
        out=Path(args.out) if args.out else None,
        check=args.check,
    )


def _cmd_scope(args: argparse.Namespace) -> dict[str, Any]:
    return run_scope(
        manifest=Path(args.manifest),
        out=Path(args.out) if args.out else None,
    )


def _cmd_scan(args: argparse.Namespace) -> dict[str, Any]:
    return run_scan(
        manifest=Path(args.manifest),
        out=Path(args.out) if args.out else None,
    )


def _cmd_assemble(args: argparse.Namespace) -> dict[str, Any]:
    return run_assemble(
        mode=args.mode,
        evidence=Path(args.evidence),
        manifest=Path(args.manifest) if args.manifest else None,
        scope=Path(args.scope) if args.scope else None,
        index=Path(args.index) if args.index else None,
        meta=Path(args.meta) if args.meta else None,
        object_type=args.object_type,
        object_id=args.object_id,
        tier=args.tier,
        shards=[Path(p) for p in args.shards],
        judges=[Path(p) for p in args.judges],
    )


def build_parser() -> argparse.ArgumentParser:
    parser = _Parser(prog="dt_audit", description="DT 质量审计取数脚本")
    parser.add_argument("--version", action="version", version=f"%(prog)s {SCHEMA_VERSION}")
    sub = parser.add_subparsers(dest="command", required=True)

    p_index = sub.add_parser("index", help="按 SR / AR 编号查关联 MR 清单（只给元数据）")
    p_index.add_argument("--id", required=True, help="SR 或 AR 编号，如 SR1234 / AR77")
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

    p_render = sub.add_parser("render", help="装配最终报告，并可回读校验")
    p_render.add_argument("--template", default=str(DEFAULT_TEMPLATE), help="报告模板路径")
    p_render.add_argument("--stats", required=True, help="stats.json 路径")
    p_render.add_argument("--evidence", required=True, help="evidence.md 路径")
    p_render.add_argument("--narrative", required=True, help="narrative.json 路径（判断类文案）")
    p_render.add_argument("--out", default=None, help="报告输出路径，默认与证据表同目录")
    p_render.add_argument("--check", action="store_true", help="回读校验，不通过则报错且不写文件")
    p_render.add_argument("--pretty", action="store_true", help="缩进输出")
    p_render.set_defaults(handler=_cmd_render)

    p_scope = sub.add_parser("scope", help="按范围规则分类变更文件，输出范围表与体量指标")
    p_scope.add_argument("--manifest", required=True, help="manifest.json 路径")
    p_scope.add_argument("--out", default=None, help="scope.json 输出路径")
    p_scope.add_argument("--pretty", action="store_true", help="缩进输出")
    p_scope.set_defaults(handler=_cmd_scope)

    p_scan = sub.add_parser("scan", help="扫测试 diff，输出方向原因的线索")
    p_scan.add_argument("--manifest", required=True, help="manifest.json 路径（diff 相对它解析）")
    p_scan.add_argument("--out", default=None, help="scan.json 输出路径")
    p_scan.add_argument("--pretty", action="store_true", help="缩进输出")
    p_scan.set_defaults(handler=_cmd_scan)

    p_assemble = sub.add_parser("assemble", help="生成证据表骨架、合并分片、回填裁定")
    p_assemble.add_argument("--mode", required=True, choices=("init", "merge", "judge"))
    p_assemble.add_argument("--evidence", required=True, help="evidence.md 路径（不存在时由 init 创建）")
    p_assemble.add_argument("--manifest", default=None, help="manifest.json 路径")
    p_assemble.add_argument("--scope", default=None, help="scope.json 路径；缺省则由 manifest 现算")
    p_assemble.add_argument("--index", default=None, help="index / mr 命令的 JSON 输出")
    p_assemble.add_argument("--meta", default=None, help="对象与变更单元的元数据 JSON")
    p_assemble.add_argument("--object-type", default="", help="SR / AR / MR")
    p_assemble.add_argument("--object-id", default="", help="SR1234 / AR77 / MR 地址；SR/AR 带分隔符会自动归一")
    p_assemble.add_argument("--tier", default="仅 diff", help="变更单元来源档位")
    p_assemble.add_argument("--shards", action="append", default=[], help="枚举片 JSON，可重复")
    p_assemble.add_argument("--judges", action="append", default=[], help="裁定片 JSON，可重复")
    p_assemble.add_argument("--pretty", action="store_true", help="缩进输出")
    p_assemble.set_defaults(handler=_cmd_assemble)

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
    except RenderError as exc:
        print(f"render error: {exc}", file=sys.stderr)
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
