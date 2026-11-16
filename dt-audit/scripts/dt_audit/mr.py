"""MR 批次取数。

按 MR 地址批量查询详情，用于检测对象是「一批 MR」的场景。
这条路径不经过 SR，因此不调用 ``fetch_sr_mr_index``。
"""

from __future__ import annotations

from typing import Sequence

from .contracts import MrDetailBatch


def fetch_mr_details(mr_urls: Sequence[str]) -> MrDetailBatch:
    """按 MR 地址批量查询详情。

    返回结构见 :class:`dt_audit.contracts.MrDetailBatch`。

    实现要点：

    1. 从地址解析出平台、仓路径、MR 编号，支持 GitLab、GitHub、Gitee 的常见地址形态。
    2. 逐条查询元数据：标题、状态、目标分支、基准提交、头提交、提交列表。
    3. 返回的 ``MrRef`` 与 SR 路径完全同构，Skill 后续流程不做区分。
    4. ``commits`` 按拓扑序，带完整 message。
    5. 单条失败不抛异常，写入 ``warnings`` 后继续；全部失败才抛 ``NotFoundError``。
    6. ``mrs`` 按 ``mr_id`` 排序，保证同一输入多次调用结果稳定。

    Args:
        mr_urls: MR 地址列表，如
            ``"https://git.corp/group/repo/-/merge_requests/1234"``。

    Raises:
        NotFoundError: 全部地址无法解析或查询不到。
        UpstreamError: 平台不可达或鉴权失败。
    """
    raise NotImplementedError("fetch_mr_details 尚未实现，见 scripts/README.md")
