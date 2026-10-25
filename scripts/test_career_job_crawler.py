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

    def test_parse_mtr_taleo_list_with_official_deadline(self):
        source = """
        <script>
        api.fillList('requisitionListInterface', 'listRequisition', [
          '104343','Graduate Engineer (Ref:260000ZJ)',
          '104343','Graduate Engineer (Ref:260000ZJ)',
          '104343','104343','104343','104343','104343',
          '260000ZJ','Hong Kong','false','','','','',
          'Full-time','16/Oct/26','29/Oct/26','Apply'
        ]);
        </script>
        """
        rows = crawler.parse_mtr_listing(source)
        self.assertEqual(1, len(rows))
        self.assertEqual("MTR Corporation", rows[0]["companyName"])
        self.assertEqual("GRADUATE_PROGRAM", rows[0]["jobType"])
        self.assertEqual("2026-10-29", rows[0]["applicationDeadline"])
        self.assertEqual(
            "https://careers.mtr.com.hk/careersection/mtr_external/jobdetail.ftl?job=260000ZJ&lang=en",
            rows[0]["officialUrl"],
        )

    def test_parse_sands_china_department_document(self):
        source = """
        <!-- <p class="hr_sec-title">Old Department</p>
        <p class="hr_main-text">Old Role</p>
        <p><a href="https://assets.sandsresortsmacao.cn/content/HR/career/poster/old.pdf">Details</a></p> -->
        <p class="hr_sec-title">Information Technology</p>
        <p class="hr_main-text"><span>&bull; Site Reliability Engineer<br />
        &bull; Senior DevOps Engineer</span></p>
        <p><a href="https://assets.sandsresortsmacao.cn/content/HR/career/poster/it_en.pdf">Details</a></p>
        <p class="hr_sec-title">Hotel Operations</p>
        <p class="hr_main-text">&bull; Butler</p>
        """
        rows = crawler.parse_sands_china_listing(source)
        self.assertEqual(1, len(rows))
        self.assertEqual("Sands China", rows[0]["companyName"])
        self.assertEqual("MACAO", rows[0]["region"])
        self.assertEqual("Information Technology Opportunities", rows[0]["positionTitle"])
        self.assertIn("Site Reliability Engineer", rows[0]["description"])
        self.assertTrue(rows[0]["officialUrl"].endswith("/it_en.pdf"))

    def test_parse_baidu_embedded_internship_data(self):
        source = r'''
        <script>
        window.__INITIAL_DATA__ = {
          "listData": {
            "recruitType": "INTERN",
            "listDetailData": [{
              "name": "算法实习生（J100001）",
              "postId": "e02d76eb-eb2f-4956-b4d2-dacaaaf01ca3",
              "postType": "技术",
              "workPlace": "北京市,深圳市",
              "workContent": "参与搜索算法研发",
              "serviceCondition": "熟悉 Python",
              "projectType": "日常实习项目"
            }]
          }
        };
        </script>
        '''
        rows = crawler.parse_baidu_listing(source)
        self.assertEqual(1, len(rows))
        self.assertEqual("Baidu", rows[0]["companyName"])
        self.assertEqual("INTERNSHIP", rows[0]["jobType"])
        self.assertEqual("北京市,深圳市", rows[0]["location"])
        self.assertIn("参与搜索算法研发", rows[0]["description"])
        self.assertEqual(
            "https://talent.baidu.com/jobs/detail/INTERN/e02d76eb-eb2f-4956-b4d2-dacaaaf01ca3",
            rows[0]["officialUrl"],
        )

    def test_parse_baidu_rejects_incomplete_records(self):
        source = '<script>window.__INITIAL_DATA__ = {"listData":{"recruitType":"INTERN","listDetailData":[{"name":"Missing ID"}]}};</script>'
        self.assertEqual([], crawler.parse_baidu_listing(source))

    def test_parse_baidu_campus_uses_graduate_detail_path(self):
        source = r'''<script>window.__INITIAL_DATA__ = {
          "detailData": {"projectType": undefined},
          "listData": {"recruitType": "GRADUATE", "listDetailData": [{
            "name": "机器学习工程师（J100002）",
            "postId": "13aac31e-1333-418f-a08d-0a2480510882",
            "postType": "技术", "workPlace": "北京市"
          }]}
        };</script>'''
        rows = crawler.parse_baidu_listing(source)
        self.assertEqual(1, len(rows))
        self.assertEqual("GRADUATE_PROGRAM", rows[0]["jobType"])
        self.assertIn("/jobs/detail/GRADUATE/", rows[0]["officialUrl"])

    def test_registry_has_one_official_source_for_each_region(self):
        regions = {source.region for source in crawler.SOURCE_REGISTRY.values()}
        self.assertEqual({"MAINLAND_CHINA", "HONG_KONG", "MACAO"}, regions)
        self.assertIn("mtr", crawler.SOURCE_REGISTRY)
        self.assertIn("sands-china", crawler.SOURCE_REGISTRY)
        self.assertIn("baidu-internship", crawler.SOURCE_REGISTRY)
        self.assertIn("baidu-campus", crawler.SOURCE_REGISTRY)
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
