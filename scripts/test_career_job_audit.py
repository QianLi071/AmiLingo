import unittest
from datetime import date

import career_job_audit as audit


def posting(**overrides):
    value = {
        "companyName": "Example",
        "positionTitle": "Software Intern",
        "region": "HONG_KONG",
        "jobType": "INTERNSHIP",
        "description": "Official public posting",
        "officialUrl": "https://example.com/jobs/1",
        "sourceName": "Example Careers",
        "applicationDeadline": None,
        "active": True,
    }
    value.update(overrides)
    return value


class CareerJobAuditTest(unittest.TestCase):
    def test_valid_payload_passes(self):
        payload = {"crawledAt": "2026-10-18T01:00:00+00:00", "count": 1, "postings": [posting()]}
        audited, report = audit.audit_payload(payload, today=date(2026, 10, 18))
        self.assertTrue(report["valid"])
        self.assertEqual(0, audited["audit"]["errorCount"])

    def test_expired_posting_is_deactivated(self):
        payload = {"crawledAt": "2026-10-18T01:00:00+00:00", "count": 1, "postings": [posting(applicationDeadline="2026-10-17")]}
        audited, report = audit.audit_payload(payload, today=date(2026, 10, 18))
        self.assertTrue(report["valid"])
        self.assertFalse(audited["postings"][0]["active"])
        self.assertEqual(1, audited["audit"]["deactivatedExpiredCount"])

    def test_unsafe_and_duplicate_urls_fail(self):
        payload = {
            "crawledAt": "2026-10-18T01:00:00+00:00", "count": 2,
            "postings": [posting(officialUrl="http://example.com/jobs/1"), posting(officialUrl="http://example.com/jobs/1")],
        }
        _, report = audit.audit_payload(payload, today=date(2026, 10, 18))
        self.assertFalse(report["valid"])
        self.assertTrue(any("HTTPS" in error for error in report["errors"]))
        self.assertTrue(any("duplicates" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
