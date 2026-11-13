import unittest

from career_job_merge import merge_payload


def posting(source, url, title, source_key=None):
    item = {"sourceName": source, "officialUrl": url, "positionTitle": title}
    if source_key:
        item["sourceKey"] = source_key
    return item


class CareerJobMergeTest(unittest.TestCase):
    def test_partial_update_preserves_unselected_sources(self):
        existing = {
            "postings": [
                posting("Baidu Talent", "https://jobs.example/baidu-old", "Old Baidu", "baidu-internship"),
                posting("MTR Careers", "https://jobs.example/mtr", "MTR", "mtr"),
            ]
        }
        update = {
            "selectedSources": [{"key": "baidu-internship", "name": "Baidu Talent Internships"}],
            "sourceResults": [{"key": "baidu-internship", "name": "Baidu Talent Internships", "status": "SUCCESS"}],
            "postings": [posting("Baidu Talent", "https://jobs.example/baidu-new", "New Baidu", "baidu-internship")],
        }
        merged = merge_payload(existing, update)
        self.assertEqual(2, merged["count"])
        self.assertEqual(
            {"https://jobs.example/baidu-new", "https://jobs.example/mtr"},
            {item["officialUrl"] for item in merged["postings"]},
        )

    def test_failed_source_keeps_previous_records(self):
        existing = {"postings": [posting("MTR Careers", "https://jobs.example/mtr", "MTR", "mtr")]}
        update = {
            "selectedSources": [{"key": "mtr", "name": "MTR Careers"}],
            "sourceResults": [{"key": "mtr", "name": "MTR Careers", "status": "FAILED"}],
            "postings": [],
        }
        merged = merge_payload(existing, update)
        self.assertEqual(1, merged["count"])
        self.assertEqual(1, merged["mergeSummary"]["retainedExistingCount"])

    def test_partial_source_keeps_missing_old_links_and_refreshes_duplicates(self):
        existing = {
            "postings": [
                posting("ByteDance Campus Careers", "https://jobs.example/1", "Old 1", "bytedance"),
                posting("ByteDance Campus Careers", "https://jobs.example/2", "Old 2", "bytedance"),
            ]
        }
        update = {
            "selectedSources": [{"key": "bytedance", "name": "ByteDance Campus Careers"}],
            "sourceResults": [{"key": "bytedance", "name": "ByteDance Campus Careers", "status": "PARTIAL"}],
            "postings": [posting("ByteDance Campus Careers", "https://jobs.example/1", "Fresh 1", "bytedance")],
        }
        merged = merge_payload(existing, update)
        self.assertEqual(2, merged["count"])
        by_url = {item["officialUrl"]: item for item in merged["postings"]}
        self.assertEqual("Fresh 1", by_url["https://jobs.example/1"]["positionTitle"])
        self.assertEqual("Old 2", by_url["https://jobs.example/2"]["positionTitle"])

    def test_legacy_baidu_urls_are_split_into_internship_and_campus_sources(self):
        existing = {
            "postings": [
                posting("Baidu Talent", "https://talent.baidu.com/jobs/detail/INTERN/old", "Old intern"),
                posting("Baidu Talent", "https://talent.baidu.com/jobs/detail/GRADUATE/keep", "Campus"),
            ]
        }
        update = {
            "selectedSources": [{"key": "baidu-internship", "name": "Baidu Talent Internships"}],
            "sourceResults": [{"key": "baidu-internship", "status": "SUCCESS"}],
            "postings": [
                posting("Baidu Talent", "https://talent.baidu.com/jobs/detail/INTERN/new", "New intern", "baidu-internship")
            ],
        }
        merged = merge_payload(existing, update)
        self.assertEqual(2, merged["count"])
        self.assertEqual(
            {"baidu-internship", "baidu-campus"},
            {item["sourceKey"] for item in merged["postings"]},
        )


if __name__ == "__main__":
    unittest.main()
