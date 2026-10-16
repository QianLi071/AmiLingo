package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.module.lang.entity.PracticeAttempt;
import com.amilingo.platform.module.lang.enums.Section;
import com.amilingo.platform.module.lang.repository.PracticeAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 薄弱点分析器：基于 {@link PracticeAttempt} 练习流水，统计各分科均分、次数与近期趋势，
 * 定位薄弱分科并给出可执行建议。统计在本地完成，快速、确定、无外部依赖。
 */
@Component
public class WeaknessAnalyzer {

    private final PracticeAttemptRepository practiceAttemptRepository;

    public WeaknessAnalyzer(PracticeAttemptRepository practiceAttemptRepository) {
        this.practiceAttemptRepository = practiceAttemptRepository;
    }

    public WeaknessReport analyze(Long userId) {
        List<PracticeAttempt> attempts = practiceAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<Section, List<PracticeAttempt>> bySection = new EnumMap<>(Section.class);
        for (PracticeAttempt attempt : attempts) {
            if (attempt.getOverall() != null) {
                bySection.computeIfAbsent(attempt.getSection(), s -> new ArrayList<>()).add(attempt);
            }
        }

        Map<Section, SectionStat> stats = new EnumMap<>(Section.class);
        for (Map.Entry<Section, List<PracticeAttempt>> e : bySection.entrySet()) {
            List<PracticeAttempt> list = e.getValue();
            double average = list.stream().mapToDouble(PracticeAttempt::getOverall).average().orElse(0.0);
            average = Math.round(average * 10) / 10.0;
            stats.put(e.getKey(), new SectionStat(average, list.size(), trendOf(list)));
        }

        List<Section> rankedWeak = new ArrayList<>(stats.keySet());
        rankedWeak.sort(Comparator.comparingDouble(s -> stats.get(s).average()));

        String summary = buildSummary(rankedWeak, stats);
        List<String> tips = buildTips(rankedWeak, stats);

        return new WeaknessReport(rankedWeak, stats, summary, tips);
    }

    /** 趋势：取最近最多 3 次（已按时间倒序），比较最新与最早。 */
    private static String trendOf(List<PracticeAttempt> orderedDesc) {
        List<PracticeAttempt> recent = orderedDesc.stream().limit(3).toList();
        if (recent.size() < 2) {
            return "数据不足";
        }
        double newest = recent.get(0).getOverall();
        double oldest = recent.get(recent.size() - 1).getOverall();
        double diff = newest - oldest;
        if (Math.abs(diff) < 0.25) {
            return "持平";
        }
        return diff > 0 ? "上升" : "下降";
    }

    private static String buildSummary(List<Section> rankedWeak, Map<Section, SectionStat> stats) {
        if (rankedWeak.isEmpty()) {
            return "暂无足够练习数据，建议先完成一次测评或若干练习。";
        }
        Section weakest = rankedWeak.get(0);
        SectionStat stat = stats.get(weakest);
        return String.format("当前最薄弱分科为%s（均分 %s，%d 次练习，近期%s），应优先安排专项训练。",
                weakest.getDisplayName(), stat.average(), stat.attempts(), stat.trend());
    }

    private static List<String> buildTips(List<Section> rankedWeak, Map<Section, SectionStat> stats) {
        List<String> tips = new ArrayList<>();
        int limit = Math.min(3, rankedWeak.size());
        for (int i = 0; i < limit; i++) {
            Section section = rankedWeak.get(i);
            SectionStat stat = stats.get(section);
            tips.add(switch (section) {
                case LISTENING -> "听力薄弱：每天精听 20 分钟并跟读，整理同义替换。";
                case READING -> "阅读薄弱：限时精练 + 长难句拆解，强化定位能力。";
                case WRITING -> "写作薄弱：每周写 2 篇并用 AI 评分，积累论点与连接词。";
                case SPEAKING -> "口语薄弱：按话题卡录音练习，注意流利度与发音。";
            });
            if ("下降".equals(stat.trend())) {
                tips.add(section.getDisplayName() + "成绩近期下滑，建议复盘最近错题、回归基础方法。");
            }
        }
        return tips;
    }

    /** 薄弱点分析报告。 */
    public record WeaknessReport(
            List<Section> rankedWeakSections,
            Map<Section, SectionStat> sectionStats,
            String summary,
            List<String> tips
    ) {
    }

    public record SectionStat(double average, int attempts, String trend) {
    }
}
