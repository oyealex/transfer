"""SR → MR 索引取数。

实现 ``fetch_sr_mr_index``，这是 Skill 的默认入口。
"""

from __future__ import annotations

from .contracts import SrMrIndex


def fetch_sr_mr_index(
    sr_id: str,
    *,
    include_states: tuple[str, ...] = ("merged", "opened", "closed", "locked"),
) -> SrMrIndex:
    """查询 SR 关联的全部 MR（含挂在 AR 下的 MR），不返回 diff 内容。

    返回结构见 :class:`dt_audit.contracts.SrMrIndex`。

    实现要点：

    1. 同时解析直接挂 SR 和挂 AR 的 MR，``linked_via`` 标明来源。
    2. 同一仓库的 MR 只出现一次；跨仓 MR 各自一条。
    3. ``commits`` 按拓扑序（父在前），带完整 message，供 Skill 判断需求归属。
    4. 平台能给出 ``base_sha`` 时必须返回，它决定证据来源是「本地精确」还是「本地推导」。
    5. 单个 MR 取数失败不抛异常，写入 ``warnings`` 后继续。
    6. ``mrs`` 按 ``mr_id`` 排序，保证同一 SR 多次调用结果稳定。

    Args:
        sr_id: SR 编号，如 ``"SR-1234"``。
        include_states: 需要返回的 MR 状态。默认全量，由 Skill 侧过滤。

    Raises:
        NotFoundError: SR 不存在。
        UpstreamError: 平台不可达或鉴权失败。
    """
    raise NotImplementedError("fetch_sr_mr_index 尚未实现，见 scripts/README.md")
