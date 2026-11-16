"""DT 质量审计取数脚本。

对外只暴露三个方法：

- :func:`fetch_sr_mr_index` —— SR 关联的 MR 索引（只有 SR 走这条）
- :func:`fetch_mr_details` —— 按 MR 地址批量取详情（MR 批次走这条）
- :func:`fetch_mr_change_detail` —— 本地仓不可达时的文件级变更兜底

数据结构定义在 :mod:`dt_audit.contracts`。
"""

from __future__ import annotations

from .contracts import (
    CommitRef,
    FileChange,
    MrChangeDetail,
    MrDetailBatch,
    MrRef,
    RepoRef,
    SCHEMA_VERSION,
    SrMrIndex,
)
from .detail import fetch_mr_change_detail
from .index import fetch_sr_mr_index
from .mr import fetch_mr_details

__all__ = [
    "fetch_sr_mr_index",
    "fetch_mr_details",
    "fetch_mr_change_detail",
    "SCHEMA_VERSION",
    "CommitRef",
    "FileChange",
    "MrChangeDetail",
    "MrDetailBatch",
    "MrRef",
    "RepoRef",
    "SrMrIndex",
]
