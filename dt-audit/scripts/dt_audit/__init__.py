"""DT 质量审计脚本。

对外暴露五个入口：

- :func:`fetch_sr_mr_index` —— SR / AR 关联的 MR 清单（只给元数据）
- :func:`fetch_mr_details` —— 按 MR 地址批量查元数据
- :func:`fetch_mr_changes` —— 取 MR 修改内容，把 manifest 与 diff 落盘
- :func:`run_stats` —— 解析证据表、算指标、校验一致性
- :func:`run_render` —— 装配报告并做回读校验；:func:`render_report` 是它的纯函数内核

两种输入（SR / AR 编号、MR 地址）在 :func:`fetch_mr_changes` 之后完全一致：
改动一律由它产出，本地仓只用于补读上下文。

数据结构定义在 :mod:`dt_audit.contracts`，异常定义在 :mod:`dt_audit.errors`。
"""

from __future__ import annotations

from .contracts import (
    CommitPoint,
    FileChange,
    MrChange,
    MrChangeManifest,
    MrDetailBatch,
    MrRef,
    RepoRef,
    SCHEMA_VERSION,
    SrMrIndex,
)
from .fetch import fetch_mr_changes
from .errors import AuditError, NotFoundError, UpstreamError
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details
from .render import RenderError, render_report, run_render
from .stats import EvidenceError, run_stats

__all__ = [
    "fetch_sr_mr_index",
    "fetch_mr_details",
    "fetch_mr_changes",
    "run_stats",
    "run_render",
    "render_report",
    "EvidenceError",
    "RenderError",
    "AuditError",
    "NotFoundError",
    "UpstreamError",
    "SCHEMA_VERSION",
    "CommitPoint",
    "FileChange",
    "MrChange",
    "MrChangeManifest",
    "MrDetailBatch",
    "MrRef",
    "RepoRef",
    "SrMrIndex",
]
