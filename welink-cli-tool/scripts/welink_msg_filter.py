#!/usr/bin/env python3
"""
welink_msg_filter.py — Agent-oriented wrapper for WeLink CLI.

Subcommands:
  (default)           Query chat messages with cursor-based pagination + filtering
  search-group        Search groups by name (simulated pagination via --page)
  search-person       Search contacts by name (simulated pagination via --page)

Always outputs JSON to stdout. Progress/verbose lines go to stderr.

Exit codes:
  0 — success (even if partial results)
  1 — usage/argument error
  2 — fatal error
"""

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime


# ═══════════════════════════════════════════════════════════════════════
# Shared helpers
# ═══════════════════════════════════════════════════════════════════════

def parse_time_arg(time_str: str | None) -> int | None:
    """Parse a time string into millisecond unix timestamp.

    Accepts: millisecond ts (>1e12), second ts (1e9~1e12),
             "YYYY-MM-DD HH:MM:SS", "YYYY-MM-DD", "YYYY/MM/DD HH:MM:SS", "YYYY/MM/DD"
    """
    if not time_str:
        return None
    try:
        ts = float(time_str)
        if ts > 1e12:
            return int(ts)
        if ts > 1e9:
            return int(ts * 1000)
        raise ValueError(f"Timestamp out of plausible range: {time_str}")
    except ValueError:
        pass
    for fmt in (
        "%Y-%m-%d %H:%M:%S", "%Y-%m-%d",
        "%Y/%m/%d %H:%M:%S", "%Y/%m/%d",
    ):
        try:
            return int(datetime.strptime(time_str, fmt).timestamp() * 1000)
        except ValueError:
            continue
    raise ValueError(f"Cannot parse time: {time_str}")


def run_cmd(args: list[str], timeout: int = 60) -> tuple[int, str, str]:
    """Run a command; return (returncode, stdout, stderr)."""
    p = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    return p.returncode, (p.stdout or "").strip(), (p.stderr or "").strip()


def fail_json(msg: str, error_type: str = "arg_error") -> str:
    return json.dumps({
        "success": False, "error": msg, "error_type": error_type,
        "messages": [], "partial": False,
    }, ensure_ascii=False)


class QueryError(Exception):
    def __init__(self, message: str, error_type: str = "unknown"):
        super().__init__(message)
        self.error_type = error_type


def try_reauth(cli_path: str) -> None:
    """Run auth login; raise QueryError on failure."""
    print("[AUTH] 401 detected, running auth login ...", file=sys.stderr)
    rc, _, err = run_cmd([cli_path, "auth", "login"])
    if rc != 0:
        raise QueryError(f"Auth login failed: {err}", "auth_error")
    print("[AUTH] Re-login ok.", file=sys.stderr)


# ═══════════════════════════════════════════════════════════════════════
# Subcommand: query-history-message (default)
# ═══════════════════════════════════════════════════════════════════════

def compile_keyword(kw: str | None, use_regex: bool):
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


def message_matches(msg, start_ms, end_ms, sender, receiver, ctype, kw_fn):
    ts = msg.get("serverSendTime", 0)
    if start_ms is not None and ts < start_ms:
        return False, True
    if end_ms is not None and ts > end_ms:
        return False, False
    if sender and msg.get("sender", "") != sender:
        return False, False
    if receiver and msg.get("receiver", "") != receiver:
        return False, False
    if ctype and msg.get("contentType", "") != ctype:
        return False, False
    if kw_fn and not kw_fn(msg.get("content", "")):
        return False, False
    return True, False


def query_message_page(target_type, target_id, count, msg_id, cli_path):
    cmd = [
        cli_path, "im", "query-history-message",
        f"--{'group-id' if target_type == 'group' else 'user-account'}", target_id,
        "--query-count", str(count),
        "--message-id", str(msg_id),
    ]
    rc, stdout, stderr = run_cmd(cmd)

    if rc != 0:
        if "401" in f"{stdout} {stderr}".lower():
            try_reauth(cli_path)
            rc, stdout, stderr = run_cmd(cmd)
            if rc != 0:
                raise QueryError(
                    f"Command failed after re-auth (rc={rc}): {stderr or stdout}",
                    "auth_error")
        else:
            raise QueryError(
                f"Command failed (rc={rc}): {stderr or stdout}", "network_error")

    try:
        data = json.loads(stdout)
    except json.JSONDecodeError as e:
        raise QueryError(f"JSON parse error: {e}", "parse_error")

    if data.get("resultCode", "") != "0":
        raise QueryError(
            f"API returned resultCode={data.get('resultCode')}, "
            f"context={data.get('resultContext', data.get('resultContent', ''))}",
            "api_error")
    return data


def fetch_and_filter(target_type, target_id, start_msg_id, page_size,
                     start_ms, end_ms, sender, receiver, ctype,
                     kw_fn, kw_display, max_count, verbose, cli_path):
    matched, cursor = [], start_msg_id
    page_num, total_fetched = 0, 0
    error_info, error_type = None, None
    stopped_early = False

    try:
        while True:
            page_num += 1
            try:
                data = query_message_page(target_type, target_id, page_size, cursor, cli_path)
            except QueryError as e:
                error_info, error_type = str(e), e.error_type
                stopped_early = True
                break

            resp = data.get("respData", {})
            chat_info = resp.get("chatInfo", [])
            total_fetched += len(chat_info)

            if verbose:
                print(f"[PAGE {page_num}] got {len(chat_info)} msgs "
                      f"(fetched={total_fetched} matched={len(matched)})", file=sys.stderr)

            if not chat_info:
                break

            min_msg_id = min(m["msgId"] for m in chat_info)
            do_stop = False

            for msg in chat_info:
                ok, out_of_range = message_matches(
                    msg, start_ms, end_ms, sender, receiver, ctype, kw_fn)
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
            if len(chat_info) < page_size:
                break
            if min_msg_id == cursor:
                break
            cursor = min_msg_id

    except Exception as e:
        error_info, error_type = str(e), "unknown"
        stopped_early = True

    if max_count > 0 and len(matched) > max_count:
        matched = matched[:max_count]

    success = error_info is None
    return {
        "success": success,
        "error": error_info,
        "error_type": error_type,
        "summary": {
            "target_type": target_type,
            "target_id": target_id,
            "filters": {
                "start_time": start_ms,
                "end_time": end_ms,
                "sender": sender,
                "receiver": receiver,
                "content_type": ctype,
                "keyword": kw_display,
            },
            "matched_count": len(matched),
            "total_fetched": total_fetched,
            "total_pages": page_num,
            "truncated": stopped_early or (max_count > 0 and len(matched) >= max_count),
        },
        "messages": matched,
        "partial": not success,
    }


def cmd_query_messages(args, cli_path):
    if args.page_size < 1 or args.page_size > 100:
        print(fail_json("--page-size must be 1-100"))
        sys.exit(1)

    try:
        start_ms = parse_time_arg(args.start_time)
        end_ms = parse_time_arg(args.end_time)
    except ValueError as e:
        print(fail_json(str(e)))
        sys.exit(1)

    if start_ms is not None and end_ms is not None and start_ms > end_ms:
        print(fail_json("--start-time must be earlier than --end-time"))
        sys.exit(1)

    try:
        kw_fn, kw_display = compile_keyword(args.keyword, args.regex)
    except ValueError as e:
        print(fail_json(str(e)))
        sys.exit(1)

    target_type = "group" if args.group_id else "user"
    target_id = args.group_id or args.user_account

    result = fetch_and_filter(
        target_type=target_type, target_id=target_id,
        start_msg_id=args.message_id, page_size=args.page_size,
        start_ms=start_ms, end_ms=end_ms,
        sender=args.sender, receiver=args.receiver,
        ctype=args.content_type,
        kw_fn=kw_fn, kw_display=kw_display,
        max_count=args.max_count, verbose=args.verbose, cli_path=cli_path,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["success"] else 2)


# ═══════════════════════════════════════════════════════════════════════
# Subcommand: search-group
# ═══════════════════════════════════════════════════════════════════════

def search_groups(keyword, page, page_size, cli_path, verbose):
    """Search groups by name. Simulates pagination by querying (page*page_size)
    items and returning the last page_size slice."""
    total_needed = page * page_size

    cmd = [cli_path, "search", "group", "--text", keyword,
           "--page-size", str(total_needed)]

    if verbose:
        print(f"[SEARCH-GROUP] querying up to {total_needed} results for '{keyword}'",
              file=sys.stderr)

    rc, stdout, stderr = run_cmd(cmd)

    if rc != 0:
        if "401" in f"{stdout} {stderr}".lower():
            try:
                try_reauth(cli_path)
            except QueryError as e:
                return {
                    "success": False, "error": str(e),
                    "error_type": e.error_type, "results": [], "partial": False,
                    "summary": {"keyword": keyword, "page": page,
                                "page_size": page_size, "total_matches": 0,
                                "returned_count": 0},
                }
            rc, stdout, stderr = run_cmd(cmd)
            if rc != 0:
                return {
                    "success": False,
                    "error": f"Command failed after re-auth: {stderr or stdout}",
                    "error_type": "auth_error", "results": [], "partial": False,
                    "summary": {"keyword": keyword, "page": page,
                                "page_size": page_size, "total_matches": 0,
                                "returned_count": 0},
                }
        else:
            return {
                "success": False,
                "error": f"Command failed (rc={rc}): {stderr or stdout}",
                "error_type": "network_error", "results": [], "partial": False,
                "summary": {"keyword": keyword, "page": page,
                            "page_size": page_size, "total_matches": 0,
                            "returned_count": 0},
            }

    try:
        data = json.loads(stdout)
    except json.JSONDecodeError as e:
        return {
            "success": False, "error": f"JSON parse error: {e}",
            "error_type": "parse_error", "results": [], "partial": False,
            "summary": {"keyword": keyword, "page": page,
                        "page_size": page_size, "total_matches": 0,
                        "returned_count": 0},
        }

    search_data = data.get("search_group_cli", {})
    if search_data.get("code") != "200":
        return {
            "success": False,
            "error": f"API returned code={search_data.get('code')}, "
                     f"message={search_data.get('message', '')}",
            "error_type": "api_error", "results": [], "partial": False,
            "summary": {"keyword": keyword, "page": page,
                        "page_size": page_size, "total_matches": 0,
                        "returned_count": 0},
        }

    all_items = search_data.get("data", [])
    total_rows = search_data.get("pagination", {}).get("totalRows", len(all_items))

    # Slice: items are in order; return the current page
    start_idx = (page - 1) * page_size
    end_idx = start_idx + page_size
    page_items = all_items[start_idx:end_idx]

    # Extract key fields for cleaner output
    simplified = []
    for g in page_items:
        simplified.append({
            "groupId": g.get("groupId", ""),
            "groupName": g.get("groupName", ""),
            "memberNum": g.get("memberNum", 0),
            "ownerId": g.get("ownerId", ""),
            "createDate": g.get("createDate", 0),
            "activeDate": g.get("activeDate", 0),
        })

    return {
        "success": True,
        "error": None,
        "error_type": None,
        "summary": {
            "keyword": keyword,
            "page": page,
            "page_size": page_size,
            "total_matches": total_rows,
            "returned_count": len(page_items),
        },
        "results": simplified,
        "partial": False,
    }


def cmd_search_group(args, cli_path):
    if args.page_size < 1 or args.page_size > 100:
        print(fail_json("--page-size must be 1-100"))
        sys.exit(1)
    if args.page < 1:
        print(fail_json("--page must be >= 1"))
        sys.exit(1)

    result = search_groups(args.keyword, args.page, args.page_size,
                           cli_path, args.verbose)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["success"] else 2)


# ═══════════════════════════════════════════════════════════════════════
# Subcommand: search-person
# ═══════════════════════════════════════════════════════════════════════

def search_persons(keyword, page, page_size, cli_path, verbose):
    """Search persons by name. Simulates pagination."""
    total_needed = page * page_size

    cmd = [cli_path, "search", "person", "--text", keyword,
           "--page-size", str(total_needed)]

    if verbose:
        print(f"[SEARCH-PERSON] querying up to {total_needed} results for '{keyword}'",
              file=sys.stderr)

    rc, stdout, stderr = run_cmd(cmd)

    if rc != 0:
        if "401" in f"{stdout} {stderr}".lower():
            try:
                try_reauth(cli_path)
            except QueryError as e:
                return _empty_person_result(keyword, page, page_size, str(e), e.error_type)
            rc, stdout, stderr = run_cmd(cmd)
            if rc != 0:
                return _empty_person_result(keyword, page, page_size,
                                            f"Command failed after re-auth: {stderr or stdout}",
                                            "auth_error")
        else:
            return _empty_person_result(keyword, page, page_size,
                                        f"Command failed (rc={rc}): {stderr or stdout}",
                                        "network_error")

    try:
        data = json.loads(stdout)
    except json.JSONDecodeError as e:
        return _empty_person_result(keyword, page, page_size,
                                    f"JSON parse error: {e}", "parse_error")

    search_data = data.get("search_cli_person", {})
    if search_data.get("code") != "200":
        return _empty_person_result(
            keyword, page, page_size,
            f"API returned code={search_data.get('code')}, "
            f"message={search_data.get('message', '')}",
            "api_error")

    all_items = search_data.get("data", [])
    total_rows = search_data.get("pagination", {}).get("totalRows", len(all_items))

    start_idx = (page - 1) * page_size
    end_idx = start_idx + page_size
    page_items = all_items[start_idx:end_idx]

    simplified = []
    for p in page_items:
        simplified.append({
            "w3account": p.get("w3account", ""),
            "chineseName": p.get("chineseName", ""),
            "englishName": p.get("englishName", ""),
            "sex": p.get("sex", ""),
            "appointPos": p.get("appointPos", ""),
            "appointGrade": p.get("appointGrade", ""),
            "departmentName": p.get("departmentName", ""),
            "deptL1Name": p.get("deptL1Name", ""),
            "deptName": p.get("deptName", ""),
            "mobilePhone": p.get("mobilePhone", ""),
            "personMail": p.get("personMail", ""),
            "personType": p.get("personType", ""),
        })

    return {
        "success": True,
        "error": None,
        "error_type": None,
        "summary": {
            "keyword": keyword,
            "page": page,
            "page_size": page_size,
            "total_matches": total_rows,
            "returned_count": len(page_items),
        },
        "results": simplified,
        "partial": False,
    }


def _empty_person_result(keyword, page, page_size, error, error_type):
    return {
        "success": False, "error": error, "error_type": error_type,
        "results": [], "partial": False,
        "summary": {"keyword": keyword, "page": page,
                    "page_size": page_size, "total_matches": 0,
                    "returned_count": 0},
    }


def cmd_search_person(args, cli_path):
    if args.page_size < 1 or args.page_size > 100:
        print(fail_json("--page-size must be 1-100"))
        sys.exit(1)
    if args.page < 1:
        print(fail_json("--page must be >= 1"))
        sys.exit(1)

    result = search_persons(args.keyword, args.page, args.page_size,
                            cli_path, args.verbose)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["success"] else 2)


# ═══════════════════════════════════════════════════════════════════════
# CLI entry point
# ═══════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="WeLink query wrapper — agent interface")
    sub = parser.add_subparsers(dest="command", required=True)

    # ── query-messages ──
    msg_parser = sub.add_parser("query-messages", aliases=["msg"],
                                help="Query chat messages with filtering")
    tgt = msg_parser.add_mutually_exclusive_group(required=True)
    tgt.add_argument("--group-id", type=str, help="Group chat ID (numeric)")
    tgt.add_argument("--user-account", type=str, help="User account (e.g. a12345678)")
    msg_parser.add_argument("--message-id", type=int, default=0)
    msg_parser.add_argument("--page-size", type=int, default=100)
    msg_parser.add_argument("--max-count", type=int, default=0)
    msg_parser.add_argument("--start-time", type=str)
    msg_parser.add_argument("--end-time", type=str)
    msg_parser.add_argument("--sender", type=str)
    msg_parser.add_argument("--receiver", type=str)
    msg_parser.add_argument("--content-type", type=str,
                            choices=["TEXT_MSG", "IMAGESPAN_MSG", "PICTURE_MSG",
                                     "CARD_MSG", "FILE_MSG"])
    msg_parser.add_argument("--keyword", type=str)
    msg_parser.add_argument("--regex", action="store_true")
    msg_parser.add_argument("--verbose", "-v", action="store_true")
    msg_parser.add_argument("--cli-path", type=str, default="welink-cli")

    # ── search-group ──
    sg_parser = sub.add_parser("search-group",
                               help="Search groups by name")
    sg_parser.add_argument("--keyword", type=str, required=True,
                           help="Group name keyword")
    sg_parser.add_argument("--page-size", type=int, default=20,
                           help="Results per page (default 20, max 100)")
    sg_parser.add_argument("--page", type=int, default=1,
                           help="Page number (default 1)")
    sg_parser.add_argument("--verbose", "-v", action="store_true")
    sg_parser.add_argument("--cli-path", type=str, default="welink-cli")

    # ── search-person ──
    sp_parser = sub.add_parser("search-person",
                               help="Search contacts by name")
    sp_parser.add_argument("--keyword", type=str, required=True,
                           help="Person name keyword")
    sp_parser.add_argument("--page-size", type=int, default=20,
                           help="Results per page (default 20, max 100)")
    sp_parser.add_argument("--page", type=int, default=1,
                           help="Page number (default 1)")
    sp_parser.add_argument("--verbose", "-v", action="store_true")
    sp_parser.add_argument("--cli-path", type=str, default="welink-cli")

    args = parser.parse_args()
    cli_path = args.cli_path

    if args.command in ("query-messages", "msg"):
        cmd_query_messages(args, cli_path)
    elif args.command == "search-group":
        cmd_search_group(args, cli_path)
    elif args.command == "search-person":
        cmd_search_person(args, cli_path)
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
