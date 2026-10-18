package com.amilingo.platform.module.career.bootstrap;

import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import com.amilingo.platform.module.career.repository.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Synchronizes bundled, publicly sourced jobs using the official URL as identity. */
@Component
@ConditionalOnProperty(name = "career.seed.enabled", havingValue = "true", matchIfMissing = true)
public class JobPostingSeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobPostingSeedLoader.class);
    private static final String SEED_RESOURCE = "career/job-postings.crawled.json";

    private final JobPostingRepository repository;
    private final ObjectMapper objectMapper;

    public JobPostingSeedLoader(JobPostingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
        if (!resource.exists()) {
            log.warn("Career seed resource {} was not found; no jobs were imported", SEED_RESOURCE);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            int synchronizedCount = importFrom(input);
            log.info("Career job seed completed: {} posting(s) synchronized", synchronizedCount);
        }
    }

    int importFrom(InputStream input) throws IOException {
        JsonNode postingsNode = objectMapper.readTree(input).path("postings");
        if (!postingsNode.isArray()) {
            throw new IOException("Career seed file must contain a postings array");
        }

        List<JobPosting> synchronizedPostings = new ArrayList<>();
        Set<String> urlsInFile = new HashSet<>();
        for (JsonNode node : postingsNode) {
            String officialUrl = requiredText(node, "officialUrl");
            if (!urlsInFile.add(officialUrl)) {
                continue;
            }
            JobPosting posting = repository.findByOfficialUrl(officialUrl)
                    .orElseGet(JobPosting::new);
            apply(node, officialUrl, posting);
            synchronizedPostings.add(posting);
        }
        if (!synchronizedPostings.isEmpty()) {
            repository.saveAll(synchronizedPostings);
        }
        return synchronizedPostings.size();
    }

    private void apply(JsonNode node, String officialUrl, JobPosting posting) throws IOException {
        posting.setCompanyName(requiredText(node, "companyName"));
        posting.setPositionTitle(requiredText(node, "positionTitle"));
        posting.setRegion(parseEnum(JobRegion.class, requiredText(node, "region"), "region"));
        posting.setJobType(parseEnum(JobType.class, requiredText(node, "jobType"), "jobType"));
        posting.setIndustry(optionalText(node, "industry"));
        posting.setLocation(optionalText(node, "location"));
        posting.setDescription(requiredText(node, "description"));
        posting.setRequirements(optionalText(node, "requirements"));
        posting.setApplicationDeadline(optionalDate(node, "applicationDeadline"));
        posting.setOfficialUrl(officialUrl);
        posting.setSourceName(requiredText(node, "sourceName"));
        posting.setActive(node.path("active").asBoolean(true));
    }

    private String requiredText(JsonNode node, String field) throws IOException {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw new IOException("Career seed posting is missing required field: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private LocalDate optionalDate(JsonNode node, String field) throws IOException {
        String value = optionalText(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IOException("Career seed field " + field + " must use YYYY-MM-DD", exception);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String field) throws IOException {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported career seed value for " + field + ": " + value, exception);
        }
    }
}
