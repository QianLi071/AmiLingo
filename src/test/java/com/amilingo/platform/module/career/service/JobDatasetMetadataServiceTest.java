package com.amilingo.platform.module.career.service;

import com.amilingo.platform.module.career.dto.response.JobDatasetMetadataView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobDatasetMetadataServiceTest {

    @Test
    void readsPublicDatasetMetadataWithoutExposingPostings() throws Exception {
        String json = """
                {
                  "crawledAt": "2026-10-18T01:00:00+00:00",
                  "notice": "Verify on the official page.",
                  "count": 3,
                  "warnings": ["one source was unavailable"],
                  "audit": {"auditedAt": "2026-10-18T01:01:00+00:00"},
                  "postings": [{"companyName": "Hidden from metadata response"}]
                }
                """;
        JobDatasetMetadataService service = new JobDatasetMetadataService(new ObjectMapper());
        JobDatasetMetadataView view = service.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("2026-10-18T01:00:00+00:00", view.crawledAt());
        assertEquals("2026-10-18T01:01:00+00:00", view.auditedAt());
        assertEquals(3, view.sourcePostingCount());
        assertEquals(1, view.warnings().size());
    }
}
