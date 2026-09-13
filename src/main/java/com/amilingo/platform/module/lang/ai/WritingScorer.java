package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.infra.ai.AiGateway;
import com.amilingo.platform.module.lang.ai.prompt.WritingPrompt;
import com.amilingo.platform.module.lang.dto.request.WritingGradeRequest;
import com.amilingo.platform.module.lang.dto.response.WritingGradeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WritingScorer {

    private final AiGateway aiGateway;  // 调用AI模型

    public WritingGradeResponse score(WritingGradeRequest req) {
        String system = WritingPrompt.SYSTEM;
        String user = WritingPrompt.USER_TEMPLATE.formatted(
            req.targetTotal(),
            req.targetParts(),
            "N/A",
            req.prompt(),
            req.essay()
        );

        return aiGateway.chatJson(system, user,WritingGradeResponse.class);
    }
}
