import unittest

import career_job_crawler as crawler


class CareerJobCrawlerParserTest(unittest.TestCase):
    def test_parse_bytedance_title(self):
        rows = crawler.parse_bytedance_detail(
            "<html><head><title>算法实习生-AI应用与创新 - 加入字节跳动</title></head></html>",
            "https://jobs.bytedance.com/campus/position/1/detail",
        )
        self.assertEqual("算法实习生-AI应用与创新", rows[0]["positionTitle"])
        self.assertEqual("INTERNSHIP", rows[0]["jobType"])

    def test_parse_wynn_row(self):
        source = """
        <tr data-testid="table-row" aria-label="Senior Engineer - IT Infrastructure">
          <td>Senior Engineer - IT Infrastructure</td>
          <td>Information Technology</td><td>Wynn Macau</td>
          <td><a href="/open-roles/53/">View</a></td>
        </tr>
        """
        rows = crawler.parse_wynn_listing(source)
        self.assertEqual("MACAO", rows[0]["region"])
        self.assertEqual("Senior Engineer - IT Infrastructure", rows[0]["positionTitle"])
        self.assertTrue(str(rows[0]["officialUrl"]).endswith("/open-roles/53/"))

    def test_parse_hkjc_row(self):
        source = """
        <tr class="data-row">
          <td><span class="jobTitle hidden-phone"><a href="/job/Test/123/" class="jobTitle-link">Assistant Engineer</a></span></td>
          <td><span class="jobFacility">Information Technology</span></td>
          <td><span class="jobLocation">Happy Valley, Hong Kong Island, HK</span></td>
          <td><span class="jobShifttype">Full-time</span></td>
        </tr>
        """
        rows = crawler.parse_hkjc_listing(source)
        self.assertEqual("HONG_KONG", rows[0]["region"])
        self.assertEqual("Assistant Engineer", rows[0]["positionTitle"])
        self.assertIn("Full-time", rows[0]["description"])

    def test_registry_has_one_official_source_for_each_region(self):
        regions = {source.region for source in crawler.SOURCE_REGISTRY.values()}
        self.assertEqual({"MAINLAND_CHINA", "HONG_KONG", "MACAO"}, regions)
        self.assertEqual(
            ["hkjc", "bytedance"],
            [source.key for source in crawler.resolve_sources(["hkjc", "bytedance", "hkjc"])],
        )

    def test_unknown_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unknown source"):
            crawler.resolve_sources(["not-registered"])

    def test_crawl_can_select_one_source_without_network(self):
        page = """
        <tr class="data-row">
          <td><a class="jobTitle-link" href="/job/Test/123/">Graduate Engineer</a></td>
          <td><span class="jobFacility">Technology</span></td>
          <td><span class="jobLocation">Hong Kong</span></td>
          <td><span class="jobShifttype">Full-time</span></td>
        </tr>
        """
        fetched = []

        def fake_fetch(url):
            fetched.append(url)
            return page

        rows, warnings = crawler.crawl(
            2, ["hkjc"], fetcher=fake_fetch, sleeper=lambda _seconds: None
        )
        self.assertEqual([], warnings)
        self.assertEqual([crawler.HKJC_LISTING_URL], fetched)
        self.assertEqual(1, len(rows))
        self.assertEqual("HONG_KONG", rows[0]["region"])

    def test_source_failure_does_not_stop_another_source(self):
        wynn_page = """
        <tr data-testid="table-row"><td>Graduate Engineer</td><td>IT</td><td>Macao</td>
        <td><a href="/open-roles/7/">View</a></td></tr>
        """

        def fake_fetch(url):
            if url == crawler.HKJC_LISTING_URL:
                raise TimeoutError("test timeout")
            return wynn_page

        rows, warnings = crawler.crawl(
            2, ["hkjc", "wynn-macau"], fetcher=fake_fetch, sleeper=lambda _seconds: None
        )
        self.assertEqual(1, len(rows))
        self.assertEqual("MACAO", rows[0]["region"])
        self.assertEqual(1, len(warnings))
        self.assertIn("HKJC Careers", warnings[0])


if __name__ == "__main__":
    unittest.main()
