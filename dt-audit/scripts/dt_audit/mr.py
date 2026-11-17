"""MR 元数据取数。

检测对象直接是一个 MR 地址时走这条。只给元数据，不给修改内容。
"""

from __future__ import annotations

from typing import Sequence

from .contracts import MrDetailBatch


def fetch_mr_details(mr_urls: Sequence[str]) -> MrDetailBatch:
    """按 MR 地址批量查元数据。

    返回结构见 :class:`dt_audit.contracts.MrDetailBatch`。

    实现要点：

    1. 从地址解析出平台、仓路径、MR 编号，支持 GitLab、GitHub、Gitee 的常见地址形态。
    2. 每个 MR 只返回首个与末个提交（``first_commit`` / ``last_commit``），不返回完整提交列表。
    3. 返回的 ``MrRef`` 与 SR/AR 路径完全同构，Skill 后续流程不做区分。
    4. 单条失败不抛异常，写入 ``warnings`` 后继续；全部失败才抛 ``NotFoundError``。
    5. ``mrs`` 按 ``mr_id`` 排序，保证同一输入多次调用结果稳定。

    Args:
        mr_urls: MR 地址列表，如
            ``"https://git.corp/group/repo/-/merge_requests/1234"``。

    Raises:
        NotFoundError: 全部地址无法解析或查询不到。
        UpstreamError: 平台不可达或鉴权失败。
    """
    raise NotImplementedError("fetch_mr_details 尚未实现，见 scripts/README.md")
