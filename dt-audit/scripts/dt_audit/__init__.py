"""DT 质量审计脚本。

对外暴露四个入口：

- :func:`fetch_sr_mr_index` —— SR / AR 关联的 MR 清单（只给元数据）
- :func:`fetch_mr_details` —— 按 MR 地址批量查元数据
- :func:`fetch_mr_changes` —— 本地仓不可用时，把修改内容落盘到临时目录
- :func:`run_stats` —— 解析证据表、算指标、校验一致性

数据结构定义在 :mod:`dt_audit.contracts`。
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
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details
from .stats import EvidenceError, run_stats

__all__ = [
    "fetch_sr_mr_index",
    "fetch_mr_details",
    "fetch_mr_changes",
    "run_stats",
    "EvidenceError",
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
