import unittest

import career_job_link_checker as checker


class CareerJobLinkCheckerTest(unittest.TestCase):
    def test_classifies_status_without_treating_restrictions_as_missing(self):
        self.assertEqual("REACHABLE", checker.classify(200))
        self.assertEqual("ACCESS_RESTRICTED", checker.classify(403))
        self.assertEqual("NOT_FOUND", checker.classify(404))
        self.assertEqual("TEMPORARY_FAILURE", checker.classify(503))

    def test_builds_report_with_injected_fetcher(self):
        payload = {"postings": [
            {"companyName": "A", "positionTitle": "Intern", "officialUrl": "https://example.com/1"},
            {"companyName": "B", "positionTitle": "Graduate", "officialUrl": "https://example.com/2"},
        ]}
        statuses = {"https://example.com/1": 200, "https://example.com/2": 410}
        report = checker.check_payload(payload, fetch_status=statuses.__getitem__, delay_seconds=0)
        self.assertEqual(2, report["checkedCount"])
        self.assertEqual(1, report["counts"]["REACHABLE"])
        self.assertEqual(1, report["counts"]["NOT_FOUND"])

    def test_network_errors_are_reported_for_manual_review(self):
        payload = {"postings": [{"companyName": "A", "positionTitle": "Intern", "officialUrl": "https://example.com/1"}]}

        def fail(_url):
            raise TimeoutError("timed out")

        report = checker.check_payload(payload, fetch_status=fail, delay_seconds=0)
        self.assertEqual("NETWORK_ERROR", report["results"][0]["result"])
        self.assertIn("manual review", report["notice"])


if __name__ == "__main__":
    unittest.main()
