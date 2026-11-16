"""取数脚本与 Skill 之间的数据契约。

唯一来源：脚本返回值、Skill 解析、报告字段都以此为准。
改动这里必须同步 ``scripts/README.md``。

约定：
- 只放数据结构，不放实现。
- 字段一律可选显式 ``None``，不要省略键。
- 列表排序稳定：``mrs`` 按 ``mr_id``，``files`` 按 ``path``，``commits`` 按拓扑序。
"""

from __future__ import annotations

from typing import Literal, TypedDict

SCHEMA_VERSION = "1.0"

MrState = Literal["merged", "opened", "closed", "locked", "unknown"]


class RepoRef(TypedDict):
    """代码仓标识，用于在本地定位副本。"""

    project_path: str
    """平台规范路径，如 ``group/subgroup/repo``。"""

    web_url: str
    """仓的网页地址，如 ``https://git.corp/group/subgroup/repo``。"""


class CommitRef(TypedDict):
    """MR 内的一个提交。"""

    sha: str
    """完整 SHA。"""

    short_sha: str
    """短 SHA，便于人读。"""

    message: str
    """完整 commit message，含 body。Skill 用它判断需求归属。"""

    committed_at: str
    """ISO8601，带时区。仅作参考，不要用于排序。"""

    author_name: str
    parents: list[str]
    """父提交 SHA。用于定位改动前版本、识别 merge commit。"""


class MrRef(TypedDict):
    """一个 MR 的索引信息，不含 diff。"""

    mr_id: str
    """仓内 MR 编号，统一按字符串返回。"""

    title: str
    web_url: str
    repo: RepoRef
    target_branch: str
    state: MrState
    linked_via: str
    """``"SR"`` 或 ``"AR-1234"``，标明这个 MR 挂在哪一级。"""

    base_sha: str | None
    """MR 的 diff 基准。平台能给出时必须返回：它决定证据来源是「本地精确」还是「本地推导」。"""

    head_sha: str | None
    """MR 最后一个提交。"""

    commits: list[CommitRef]
    """按拓扑序，父在前。"""


class SrMrIndex(TypedDict):
    """``fetch_sr_mr_index`` 的返回值。"""

    schema_version: str
    sr_id: str
    fetched_at: str
    """ISO8601，带时区。"""

    mrs: list[MrRef]
    warnings: list[str]
    """单 MR 取数失败等非致命问题，逐条记录，不抛异常。"""


class MrDetailBatch(TypedDict):
    """``fetch_mr_details`` 的返回值。

    与 ``SrMrIndex`` 同构，只是入口从 SR 编号换成了 MR 地址列表。
    """

    schema_version: str
    fetched_at: str
    """ISO8601，带时区。"""

    mrs: list[MrRef]
    warnings: list[str]
    """单个地址解析失败时逐条记录，其余地址照常返回。"""
