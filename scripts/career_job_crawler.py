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
from dataclasses import dataclass
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Callable, Iterable
from urllib.parse import unquote, urljoin
from urllib.request import Request, urlopen


USER_AGENT = "AmiLingoCareerResearch/0.1 (+student software-engineering project)"
REQUEST_INTERVAL_SECONDS = 1.0

BYTEDANCE_JOBS = (
    "https://jobs.bytedance.com/campus/position/7595142586517293365/detail",
    "https://jobs.bytedance.com/campus/position/7602148466978244917/detail",
)
WYNN_LISTING_URL = "https://www.wynncareersmacau.com/open-roles/"
HKJC_LISTING_URL = "https://careers.hkjc.com/go/Apply-Now/7951310/"
MTR_LISTING_URL = "https://careers.mtr.com.hk/careersection/mtr_external/joblist.ftl?lang=en"
SANDS_CHINA_LISTING_URL = (
    "https://en.sandsresortsmacao.com/sands-lifestyle/about-us/job-list.html"
)

PostingParser = Callable[[str, str], list[dict[str, object]]]


@dataclass(frozen=True)
class CareerSource:
    """One independently fetchable official career source."""

    key: str
    display_name: str
    region: str
    urls: tuple[str, ...]
    parser: PostingParser
    listing_page: bool


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
    application_deadline: str | None = None,
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
        "applicationDeadline": application_deadline,
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
            location="中国内地（以官网为准）",
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


def _taleo_date(value: str) -> str | None:
    match = re.fullmatch(r"(\d{2})/([A-Za-z]{3})/(\d{2})", value.strip())
    if not match:
        return None
    months = {
        "jan": 1, "feb": 2, "mar": 3, "apr": 4, "may": 5, "jun": 6,
        "jul": 7, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12,
    }
    month = months.get(match.group(2).lower())
    if month is None:
        return None
    try:
        return datetime(2000 + int(match.group(3)), month, int(match.group(1))).date().isoformat()
    except ValueError:
        return None


def parse_mtr_listing(page_html: str, base_url: str = MTR_LISTING_URL) -> list[dict[str, object]]:
    """Parse the public Taleo list embedded in MTR's official careers page."""
    list_match = re.search(
        r"api\.fillList\(\s*['\"]requisitionListInterface['\"]\s*,\s*"
        r"['\"]listRequisition['\"]\s*,\s*\[(.*?)\]\s*\);",
        page_html,
        flags=re.I | re.S,
    )
    if not list_match:
        return []
    values = [unquote(item) for item in re.findall(r"'((?:\\.|[^'])*)'", list_match.group(1))]
    postings: list[dict[str, object]] = []
    for index in range(0, max(0, len(values) - 18)):
        internal_id = values[index]
        title = values[index + 1].strip()
        job_number = values[index + 9].strip()
        location = values[index + 10].strip()
        schedule = values[index + 16].strip()
        posted_at = values[index + 17].strip()
        deadline = _taleo_date(values[index + 18])
        is_record = (
            internal_id.isdigit()
            and values[index + 2] == internal_id
            and values[index + 3].strip() == title
            and bool(re.fullmatch(r"[A-Za-z0-9]+", job_number))
            and _taleo_date(posted_at) is not None
            and deadline is not None
        )
        if not is_record:
            continue
        detail_url = (
            "https://careers.mtr.com.hk/careersection/mtr_external/jobdetail.ftl"
            f"?job={job_number}&lang=en"
        )
        postings.append(
            make_posting(
                company="MTR Corporation",
                title=clean_text(title),
                region="HONG_KONG",
                industry="Transport / Infrastructure",
                location=clean_text(location) or "Hong Kong",
                official_url=detail_url,
                source="MTR Careers",
                detail=f"港铁官方公开职位；工作安排：{clean_text(schedule) or '以官网为准'}。",
                application_deadline=deadline,
            )
        )
    return postings


def parse_sands_china_listing(
    page_html: str, base_url: str = SANDS_CHINA_LISTING_URL
) -> list[dict[str, object]]:
    """Create one transparent entry per department-level official vacancy PDF."""
    active_html = re.sub(r"<!--.*?-->", "", page_html, flags=re.S)
    section_pattern = re.compile(
        r"<p\b[^>]*class=[\"'][^\"']*\bhr_sec-title\b[^\"']*[\"'][^>]*>"
        r"(.*?)</p>(.*?)(?=<p\b[^>]*class=[\"'][^\"']*\bhr_sec-title\b|$)",
        flags=re.I | re.S,
    )
    postings: list[dict[str, object]] = []
    for heading, section in section_pattern.findall(active_html):
        document_match = re.search(
            r"href=[\"'](https://assets\.sandsresortsmacao\.cn/"
            r"content/HR/career/poster/[^\"']+\.pdf)[\"']",
            section,
            flags=re.I,
        )
        roles_match = re.search(
            r"<p\b[^>]*class=[\"'][^\"']*\bhr_main-text\b[^\"']*[\"'][^>]*>"
            r"(.*?)</p>",
            section,
            flags=re.I | re.S,
        )
        department = clean_text(heading)
        if not document_match or not roles_match or not department:
            continue
        roles = clean_text(roles_match.group(1)).replace("•", " · ").strip(" ·")
        document_url = urljoin(base_url, html_module.unescape(document_match.group(1)))
        postings.append(
            make_posting(
                company="Sands China",
                title=f"{department} Opportunities",
                region="MACAO",
                industry=department,
                location="Macao",
                official_url=document_url,
                source="Sands China Careers",
                detail=(
                    f"金沙中国官网当前 {department} 招聘方向：{roles[:500]}。"
                    "具体职责和申请方式请查看官方职位说明。"
                ),
            )
        )
    return postings


SOURCE_REGISTRY: dict[str, CareerSource] = {
    source.key: source
    for source in (
        CareerSource(
            key="bytedance",
            display_name="ByteDance Campus Careers",
            region="MAINLAND_CHINA",
            urls=BYTEDANCE_JOBS,
            parser=parse_bytedance_detail,
            listing_page=False,
        ),
        CareerSource(
            key="wynn-macau",
            display_name="Wynn Careers Macau",
            region="MACAO",
            urls=(WYNN_LISTING_URL,),
            parser=parse_wynn_listing,
            listing_page=True,
        ),
        CareerSource(
            key="hkjc",
            display_name="HKJC Careers",
            region="HONG_KONG",
            urls=(HKJC_LISTING_URL,),
            parser=parse_hkjc_listing,
            listing_page=True,
        ),
        CareerSource(
            key="mtr",
            display_name="MTR Careers",
            region="HONG_KONG",
            urls=(MTR_LISTING_URL,),
            parser=parse_mtr_listing,
            listing_page=True,
        ),
        CareerSource(
            key="sands-china",
            display_name="Sands China Careers",
            region="MACAO",
            urls=(SANDS_CHINA_LISTING_URL,),
            parser=parse_sands_china_listing,
            listing_page=True,
        ),
    )
}


def choose_relevant(postings: Iterable[dict[str, object]], limit: int) -> list[dict[str, object]]:
    values = list(postings)
    keywords = ("intern", "graduate", "trainee", "assistant", "engineer", "实习", "毕业")
    preferred = [
        item for item in values
        if any(
            keyword in f"{item.get('positionTitle', '')} {item.get('description', '')}".lower()
            for keyword in keywords
        )
    ]
    remaining = [item for item in values if item not in preferred]
    return (preferred + remaining)[:limit]


def resolve_sources(selected_keys: Iterable[str] | None = None) -> list[CareerSource]:
    keys = list(dict.fromkeys(selected_keys or SOURCE_REGISTRY.keys()))
    unknown = [key for key in keys if key not in SOURCE_REGISTRY]
    if unknown:
        available = ", ".join(SOURCE_REGISTRY)
        raise ValueError(f"Unknown source(s): {', '.join(unknown)}. Available: {available}")
    return [SOURCE_REGISTRY[key] for key in keys]


def crawl_source(
    source: CareerSource,
    limit: int,
    *,
    fetcher: Callable[[str], str] = fetch_text,
    sleeper: Callable[[float], None] = time.sleep,
) -> tuple[list[dict[str, object]], list[str]]:
    postings: list[dict[str, object]] = []
    warnings: list[str] = []
    urls = source.urls[:1] if source.listing_page else source.urls[:limit]
    for url in urls:
        try:
            parsed = source.parser(fetcher(url), url)
            postings.extend(choose_relevant(parsed, limit) if source.listing_page else parsed)
        except Exception as exc:  # One broken page must not discard another source.
            warnings.append(f"{source.display_name} fetch failed for {url}: {exc}")
        sleeper(REQUEST_INTERVAL_SECONDS)
    return postings[:limit], warnings


def crawl(
    limit_per_source: int,
    selected_sources: Iterable[str] | None = None,
    *,
    fetcher: Callable[[str], str] = fetch_text,
    sleeper: Callable[[float], None] = time.sleep,
) -> tuple[list[dict[str, object]], list[str]]:
    postings: list[dict[str, object]] = []
    warnings: list[str] = []

    for source in resolve_sources(selected_sources):
        source_postings, source_warnings = crawl_source(
            source, limit_per_source, fetcher=fetcher, sleeper=sleeper
        )
        postings.extend(source_postings)
        warnings.extend(source_warnings)

    unique: dict[str, dict[str, object]] = {}
    for posting in postings:
        unique[str(posting["officialUrl"])] = posting
    return list(unique.values()), warnings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit-per-source", type=int, default=2)
    parser.add_argument(
        "--source",
        action="append",
        choices=tuple(SOURCE_REGISTRY),
        help="Only crawl this registered source. Repeat to select multiple sources.",
    )
    parser.add_argument("--list-sources", action="store_true", help="List registered sources and exit.")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.list_sources:
        for source in SOURCE_REGISTRY.values():
            print(f"{source.key}\t{source.region}\t{source.display_name}\t{source.urls[0]}")
        return 0
    if args.output is None:
        parser.error("--output is required unless --list-sources is used")
    if args.limit_per_source < 1 or args.limit_per_source > 10:
        parser.error("--limit-per-source must be between 1 and 10")

    selected = resolve_sources(args.source)
    postings, warnings = crawl(args.limit_per_source, [source.key for source in selected])
    payload = {
        "crawledAt": datetime.now(timezone.utc).isoformat(),
        "notice": "Public career-page data for demonstration. Verify availability on the official page before applying.",
        "selectedSources": [
            {"key": source.key, "name": source.display_name, "region": source.region}
            for source in selected
        ],
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
