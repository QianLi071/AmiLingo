#!/usr/bin/env python3
"""Validate a crawled career-job dataset before it replaces the current file."""

from __future__ import annotations

import argparse
import copy
import json
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.parse import urlparse


REQUIRED_FIELDS = ("companyName", "positionTitle", "region", "jobType", "description", "officialUrl", "sourceName")
REGIONS = {"MAINLAND_CHINA", "HONG_KONG", "MACAO"}
JOB_TYPES = {"INTERNSHIP", "FULL_TIME", "GRADUATE_PROGRAM"}


def _parse_datetime(value: object) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def _parse_date(value: object) -> date | None:
    if value is None or value == "":
        return None
    if not isinstance(value, str):
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        return None


def audit_payload(payload: object, *, today: date | None = None, max_age_days: int = 7) -> tuple[dict, dict]:
    current_date = today or datetime.now(timezone.utc).date()
    result = copy.deepcopy(payload) if isinstance(payload, dict) else {}
    errors: list[str] = []
    warnings: list[str] = []
    deactivated: list[str] = []

    crawled_at = _parse_datetime(result.get("crawledAt"))
    if crawled_at is None:
        errors.append("crawledAt is missing or is not a valid ISO timestamp")
    else:
        age_days = (current_date - crawled_at.astimezone(timezone.utc).date()).days
        if age_days > max_age_days:
            warnings.append(f"dataset is {age_days} days old (limit: {max_age_days})")

    postings = result.get("postings")
    if not isinstance(postings, list):
        errors.append("postings must be an array")
        postings = []
        result["postings"] = postings
    if result.get("count") != len(postings):
        errors.append(f"count does not match postings length ({result.get('count')} != {len(postings)})")

    seen_urls: set[str] = set()
    for index, posting in enumerate(postings, start=1):
        label = f"posting {index}"
        if not isinstance(posting, dict):
            errors.append(f"{label} must be an object")
            continue
        for field in REQUIRED_FIELDS:
            if not isinstance(posting.get(field), str) or not posting[field].strip():
                errors.append(f"{label} is missing required field {field}")
        if posting.get("region") not in REGIONS:
            errors.append(f"{label} has unsupported region {posting.get('region')}")
        if posting.get("jobType") not in JOB_TYPES:
            errors.append(f"{label} has unsupported jobType {posting.get('jobType')}")

        official_url = posting.get("officialUrl")
        if isinstance(official_url, str):
            parsed = urlparse(official_url)
            if parsed.scheme != "https" or not parsed.netloc:
                errors.append(f"{label} officialUrl must be an absolute HTTPS URL")
            if official_url in seen_urls:
                errors.append(f"{label} duplicates officialUrl {official_url}")
            seen_urls.add(official_url)

        raw_deadline = posting.get("applicationDeadline")
        deadline = _parse_date(raw_deadline)
        if raw_deadline not in (None, "") and deadline is None:
            errors.append(f"{label} applicationDeadline must use YYYY-MM-DD or null")
        elif deadline and deadline < current_date and posting.get("active", True):
            posting["active"] = False
            deactivated.append(str(official_url or label))
            warnings.append(f"{label} was deactivated because its deadline {deadline.isoformat()} has passed")

    result["count"] = len(postings)
    result["audit"] = {
        "auditedAt": datetime.now(timezone.utc).isoformat(),
        "errorCount": len(errors),
        "warningCount": len(warnings),
        "deactivatedExpiredCount": len(deactivated),
    }
    report = {"valid": not errors, "errors": errors, "warnings": warnings, "deactivated": deactivated}
    return result, report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--max-age-days", type=int, default=7)
    args = parser.parse_args()
    if args.max_age_days < 0:
        parser.error("--max-age-days cannot be negative")

    payload = json.loads(args.input.read_text(encoding="utf-8"))
    audited, report = audit_payload(payload, max_age_days=args.max_age_days)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if not report["valid"]:
        for error in report["errors"]:
            print(f"ERROR: {error}")
        return 2
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audited, ensure_ascii=False, indent=2), encoding="utf-8")
    for warning in report["warnings"]:
        print(f"WARNING: {warning}")
    print(f"Audit passed for {len(audited['postings'])} posting(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
