package com.amilingo.platform.module.lang.service;

import com.amilingo.platform.module.lang.dto.request.GoalRequest;
import com.amilingo.platform.module.lang.dto.response.GoalView;
import com.amilingo.platform.module.lang.entity.ExamGoal;
import com.amilingo.platform.module.lang.entity.ScoreTarget;
import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;
import com.amilingo.platform.module.lang.repository.ExamGoalRepository;
import com.amilingo.platform.module.lang.repository.ScoreTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 考试目标服务：目标与单科目标分的读写。 */
@Service
public class GoalService {

    private final ExamGoalRepository examGoalRepository;
    private final ScoreTargetRepository scoreTargetRepository;

    public GoalService(ExamGoalRepository examGoalRepository,
                       ScoreTargetRepository scoreTargetRepository) {
        this.examGoalRepository = examGoalRepository;
        this.scoreTargetRepository = scoreTargetRepository;
    }

    @Transactional
    public GoalView saveGoal(Long userId, GoalRequest request) {
        if (request.examDate() == null || request.examDate().isBlank()) {
            throw new IllegalArgumentException("考试日期不能为空");
        }
        ExamType examType = ExamType.fromCode(request.examType());
        LocalDate examDate = LocalDate.parse(request.examDate());

        ExamGoal goal = examGoalRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseGet(ExamGoal::new);
        goal.setUserId(userId);
        goal.setExamType(examType);
        goal.setExamDate(examDate);
        goal.setRegion(request.region());
        goal.setTargetTotal(request.targetTotal());
        examGoalRepository.save(goal);

        // 重建单科目标
        List<ScoreTarget> existing = scoreTargetRepository.findByUserIdAndGoalId(userId, goal.getId());
        scoreTargetRepository.deleteAll(existing);

        Map<String, Double> parts = request.targetParts();
        if (parts != null) {
            for (Section section : Section.values()) {
                Double value = parts.get(section.getCode());
                if (value != null) {
                    scoreTargetRepository.save(ScoreTarget.builder()
                            .userId(userId)
                            .goalId(goal.getId())
                            .section(section)
                            .targetScore(value)
                            .build());
                }
            }
        }
        return toView(goal);
    }

    @Transactional(readOnly = true)
    public GoalView getGoal(Long userId) {
        return examGoalRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toView)
                .orElse(null);
    }

    private GoalView toView(ExamGoal goal) {
        Map<String, Double> parts = new LinkedHashMap<>();
        for (ScoreTarget target : scoreTargetRepository.findByUserIdAndGoalId(goal.getUserId(), goal.getId())) {
            parts.put(target.getSection().getCode(), target.getTargetScore());
        }
        return new GoalView(
                goal.getId(),
                goal.getExamType().name(),
                goal.getExamDate().toString(),
                goal.getRegion(),
                goal.getTargetTotal(),
                parts,
                goal.getCreatedAt() != null ? goal.getCreatedAt().toString() : null
        );
    }
}
