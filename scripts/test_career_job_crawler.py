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


if __name__ == "__main__":
    unittest.main()
