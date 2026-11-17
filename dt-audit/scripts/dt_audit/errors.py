"""脚本层的异常类型。

放在独立模块里，是为了让取数模块（``index`` / ``mr`` / ``fetch``）与命令行入口
都能从低层导入：如果异常定义在 ``cli``，而 ``cli`` 又导入这三个模块，就会形成
循环依赖。取数模块抛这些异常，``cli.main`` 负责映射成退出码。
"""

from __future__ import annotations


class AuditError(Exception):
    """脚本可预期错误的基类。"""

    exit_code = 1


class NotFoundError(AuditError):
    """SR / AR / MR / 仓不存在。"""

    exit_code = 2


class UpstreamError(AuditError):
    """平台不可达或鉴权失败。"""

    exit_code = 3
