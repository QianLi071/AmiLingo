#!/usr/bin/env python3
"""Merge a partial career crawl with the last audited dataset safely."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.parse import urlparse


PRESERVE_STATUSES = {"FAILED", "EMPTY", "PARTIAL"}


def infer_source_key(posting: dict[str, object]) -> str:
    """Migrate records created before sourceKey was added."""
    explicit = str(posting.get("sourceKey") or "").strip()
    if explicit:
        return explicit
    url = str(posting.get("officialUrl") or "")
    parsed = urlparse(url)
    host = parsed.netloc.lower()
    path = parsed.path.upper()
    if host == "jobs.bytedance.com":
        return "bytedance"
    if host == "www.wynncareersmacau.com":
        return "wynn-macau"
    if host == "careers.hkjc.com":
        return "hkjc"
    if host == "careers.mtr.com.hk":
        return "mtr"
    if host == "assets.sandsresortsmacao.cn":
        return "sands-china"
    if host == "talent.baidu.com" and "/JOBS/DETAIL/INTERN/" in path:
        return "baidu-internship"
    if host == "talent.baidu.com" and "/JOBS/DETAIL/GRADUATE/" in path:
        return "baidu-campus"
    return ""


def _postings(payload: object) -> list[dict[str, object]]:
    if not isinstance(payload, dict) or not isinstance(payload.get("postings"), list):
        return []
    return [item for item in payload["postings"] if isinstance(item, dict)]


def merge_payload(
    existing: dict[str, object] | None,
    update: dict[str, object],
) -> dict[str, object]:
    """Replace successful sources and preserve unselected or failed-source records."""
    selected = update.get("selectedSources")
    results = update.get("sourceResults")
    selected_keys = {
        str(item.get("key"))
        for item in selected if isinstance(item, dict) and item.get("key")
    } if isinstance(selected, list) else set()
    statuses = {
        str(item.get("key")): str(item.get("status"))
        for item in results if isinstance(item, dict) and item.get("key")
    } if isinstance(results, list) else {}

    merged_by_url: dict[str, dict[str, object]] = {}
    for item in _postings(update):
        item = dict(item)
        source_key = infer_source_key(item)
        if source_key:
            item["sourceKey"] = source_key
        url = str(item.get("officialUrl") or "")
        if url:
            merged_by_url[url] = item

    retained_counts: dict[str, int] = {}
    for item in _postings(existing):
        item = dict(item)
        source_name = str(item.get("sourceName") or "")
        source_key = infer_source_key(item)
        if source_key:
            item["sourceKey"] = source_key
        status = statuses.get(source_key, "SUCCESS")
        should_retain = source_key not in selected_keys or status in PRESERVE_STATUSES
        url = str(item.get("officialUrl") or "")
        if should_retain and url and url not in merged_by_url:
            merged_by_url[url] = item
            retained_counts[source_name] = retained_counts.get(source_name, 0) + 1

    merged = dict(update)
    merged["postings"] = list(merged_by_url.values())
    merged["count"] = len(merged_by_url)
    merged["mergeSummary"] = {
        "retainedExistingCount": sum(retained_counts.values()),
        "retainedBySource": retained_counts,
    }
    merged.pop("audit", None)
    return merged


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing", type=Path)
    parser.add_argument("--update", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    existing = None
    if args.existing and args.existing.exists():
        existing = json.loads(args.existing.read_text(encoding="utf-8"))
    update = json.loads(args.update.read_text(encoding="utf-8"))
    merged = merge_payload(existing, update)
    args.output.write_text(
        json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        f"Merged {merged['count']} posting(s); retained "
        f"{merged['mergeSummary']['retainedExistingCount']} existing posting(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
