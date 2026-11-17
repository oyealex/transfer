"""SR / AR → MR 索引取数。

实现 ``fetch_sr_mr_index``，这是检测对象为 SR 或 AR 时的唯一入口。
只给元数据，不给任何修改内容。
"""

from __future__ import annotations

from .contracts import SrMrIndex


def fetch_sr_mr_index(object_id: str) -> SrMrIndex:
    """按 SR 或 AR 编号查关联的 MR 清单。

    返回结构见 :class:`dt_audit.contracts.SrMrIndex`。

    实现要点：

    1. 编号以 ``SR-`` 或 ``AR-`` 开头，按前缀判断 ``object_type`` 并归一化 ``object_id``。
       调用方不关心 SR 与 AR 的层级关系，两者都解析成同一份 MR 清单。
    2. 同时解析直接挂 SR 的 MR 与挂在 AR 下的 MR，``linked_via`` 标明来源。
    3. 每个 MR 只返回首个与末个提交（``first_commit`` / ``last_commit``），
       不返回完整提交列表，也不返回任何 diff。
    4. 同一 MR 地址只出现一次；同仓不同 MR 各自一条。
    5. ``author`` 取 MR 作者名，报告 1.1 要列。
    6. 单个 MR 取数失败不抛异常，写入 ``warnings`` 后继续。
    7. ``mrs`` 按 ``mr_id`` 排序，保证同一输入多次调用结果稳定。

    Args:
        object_id: SR 或 AR 编号，如 ``"SR-1234"`` / ``"AR-77"``。

    Raises:
        NotFoundError: SR / AR 不存在。
        UpstreamError: 平台不可达或鉴权失败。

    异常定义见 :mod:`dt_audit.errors`，不要在 ``cli`` 里导入以免循环依赖。
    """
    raise NotImplementedError("fetch_sr_mr_index 尚未实现，见 scripts/README.md")
