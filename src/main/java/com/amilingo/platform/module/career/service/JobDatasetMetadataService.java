package com.amilingo.platform.module.career.service;

import com.amilingo.platform.module.career.dto.response.JobDatasetMetadataView;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobDatasetMetadataService {

    private static final String DATASET_RESOURCE = "career/job-postings.crawled.json";
    private final ObjectMapper objectMapper;

    public JobDatasetMetadataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JobDatasetMetadataView get() {
        ClassPathResource resource = new ClassPathResource(DATASET_RESOURCE);
        try (InputStream input = resource.getInputStream()) {
            return read(input);
        } catch (IOException exception) {
            return new JobDatasetMetadataView(null, null, 0,
                    "岗位数据时间暂不可用，请在企业官网核对最新信息。", List.of("metadata unavailable"));
        }
    }

    JobDatasetMetadataView read(InputStream input) throws IOException {
        JsonNode root = objectMapper.readTree(input);
        List<String> warnings = new ArrayList<>();
        JsonNode warningNodes = root.path("warnings");
        if (warningNodes.isArray()) {
            for (JsonNode warning : warningNodes) {
                if (!warning.asText().isBlank()) warnings.add(warning.asText());
            }
        }
        return new JobDatasetMetadataView(
                text(root.path("crawledAt")),
                text(root.path("audit").path("auditedAt")),
                root.path("count").asInt(0),
                text(root.path("notice")),
                List.copyOf(warnings)
        );
    }

    private String text(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
