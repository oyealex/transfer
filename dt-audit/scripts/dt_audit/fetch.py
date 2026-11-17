"""MR 修改内容取数。

两种输入都走这里：把每个 MR 的改动落成 ``manifest.json`` + 逐文件 unified diff，
Skill 以这两份东西为主要证据。本地仓可不可达只决定 Skill 还能额外读多少上下文，
不改变改动内容的来源。

设计取舍：

- **diff 写文件，不塞 stdout**。一个 MR 的 diff 动辄几十 KB，塞进 JSON 会撑爆上下文，
  也没法按文件定位。stdout 只回清单，Skill 需要哪份 diff 再读哪份。
- **不补算 diff**。平台给多少上下文就落多少，不为了「至少 N 行上下文」去拉两端文件重算——
  那会让脚本依赖完整文件，而完整文件正是这里拿不到的东西。
- **清单与落盘一致**。``manifest.json`` 与返回值是同一份内容，Skill 读哪个都一样。
"""

from __future__ import annotations

from pathlib import Path
from typing import Sequence

from .contracts import MrChangeManifest


def fetch_mr_changes(mr_urls: Sequence[str], out_dir: str | Path) -> MrChangeManifest:
    """批量取 MR 的修改内容，逐文件写 diff 到 ``out_dir``。

    返回结构见 :class:`dt_audit.contracts.MrChangeManifest`。

    目录结构：

    ::

        <out_dir>/
        ├── manifest.json                 # 与返回值同构
        └── <mr_id>/
            └── <扁平化路径>.diff          # 逐个文件的 unified diff

    实现要点：

    1. **基准必须与平台 MR diff 一致**。取平台记录的 ``base_sha``（GitLab 的
       ``diff_refs.base_sha``）做基准；拿不到时用 ``merge-base(目标分支, 末提交)``。
       已合并的 MR 要特别小心：head 若已成为目标分支的祖先，用当前目标尖端重算
       merge-base 会得到**空 diff**，必须用平台记录的 base_sha，或从合并提交的第一父
       反推「合并前的目标尖端」。推不出来的 MR 写进 ``warnings``，不要静默产出空 diff。
    2. ``manifest.json`` 按 :class:`MrChangeManifest` 写，字段与返回值一致。
       它是 Skill 侧文件清单的唯一来源，每个变更文件都必须出现在里面。
    3. 扁平化规则：路径分隔符换成 ``__``；超过 120 字符或重名时追加序号。
       同一 MR 内路径唯一，不同 MR 分目录，不会互相覆盖。
    4. diff 用 unified 格式，**平台给多少上下文就保留多少**（通常是 3 行），不补算。
    5. 二进制、子模块：``is_binary`` 置真，``diff_path`` 置 ``None``。
    6. 平台截断的超大 diff：``truncated`` 置真，仍落盘已拿到的部分。
    7. 重命名与复制给 ``old_path``；删除的文件也落盘，``change_type`` 为 ``deleted``。
    8. 单个文件或单个 MR 失败写入 ``warnings``，不影响其他 MR。
    9. 同一输入两次调用，``manifest.json`` 除 ``fetched_at`` 外字节级一致。

    Args:
        mr_urls: MR 地址列表，如
            ``"https://git.corp/group/repo/-/merge_requests/1234"``。
        out_dir: 落盘目录，通常是 ``.dt-audit/<对象>/_diffs``。

    Raises:
        NotFoundError: 全部地址无法解析或查询不到。
        UpstreamError: 平台不可达或鉴权失败。

    异常定义见 :mod:`dt_audit.errors`。
    """
    raise NotImplementedError("fetch_mr_changes 尚未实现，见 scripts/README.md")
