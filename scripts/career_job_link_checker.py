#!/usr/bin/env python3
"""Check official job links and write a report without changing job activity state."""

from __future__ import annotations

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


USER_AGENT = "AmiLingoCareerResearch/0.1 (+student software-engineering project)"


def request_status(url: str, timeout: float = 15.0) -> int:
    request = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept": "text/html,application/xhtml+xml",
        "Range": "bytes=0-1023",
    })
    try:
        with urlopen(request, timeout=timeout) as response:
            return int(response.status)
    except HTTPError as error:
        return int(error.code)


def classify(status: int | None, error: str = "") -> str:
    if error:
        return "NETWORK_ERROR"
    if status is None:
        return "UNKNOWN"
    if 200 <= status < 400:
        return "REACHABLE"
    if status in (401, 403, 429):
        return "ACCESS_RESTRICTED"
    if status in (404, 410):
        return "NOT_FOUND"
    if status >= 500:
        return "TEMPORARY_FAILURE"
    return "UNEXPECTED_RESPONSE"


def check_payload(
    payload: object,
    *,
    fetch_status: Callable[[str], int] = request_status,
    delay_seconds: float = 1.0,
    limit: int | None = None,
) -> dict:
    postings = payload.get("postings", []) if isinstance(payload, dict) else []
    if not isinstance(postings, list):
        postings = []
    selected = postings[:limit] if limit is not None else postings
    results = []
    for index, posting in enumerate(selected):
        if not isinstance(posting, dict):
            continue
        url = str(posting.get("officialUrl") or "")
        status = None
        error = ""
        try:
            status = fetch_status(url)
        except (URLError, TimeoutError, OSError, ValueError) as exception:
            error = str(exception)[:300]
        results.append({
            "companyName": posting.get("companyName"),
            "positionTitle": posting.get("positionTitle"),
            "officialUrl": url,
            "httpStatus": status,
            "result": classify(status, error),
            "error": error or None,
        })
        if delay_seconds > 0 and index < len(selected) - 1:
            time.sleep(delay_seconds)

    counts: dict[str, int] = {}
    for item in results:
        counts[item["result"]] = counts.get(item["result"], 0) + 1
    return {
        "checkedAt": datetime.now(timezone.utc).isoformat(),
        "notice": "A failed check is evidence for manual review, not automatic removal.",
        "checkedCount": len(results),
        "counts": counts,
        "results": results,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--delay-seconds", type=float, default=1.0)
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    if args.delay_seconds < 0:
        parser.error("--delay-seconds cannot be negative")

    payload = json.loads(args.input.read_text(encoding="utf-8"))
    report = check_payload(payload, delay_seconds=args.delay_seconds, limit=args.limit)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Checked {report['checkedCount']} official link(s).")
    for name, count in sorted(report["counts"].items()):
        print(f"{name}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
