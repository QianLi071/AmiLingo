package com.amilingo.platform.module.career.bootstrap;

import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import com.amilingo.platform.module.career.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingSeedLoaderTest {

    @Mock
    private JobPostingRepository repository;

    @Test
    void importsNewPostingAndRefreshesExistingOfficialUrl() throws Exception {
        JobPosting existing = JobPosting.builder()
                .id(10L)
                .companyName("Old company name")
                .positionTitle("Old title")
                .officialUrl("https://example.com/jobs/2")
                .build();
        when(repository.findByOfficialUrl(anyString()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        JobPostingSeedLoader loader = new JobPostingSeedLoader(repository, new ObjectMapper());

        int imported = loader.importFrom(new ByteArrayInputStream(json().getBytes(StandardCharsets.UTF_8)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobPosting>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<JobPosting> saved = captor.getValue();
        assertEquals(2, imported);
        assertEquals("Software Engineering Intern", saved.getFirst().getPositionTitle());
        assertEquals(JobRegion.HONG_KONG, saved.getFirst().getRegion());
        assertEquals(JobType.INTERNSHIP, saved.getFirst().getJobType());
        assertEquals(10L, saved.get(1).getId());
        assertEquals("Graduate Engineer", saved.get(1).getPositionTitle());
        assertEquals(JobRegion.MACAO, saved.get(1).getRegion());
    }

    private String json() {
        return """
                {
                  "postings": [
                    {
                      "companyName": "Example One",
                      "positionTitle": "Software Engineering Intern",
                      "region": "HONG_KONG",
                      "jobType": "INTERNSHIP",
                      "industry": "Technology",
                      "location": "Hong Kong",
                      "description": "Build software",
                      "requirements": "Students",
                      "applicationDeadline": null,
                      "officialUrl": "https://example.com/jobs/1",
                      "sourceName": "Official careers site",
                      "active": true
                    },
                    {
                      "companyName": "Example Two",
                      "positionTitle": "Graduate Engineer",
                      "region": "MACAO",
                      "jobType": "GRADUATE_PROGRAM",
                      "description": "Build systems",
                      "officialUrl": "https://example.com/jobs/2",
                      "sourceName": "Official careers site",
                      "active": true
                    }
                  ]
                }
                """;
    }
}
