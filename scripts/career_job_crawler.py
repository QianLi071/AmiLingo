#!/usr/bin/env python3
"""Collect a small set of public job postings from official career sites.

The crawler deliberately uses a low request rate, does not log in, and does not
bypass anti-bot controls. It only follows the configured public career pages.
"""

from __future__ import annotations

import argparse
import html as html_module
import json
import re
import time
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin
from urllib.request import Request, urlopen


USER_AGENT = "AmiLingoCareerResearch/0.1 (+student software-engineering project)"
REQUEST_INTERVAL_SECONDS = 1.0

BYTEDANCE_JOBS = (
    "https://jobs.bytedance.com/campus/position/7595142586517293365/detail",
    "https://jobs.bytedance.com/campus/position/7602148466978244917/detail",
)
WYNN_LISTING_URL = "https://www.wynncareersmacau.com/open-roles/"
HKJC_LISTING_URL = "https://careers.hkjc.com/go/Apply-Now/7951310/"


class _TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self.parts.append(data)


def clean_text(fragment: str) -> str:
    parser = _TextExtractor()
    parser.feed(fragment)
    return re.sub(r"\s+", " ", html_module.unescape(" ".join(parser.parts))).strip()


def fetch_text(url: str) -> str:
    request = Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        },
    )
    with urlopen(request, timeout=20) as response:
        content_type = response.headers.get_content_charset() or "utf-8"
        return response.read(2_500_000).decode(content_type, errors="replace")


def infer_job_type(title: str, detail: str = "") -> str:
    text = f"{title} {detail}".lower()
    if any(word in text for word in ("intern", "internship", "实习")):
        return "INTERNSHIP"
    if any(word in text for word in ("graduate", "trainee", "management associate", "管培")):
        return "GRADUATE_PROGRAM"
    return "FULL_TIME"


def make_posting(
    *,
    company: str,
    title: str,
    region: str,
    industry: str,
    location: str,
    official_url: str,
    source: str,
    detail: str = "",
) -> dict[str, object]:
    return {
        "companyName": company,
        "positionTitle": title,
        "region": region,
        "jobType": infer_job_type(title, detail),
        "industry": industry,
        "location": location,
        "description": detail or "岗位说明请以企业官方招聘页面为准。",
        "requirements": "申请条件及资格要求请以企业官方招聘页面为准。",
        "applicationDeadline": None,
        "officialUrl": official_url,
        "sourceName": source,
        "active": True,
    }


def parse_bytedance_detail(page_html: str, url: str) -> list[dict[str, object]]:
    match = re.search(r"<title[^>]*>(.*?)</title>", page_html, flags=re.I | re.S)
    if not match:
        return []
    title = clean_text(match.group(1))
    title = re.sub(r"\s*[-–—]\s*加入字节跳动\s*$", "", title).strip()
    if not title:
        return []
    return [
        make_posting(
            company="ByteDance",
            title=title,
            region="MAINLAND_CHINA",
            industry="Internet / Technology",
            location="Mainland China (see official page)",
            official_url=url,
            source="ByteDance Campus Careers",
            detail="字节跳动校园招聘公开职位，具体职责、城市与要求请查看官方页面。",
        )
    ]


def parse_wynn_listing(page_html: str, base_url: str = WYNN_LISTING_URL) -> list[dict[str, object]]:
    postings: list[dict[str, object]] = []
    rows = re.findall(
        r"<tr\b[^>]*data-testid=[\"']table-row[\"'][^>]*>(.*?)</tr>",
        page_html,
        flags=re.I | re.S,
    )
    for row in rows:
        cells = re.findall(r"<td\b[^>]*>(.*?)</td>", row, flags=re.I | re.S)
        link_match = re.search(r"href=[\"']([^\"']*/open-roles/\d+/)[\"']", row, flags=re.I)
        if len(cells) < 3 or not link_match:
            continue
        title, category, location = (clean_text(cell) for cell in cells[:3])
        if not title:
            continue
        postings.append(
            make_posting(
                company="Wynn Macau",
                title=title,
                region="MACAO",
                industry=category or "Hospitality",
                location=location or "Macao",
                official_url=urljoin(base_url, link_match.group(1)),
                source="Wynn Careers Macau",
                detail=f"Wynn Careers Macau 公开职位，类别：{category or '未注明'}。",
            )
        )
    return postings


def _class_value(row: str, class_name: str) -> str:
    match = re.search(
        rf"<span\b[^>]*class=[\"'][^\"']*\b{re.escape(class_name)}\b[^\"']*[\"'][^>]*>(.*?)</span>",
        row,
        flags=re.I | re.S,
    )
    return clean_text(match.group(1)) if match else ""


def parse_hkjc_listing(page_html: str, base_url: str = HKJC_LISTING_URL) -> list[dict[str, object]]:
    postings: list[dict[str, object]] = []
    rows = re.findall(
        r"<tr\b[^>]*class=[\"'][^\"']*\bdata-row\b[^\"']*[\"'][^>]*>(.*?)</tr>",
        page_html,
        flags=re.I | re.S,
    )
    for row in rows:
        title_match = re.search(
            r"<a\b[^>]*class=[\"'][^\"']*\bjobTitle-link\b[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            row,
            flags=re.I | re.S,
        )
        if not title_match:
            title_match = re.search(
                r"<a\b[^>]*href=[\"']([^\"']+)[\"'][^>]*class=[\"'][^\"']*\bjobTitle-link\b[^\"']*[\"'][^>]*>(.*?)</a>",
                row,
                flags=re.I | re.S,
            )
        if not title_match:
            continue
        title = clean_text(title_match.group(2))
        facility = _class_value(row, "jobFacility")
        location = _class_value(row, "jobLocation")
        shift_type = _class_value(row, "jobShifttype")
        postings.append(
            make_posting(
                company="The Hong Kong Jockey Club",
                title=title,
                region="HONG_KONG",
                industry=facility or "Various",
                location=location or "Hong Kong",
                official_url=urljoin(base_url, html_module.unescape(title_match.group(1))),
                source="HKJC Careers",
                detail=f"香港赛马会公开职位；工作性质：{shift_type or '以官网为准'}。",
            )
        )
    return postings


def choose_relevant(postings: Iterable[dict[str, object]], limit: int) -> list[dict[str, object]]:
    values = list(postings)
    keywords = ("intern", "graduate", "trainee", "assistant", "engineer", "实习", "毕业")
    preferred = [item for item in values if any(k in str(item["positionTitle"]).lower() for k in keywords)]
    remaining = [item for item in values if item not in preferred]
    return (preferred + remaining)[:limit]


def crawl(limit_per_source: int) -> tuple[list[dict[str, object]], list[str]]:
    postings: list[dict[str, object]] = []
    warnings: list[str] = []

    for url in BYTEDANCE_JOBS[:limit_per_source]:
        try:
            postings.extend(parse_bytedance_detail(fetch_text(url), url))
        except Exception as exc:  # A failed source must not discard other sources.
            warnings.append(f"ByteDance fetch failed for {url}: {exc}")
        time.sleep(REQUEST_INTERVAL_SECONDS)

    for source_name, url, parser in (
        ("Wynn", WYNN_LISTING_URL, parse_wynn_listing),
        ("HKJC", HKJC_LISTING_URL, parse_hkjc_listing),
    ):
        try:
            postings.extend(choose_relevant(parser(fetch_text(url)), limit_per_source))
        except Exception as exc:
            warnings.append(f"{source_name} fetch failed for {url}: {exc}")
        time.sleep(REQUEST_INTERVAL_SECONDS)

    unique: dict[str, dict[str, object]] = {}
    for posting in postings:
        unique[str(posting["officialUrl"])] = posting
    return list(unique.values()), warnings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit-per-source", type=int, default=2)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.limit_per_source < 1 or args.limit_per_source > 10:
        parser.error("--limit-per-source must be between 1 and 10")

    postings, warnings = crawl(args.limit_per_source)
    payload = {
        "crawledAt": datetime.now(timezone.utc).isoformat(),
        "notice": "Public career-page data for demonstration. Verify availability on the official page before applying.",
        "count": len(postings),
        "warnings": warnings,
        "postings": postings,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved {len(postings)} postings to {args.output}")
    for warning in warnings:
        print(f"WARNING: {warning}")
    return 0 if postings else 1


if __name__ == "__main__":
    raise SystemExit(main())
