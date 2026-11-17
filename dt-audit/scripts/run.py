#!/usr/bin/env python
"""``dt_audit`` 启动器。

用法：

    python run.py index  --id SR-1234 [--pretty]
    python run.py mr     --url <MR地址> [--url ...] [--pretty]
    python run.py fetch  --url <MR地址> [--url ...] --out .dt-audit/<对象>/_diffs [--pretty]
    python run.py stats  --evidence .dt-audit/SR-1234/evidence.md [--out stats.json] [--pretty]
    python run.py render --stats .dt-audit/SR-1234/stats.json --evidence .dt-audit/SR-1234/evidence.md \
                          [--manifest .dt-audit/SR-1234/_diffs/manifest.json] \
                          --narrative .dt-audit/SR-1234/narrative.json [--out 报告.md] [--check] [--pretty]

stdout 只输出 JSON；人读信息走 stderr。
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from dt_audit.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
