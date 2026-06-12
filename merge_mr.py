#!/usr/bin/env python3
"""
MR 处理脚本骨架。

两种模式：
  - normal:   普通模式 — 加分/评审 → 关 issue → 合并
  - inspect:  巡检项模式 — 额外检查特定用户是否已打分，否则 IM 通知并终止

用法：
  python merge_mr.py --mode normal  --mr-id MR-12345
  python merge_mr.py --mode inspect --mr-id MR-12345
"""

import argparse
import sys
from dataclasses import dataclass, field
from enum import Enum


# ── 硬编码配置（骨架阶段）─────────────────────────────────────────────

MR_SESSION_COOKIE = "PLACEHOLDER_COOKIE"

INSPECT_REVIEWER_IDS = {"ABC", "DEF"}

IM_MESSAGE_TEMPLATE = "请找 XXX 检视打分"


# ── 数据模型 ───────────────────────────────────────────────────────────

class MRMode(Enum):
    SCORE = "score"
    REVIEW = "review"


@dataclass
class Issue:
    issue_id: str
    title: str
    status: str          # "open" | "closed" | "resolved"
    author: str


@dataclass
class ScoreDetail:
    reviewer_id: str
    score: int


@dataclass
class MR:
    mr_id: str
    url: str
    type: str            # 平台原始类型，由 determine_mode() 映射到 MRMode
    title: str
    status: str
    author_id: str
    issues: list[Issue] = field(default_factory=list)


# ── Session ────────────────────────────────────────────────────────────

def get_session() -> "requests.Session":
    """获取包含 cookie 的 requests Session。"""
    # TODO: 实现 cookie 注入
    raise NotImplementedError


# ── MR 查询 ────────────────────────────────────────────────────────────

def query_mr_detail(session: "requests.Session", mr_id: str) -> MR:
    """查询 MR 详情，返回 MR 对象。"""
    # TODO: 调用平台 API 获取 MR 数据
    raise NotImplementedError


def determine_mode(mr: MR) -> MRMode:
    """根据自定义规则判断 MR 处理模式。"""
    # TODO: 实现规则逻辑
    raise NotImplementedError


# ── 打分 / 评审操作 ────────────────────────────────────────────────────

def add_score(session: "requests.Session", mr: MR) -> None:
    """给 MR 增加 2 分。"""
    # TODO: 调用平台打分 API
    raise NotImplementedError


def approve_review(session: "requests.Session", mr: MR) -> None:
    """批准 MR 检视。"""
    # TODO: 调用平台检视 API
    raise NotImplementedError


def approve_audit(session: "requests.Session", mr: MR) -> None:
    """批准 MR 审核。"""
    # TODO: 调用平台审核 API
    raise NotImplementedError


# ── Issue 处理 ──────────────────────────────────────────────────────────

def filter_open_issues(issues: list[Issue]) -> list[Issue]:
    """过滤出未处理的 issue。"""
    return [i for i in issues if i.status == "open"]


def close_issue(session: "requests.Session", issue: Issue) -> None:
    """关闭单个 issue。"""
    # TODO: 调用平台关闭 issue API
    raise NotImplementedError


# ── 合并 ───────────────────────────────────────────────────────────────

def is_mergeable(session: "requests.Session", mr: MR) -> bool:
    """检查 MR 是否处于可合并状态。"""
    # TODO: 调用平台状态检查 API
    raise NotImplementedError


def merge_mr(session: "requests.Session", mr: MR) -> None:
    """合并 MR。"""
    # TODO: 调用平台合并 API
    raise NotImplementedError


# ── 巡检项：打分检查 ───────────────────────────────────────────────────

def query_score_list(session: "requests.Session", mr: MR) -> list[ScoreDetail]:
    """获取 MR 的打分信息列表。"""
    # TODO: 调用平台打分列表 API
    raise NotImplementedError


def has_required_reviewer(scores: list[ScoreDetail]) -> bool:
    """检查打分列表中是否包含指定用户（ABC 或 DEF）。"""
    return any(s.reviewer_id in INSPECT_REVIEWER_IDS for s in scores)


# ── IM 通知 ────────────────────────────────────────────────────────────

def send_im(target_user_id: str, message: str) -> None:
    """发送 IM 消息给指定用户。"""
    # TODO: 对接飞书/IM API
    raise NotImplementedError


# ── 核心流程 ───────────────────────────────────────────────────────────

def run_normal(session: "requests.Session", mr: MR) -> None:
    """普通模式。"""
    mode = determine_mode(mr)
    print(f"[normal] MR {mr.mr_id} 模式: {mode.value}")

    # 加分 或 评审
    if mode == MRMode.SCORE:
        try:
            add_score(session, mr)
            print("[normal] 加分完成")
        except Exception as e:
            print(f"[normal] 加分失败: {e}")
    else:
        try:
            approve_review(session, mr)
            print("[normal] 批准检视完成")
        except Exception as e:
            print(f"[normal] 批准检视失败: {e}")

        try:
            approve_audit(session, mr)
            print("[normal] 批准审核完成")
        except Exception as e:
            print(f"[normal] 批准审核失败: {e}")

    # 关闭未处理的 issue
    open_issues = filter_open_issues(mr.issues)
    for issue in open_issues:
        try:
            close_issue(session, issue)
            print(f"[normal] 关闭 issue {issue.issue_id} 成功")
        except Exception as e:
            print(f"[normal] 关闭 issue {issue.issue_id} 失败: {e}")

    # 合并
    try:
        if is_mergeable(session, mr):
            merge_mr(session, mr)
            print("[normal] 合并完成")
        else:
            print("[normal] MR 不可合并，跳过")
    except Exception as e:
        print(f"[normal] 合并失败: {e}")


# ── 入口 ───────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="MR 处理工具")
    parser.add_argument(
        "--mode", choices=["normal", "inspect"], required=True,
        help="运行模式: normal 普通模式, inspect 巡检项模式"
    )
    parser.add_argument(
        "--mr-id", required=True,
        help="目标 MR ID"
    )
    args = parser.parse_args()

    session = get_session()

    try:
        mr = query_mr_detail(session, args.mr_id)
    except Exception as e:
        print(f"查询 MR 详情失败: {e}")
        sys.exit(1)

    if args.mode == "normal":
        run_normal(session, mr)
    else:
        # 巡检项模式：在加分/评审前检查指定用户是否已打分
        print(f"[inspect] 获取 MR {mr.mr_id} 打分列表...")
        try:
            scores = query_score_list(session, mr)
        except Exception as e:
            print(f"[inspect] 获取打分列表失败: {e}")
            sys.exit(1)

        if has_required_reviewer(scores):
            print("[inspect] 指定用户已打分，进入普通流程")
            run_normal(session, mr)
        else:
            print(f"[inspect] 未找到指定用户打分，通知 MR 作者 {mr.author_id}")
            try:
                send_im(mr.author_id, IM_MESSAGE_TEMPLATE)
                print("[inspect] IM 通知已发送，处理终止")
            except Exception as e:
                print(f"[inspect] IM 通知发送失败: {e}")


if __name__ == "__main__":
    main()
