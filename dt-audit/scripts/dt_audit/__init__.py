"""DT 质量审计脚本。

对外暴露三个入口：

- :func:`fetch_sr_mr_index` —— SR 关联的 MR 索引（只有 SR 走这条）
- :func:`fetch_mr_details` —— 按 MR 地址批量取详情（MR 批次走这条）
- :func:`run_stats` —— 解析证据表、算指标、校验一致性（步骤 5 走这条）

数据结构定义在 :mod:`dt_audit.contracts`。详细修改内容只从本地仓取，
本包不通过平台接口取 diff。
"""

from __future__ import annotations

from .contracts import (
    CommitRef,
    MrDetailBatch,
    MrRef,
    RepoRef,
    SCHEMA_VERSION,
    SrMrIndex,
)
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details
from .stats import EvidenceError, run_stats

__all__ = [
    "fetch_sr_mr_index",
    "fetch_mr_details",
    "run_stats",
    "EvidenceError",
    "SCHEMA_VERSION",
    "CommitRef",
    "MrDetailBatch",
    "MrRef",
    "RepoRef",
    "SrMrIndex",
]
