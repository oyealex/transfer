"""取数脚本与 Skill 之间的数据契约。

唯一来源：脚本返回值、Skill 解析、报告字段都以此为准。
改动这里必须同步 ``scripts/README.md``。

约定：

- 只放数据结构，不放实现。
- 字段一律可选显式 ``None``，不要省略键。
- 列表排序稳定：``mrs`` 按 ``mr_id``，``files`` 按 ``path``。
- 只给 MR 的首末两个提交点，不给完整提交列表——Skill 以 MR 为变更单元，不关心内部提交。
- 修改内容以落盘的 diff 文件为准，不进 JSON。
"""

from __future__ import annotations

from typing import Literal, TypedDict

SCHEMA_VERSION = "1.1"

ObjectType = Literal["SR", "AR"]
MrState = Literal["merged", "opened", "closed", "locked", "unknown"]
ChangeType = Literal["added", "modified", "deleted", "renamed", "copied"]


class RepoRef(TypedDict):
    """代码仓标识，用于在本地定位副本。"""

    project_path: str
    """平台规范路径，如 ``group/subgroup/repo``。"""

    web_url: str
    """仓的网页地址，如 ``https://git.corp/group/subgroup/repo``。"""


class CommitPoint(TypedDict):
    """MR 的首个或末个提交。只给定位点，不给完整提交列表。"""

    sha: str
    """完整 SHA。Skill 用它做 diff 的端点。"""

    short_sha: str
    """短 SHA，便于人读。"""


class MrRef(TypedDict):
    """一个 MR 的索引信息，不含任何修改内容。"""

    mr_id: str
    """仓内 MR 编号，统一按字符串返回。"""

    title: str
    web_url: str
    author: str
    """MR 作者名。报告 1.1 基本信息要列。"""

    repo: RepoRef
    target_branch: str
    state: MrState
    linked_via: str
    """``"SR"`` 或 ``"AR-1234"``。Skill 只原样记录，不解释 SR 与 AR 的层级关系。"""

    first_commit: CommitPoint | None
    """MR 的首个提交。diff 的基准由它的第一父提交推出。"""

    last_commit: CommitPoint | None
    """MR 的末个提交。diff 的头。"""


class SrMrIndex(TypedDict):
    """``fetch_sr_mr_index`` 的返回值。"""

    schema_version: str
    object_type: ObjectType
    object_id: str
    """归一化后的编号，如 ``SR-1234`` / ``AR-77``。"""

    fetched_at: str
    """ISO8601，带时区。"""

    mrs: list[MrRef]
    warnings: list[str]
    """单 MR 取数失败等非致命问题，逐条记录，不抛异常。"""


class MrDetailBatch(TypedDict):
    """``fetch_mr_details`` 的返回值。

    与 ``SrMrIndex`` 同构，只是入口从 SR/AR 编号换成了 MR 地址列表。
    """

    schema_version: str
    fetched_at: str
    """ISO8601，带时区。"""

    mrs: list[MrRef]
    warnings: list[str]
    """单个地址解析失败时逐条记录，其余地址照常返回。"""


class FileChange(TypedDict):
    """一个变更文件。diff 已落盘，这里只给清单与落盘位置。"""

    path: str
    old_path: str | None
    """重命名或复制时的原路径，其他情况为 ``None``。"""

    change_type: ChangeType
    is_binary: bool
    additions: int
    deletions: int
    diff_path: str | None
    """相对 ``out_dir`` 的 diff 文件路径；二进制、子模块或取数失败时为 ``None``。"""

    truncated: bool
    """平台返回的 diff 被截断时为真。"""


class MrChange(TypedDict):
    """一个 MR 的变更清单。"""

    mr_id: str
    web_url: str
    files: list[FileChange]


class MrChangeManifest(TypedDict):
    """``fetch_mr_changes`` 的返回值，同时原样写入 ``<out_dir>/manifest.json``。"""

    schema_version: str
    fetched_at: str
    out_dir: str
    """绝对路径。Skill 用它拼 diff 的完整路径。"""

    mrs: list[MrChange]
    warnings: list[str]
