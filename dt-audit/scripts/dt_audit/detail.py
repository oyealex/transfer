"""MR 变更详情取数（兜底）。

只在本地代码仓不可达时使用。Skill 优先用 ``git`` 从本地仓取 diff。
"""

from __future__ import annotations

from .contracts import MrChangeDetail


def fetch_mr_change_detail(
    mr_id: str,
    *,
    project_path: str | None = None,
    context_lines: int = 10,
    max_file_bytes: int = 2_000_000,
) -> MrChangeDetail:
    """通过平台接口取 MR 的文件级变更。

    返回结构见 :class:`dt_audit.contracts.MrChangeDetail`。

    实现要点：

    1. ``diff`` 必须是 unified 格式，上下文取 ``context_lines``，至少给 10 行。
    2. 二进制文件、子模块、超限文件把 ``truncated`` 或 ``is_binary`` 置真，``diff`` 置 ``None``。
    3. 重命名与复制要给 ``old_path``。
    4. ``files`` 按 ``path`` 排序，保证结果稳定。
    5. 单个文件取数失败写入 ``warnings``，不影响其他文件。

    这条路径的输出会被标为「平台兜底」来源：上下文受限、可能截断，Skill 只做
    文件级与字面级信号，不做需要完整文件的语义比对。

    Args:
        mr_id: 仓内 MR 编号。
        project_path: 仓的规范路径，用于区分同名 MR。
        context_lines: unified diff 的上下文行数。
        max_file_bytes: 单文件 diff 上限，超过则截断。

    Raises:
        NotFoundError: MR 不存在。
        UpstreamError: 平台不可达或鉴权失败。
    """
    raise NotImplementedError("fetch_mr_change_detail 尚未实现，见 scripts/README.md")
