#!/usr/bin/env python3
"""
welink_msg_filter.py — Agent-oriented wrapper for welink-cli im query-history-message.

Always outputs JSON to stdout. Progress/verbose lines go to stderr.

Exit codes:
  0 — success (even if partial results)
  1 — usage/argument error
  2 — fatal error (no results at all)

Output JSON schema:
  {
    "success": true|false,
    "error": null | "error message",
    "error_type": null | "auth_error" | "api_error" | "network_error" | "parse_error" | "unknown",
    "summary": {
      "target_type":   "group" | "user",
      "target_id":     "...",
      "filters":       { applied filter values },
      "matched_count": N,
      "total_fetched": N,
      "total_pages":   N,
      "truncated":     true | false
    },
    "messages": [ ... ],
    "partial": true | false
  }
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime


# ═══════════════════════════════════════════════════════════════════════
# Time helpers
# ═══════════════════════════════════════════════════════════════════════

def parse_time_arg(time_str: str | None) -> int | None:
    """Parse a time string into millisecond unix timestamp.

    Accepts:
      - Millisecond timestamp (> 1e12)
      - Second timestamp (1e9 ~ 1e12)
      - "YYYY-MM-DD HH:MM:SS"
      - "YYYY-MM-DD"
      - "YYYY/MM/DD HH:MM:SS"
      - "YYYY/MM/DD"
    """
    if not time_str:
        return None
    # Numeric timestamp
    try:
        ts = float(time_str)
        if ts > 1e12:
            return int(ts)
        if ts > 1e9:
            return int(ts * 1000)
        raise ValueError(f"Timestamp out of plausible range: {time_str}")
    except ValueError:
        pass
    # Date formats
    for fmt in (
        "%Y-%m-%d %H:%M:%S", "%Y-%m-%d",
        "%Y/%m/%d %H:%M:%S", "%Y/%m/%d",
    ):
        try:
            return int(datetime.strptime(time_str, fmt).timestamp() * 1000)
        except ValueError:
            continue
    raise ValueError(f"Cannot parse time: {time_str}")


# ═══════════════════════════════════════════════════════════════════════
# CLI subprocess
# ═══════════════════════════════════════════════════════════════════════

def run_cmd(args: list[str], timeout: int = 60) -> tuple[int, str, str]:
    """Run a command; return (returncode, stdout, stderr)."""
    p = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    return p.returncode, (p.stdout or "").strip(), (p.stderr or "").strip()


# ═══════════════════════════════════════════════════════════════════════
# Query one page — with 401 retry
# ═══════════════════════════════════════════════════════════════════════

class QueryError(Exception):
    """Non-fatal query error that still allows partial results."""
    def __init__(self, message: str, error_type: str = "unknown"):
        super().__init__(message)
        self.error_type = error_type


def query_page(target_type: str, target_id: str,
               count: int, msg_id: int,
               cli_path: str = "welink-cli") -> dict:
    """Call welink-cli for one page.  Returns the parsed JSON response.

    Raises QueryError on failure (after auth retry if 401).
    """
    cmd = [
        cli_path, "im", "query-history-message",
        f"--{ 'group-id' if target_type == 'group' else 'user-account' }", target_id,
        "--query-count", str(count),
        "--message-id", str(msg_id),
    ]

    rc, stdout, stderr = run_cmd(cmd)

    # 401 → re-auth → retry once
    if rc != 0:
        combined = f"{stdout} {stderr}".lower()
        if "401" in combined or "unauthorized" in combined:
            print("[AUTH] 401 detected, running auth login ...", file=sys.stderr)
            rc2, _, err2 = run_cmd([cli_path, "auth", "login"])
            if rc2 != 0:
                raise QueryError(f"Auth login failed: {err2}", "auth_error")
            print("[AUTH] Re-login ok, retrying query ...", file=sys.stderr)
            rc, stdout, stderr = run_cmd(cmd)
            if rc != 0:
                raise QueryError(
                    f"Command failed after re-auth (rc={rc}): {stderr or stdout}",
                    "auth_error"
                )
        else:
            raise QueryError(
                f"Command failed (rc={rc}): {stderr or stdout}",
                "network_error"
            )

    # Parse JSON
    try:
        data = json.loads(stdout)
    except json.JSONDecodeError as e:
        raise QueryError(f"JSON parse error: {e}", "parse_error")

    # API-level error
    if data.get("resultCode", "") != "0":
        raise QueryError(
            f"API returned resultCode={data.get('resultCode')}, "
            f"context={data.get('resultContext', data.get('resultContent', ''))}",
            "api_error"
        )

    return data


# ═══════════════════════════════════════════════════════════════════════
# Filter
# ═══════════════════════════════════════════════════════════════════════

def compile_keyword(kw: str | None, use_regex: bool):
    """Prepare keyword filter. Returns (matcher_fn, display_value)."""
    if not kw:
        return None, None
    if use_regex:
        try:
            rx = re.compile(kw, re.IGNORECASE | re.DOTALL)
            return (lambda content: bool(rx.search(content or ""))), f"regex:{kw}"
        except re.error as e:
            raise ValueError(f"Invalid regex '{kw}': {e}")
    else:
        low = kw.lower()
        return (lambda content: low in (content or "").lower()), kw


def message_matches(msg: dict,
                    start_time_ms: int | None,
                    end_time_ms: int | None,
                    sender: str | None,
                    receiver: str | None,
                    content_type: str | None,
                    keyword_fn) -> tuple[bool, bool]:
    """Check if a message matches filters.

    Returns (matched, out_of_range) — out_of_range means this msg is
    older than start_time so pagination can stop entirely.
    """
    ts = msg.get("serverSendTime", 0)

    # Time range
    if start_time_ms is not None and ts < start_time_ms:
        return False, True   # stop pagination
    if end_time_ms is not None and ts > end_time_ms:
        return False, False  # skip, keep going

    # Sender
    if sender and msg.get("sender", "") != sender:
        return False, False

    # Receiver
    if receiver and msg.get("receiver", "") != receiver:
        return False, False

    # Content type
    if content_type and msg.get("contentType", "") != content_type:
        return False, False

    # Keyword / regex
    if keyword_fn and not keyword_fn(msg.get("content", "")):
        return False, False

    return True, False


# ═══════════════════════════════════════════════════════════════════════
# Main fetch loop
# ═══════════════════════════════════════════════════════════════════════

def fetch_and_filter(
    target_type: str,
    target_id: str,
    start_msg_id: int,
    page_size: int,
    start_time_ms: int | None,
    end_time_ms: int | None,
    sender: str | None,
    receiver: str | None,
    content_type: str | None,
    keyword_fn,
    keyword_display: str | None,
    max_count: int,
    verbose: bool,
    cli_path: str = "welink-cli",
) -> dict:
    """Run the paginated fetch-and-filter loop.

    Returns a dict ready for JSON output (success + messages).
    Never raises — errors are captured into the result dict.
    """
    matched: list[dict] = []
    cursor = start_msg_id
    page_num = 0
    total_fetched = 0
    error_info = None
    error_type = None
    stopped_early = False

    try:
        while True:
            page_num += 1

            try:
                data = query_page(target_type, target_id, page_size, cursor,
                                  cli_path=cli_path)
            except QueryError as e:
                error_info = str(e)
                error_type = e.error_type
                stopped_early = True
                break

            resp = data.get("respData", {})
            chat_info = resp.get("chatInfo", [])
            total_fetched += len(chat_info)

            if verbose:
                print(
                    f"[PAGE {page_num}] got {len(chat_info)} msgs "
                    f"(fetched={total_fetched} matched={len(matched)})",
                    file=sys.stderr,
                )

            if not chat_info:
                break

            min_msg_id = min(m["msgId"] for m in chat_info)
            do_stop = False

            for msg in chat_info:
                ok, out_of_range = message_matches(
                    msg, start_time_ms, end_time_ms,
                    sender, receiver, content_type, keyword_fn,
                )
                if out_of_range:
                    do_stop = True
                    break
                if ok:
                    matched.append(msg)
                    if max_count > 0 and len(matched) >= max_count:
                        do_stop = True
                        break

            if do_stop:
                break

            # Partial page → reached beginning of history
            if len(chat_info) < page_size:
                break

            if min_msg_id == cursor:
                # Cursor didn't advance — shouldn't happen, but guard
                break

            cursor = min_msg_id

    except Exception as e:
        error_info = str(e)
        error_type = "unknown"
        stopped_early = True

    # Trim
    if max_count > 0 and len(matched) > max_count:
        matched = matched[:max_count]

    success = error_info is None
    truncated = stopped_early or (max_count > 0 and len(matched) >= max_count)

    result = {
        "success": success,
        "error": error_info,
        "error_type": error_type,
        "summary": {
            "target_type": target_type,
            "target_id": target_id,
            "filters": {
                "start_time": start_time_ms,
                "end_time": end_time_ms,
                "sender": sender,
                "receiver": receiver,
                "content_type": content_type,
                "keyword": keyword_display,
            },
            "matched_count": len(matched),
            "total_fetched": total_fetched,
            "total_pages": page_num,
            "truncated": truncated,
        },
        "messages": matched,
        "partial": not success,
    }

    return result


# ═══════════════════════════════════════════════════════════════════════
# CLI entry point
# ═══════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="WeLink message query with filtering — agent interface",
    )
    # Target
    tgt = parser.add_mutually_exclusive_group(required=True)
    tgt.add_argument("--group-id", type=str, help="Group chat ID (numeric)")
    tgt.add_argument("--user-account", type=str, help="User account (e.g. a12345678)")

    # Pagination
    parser.add_argument("--message-id", type=int, default=0,
                        help="Starting message ID (default 0 = newest)")
    parser.add_argument("--page-size", type=int, default=100,
                        help="Messages per API call (1-100, default 100)")

    # Limit
    parser.add_argument("--max-count", type=int, default=0,
                        help="Max matched messages to return (0 = unlimited)")

    # Time
    parser.add_argument("--start-time", type=str,
                        help="Start time (inclusive). Unix ms/s or 'YYYY-MM-DD HH:MM:SS'")
    parser.add_argument("--end-time", type=str,
                        help="End time (inclusive). Same format as --start-time")

    # People
    parser.add_argument("--sender", type=str, help="Filter by sender account")
    parser.add_argument("--receiver", type=str, help="Filter by receiver (private chat)")

    # Content
    parser.add_argument("--content-type", type=str,
                        choices=["TEXT_MSG", "IMAGESPAN_MSG", "PICTURE_MSG",
                                 "CARD_MSG", "FILE_MSG"],
                        help="Filter by message contentType")
    parser.add_argument("--keyword", type=str,
                        help="Filter by content keyword (substring, case-insensitive)")
    parser.add_argument("--regex", action="store_true",
                        help="Treat --keyword as a regex pattern instead of literal substring")

    # Meta
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Print progress to stderr")
    parser.add_argument("--cli-path", type=str, default="welink-cli",
                        help="Path to welink-cli binary (default: welink-cli)")

    args = parser.parse_args()

    # ── Validate ──
    if args.page_size < 1 or args.page_size > 100:
        print(json.dumps({
            "success": False,
            "error": "--page-size must be 1-100",
            "error_type": "arg_error",
            "messages": [],
            "partial": False,
        }, ensure_ascii=False))
        sys.exit(1)

    try:
        start_ms = parse_time_arg(args.start_time)
        end_ms = parse_time_arg(args.end_time)
    except ValueError as e:
        print(json.dumps({
            "success": False,
            "error": str(e),
            "error_type": "arg_error",
            "messages": [],
            "partial": False,
        }, ensure_ascii=False))
        sys.exit(1)

    if start_ms is not None and end_ms is not None and start_ms > end_ms:
        print(json.dumps({
            "success": False,
            "error": "--start-time must be earlier than --end-time",
            "error_type": "arg_error",
            "messages": [],
            "partial": False,
        }, ensure_ascii=False))
        sys.exit(1)

    try:
        kw_fn, kw_display = compile_keyword(args.keyword, args.regex)
    except ValueError as e:
        print(json.dumps({
            "success": False,
            "error": str(e),
            "error_type": "arg_error",
            "messages": [],
            "partial": False,
        }, ensure_ascii=False))
        sys.exit(1)

    target_type = "group" if args.group_id else "user"
    target_id = args.group_id or args.user_account

    # ── Execute ──
    result = fetch_and_filter(
        target_type=target_type,
        target_id=target_id,
        start_msg_id=args.message_id,
        page_size=args.page_size,
        start_time_ms=start_ms,
        end_time_ms=end_ms,
        sender=args.sender,
        receiver=args.receiver,
        content_type=args.content_type,
        keyword_fn=kw_fn,
        keyword_display=kw_display,
        max_count=args.max_count,
        verbose=args.verbose,
        cli_path=args.cli_path,
    )

    # ── Output ──
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["success"] else 2)


if __name__ == "__main__":
    main()
