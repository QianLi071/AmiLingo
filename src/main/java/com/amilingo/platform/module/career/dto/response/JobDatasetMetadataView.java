package com.amilingo.platform.module.career.dto.response;

import java.util.List;

public record JobDatasetMetadataView(
        String crawledAt,
        String auditedAt,
        int sourcePostingCount,
        String notice,
        List<String> warnings
) {
}
