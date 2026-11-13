package com.amilingo.platform.module.career.service;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.module.career.dto.request.JobFitAssessmentRequest;
import com.amilingo.platform.module.career.dto.response.JobFitAssessmentView;
import com.amilingo.platform.module.career.dto.response.JobFitAssessmentView.FitCriterionView;
import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobFitLevel;
import com.amilingo.platform.module.career.repository.JobPostingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 使用透明规则比较学生填写的信息和岗位文本。
 * 结果只用于整理申请准备事项，不预测录用概率，也不代替人工决策。
 */
@Service
public class JobFitAssessmentService {

    private static final Map<String, List<String>> STUDY_FIELD_TERMS = studyFieldTerms();
    private static final Map<String, List<String>> SKILL_TERMS = skillTerms();

    private final JobPostingRepository jobPostingRepository;

    public JobFitAssessmentService(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    @Transactional(readOnly = true)
    public JobFitAssessmentView assess(Long jobId, JobFitAssessmentRequest request) {
        JobPosting job = jobPostingRepository.findByIdAndActiveTrue(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "岗位不存在或已下线"));
        return assess(job, request);
    }

    JobFitAssessmentView assess(JobPosting job, JobFitAssessmentRequest request) {
        String jobText = buildJobText(job, request.fullJobDescription());
        List<FitCriterionView> criteria = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> toVerify = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int earned = 0;
        int available = 0;

        if (request.preferredRegion() != null) {
            available += 15;
            boolean match = request.preferredRegion() == job.getRegion();
            int score = match ? 15 : 0;
            earned += score;
            criteria.add(new FitCriterionView("地区偏好", score, 15,
                    match ? "岗位地区符合你的偏好。" : "岗位地区与当前偏好不同。"));
            if (match) {
                evidence.add("岗位地区符合你选择的求职范围");
            } else {
                toVerify.add("确认是否愿意前往该地区，以及签证、工作许可或通勤条件");
            }
        }

        if (request.preferredJobType() != null) {
            available += 15;
            boolean match = request.preferredJobType() == job.getJobType();
            int score = match ? 15 : 0;
            earned += score;
            criteria.add(new FitCriterionView("岗位类型", score, 15,
                    match ? "岗位类型符合你的选择。" : "岗位类型与当前选择不同。"));
            if (match) {
                evidence.add("岗位性质符合你的求职目标");
            } else {
                toVerify.add("确认该岗位的用工性质、实习周期或毕业时间要求");
            }
        }

        String studyField = detectStudyField(request.major());
        if (studyField != null) {
            available += 25;
            boolean match = containsAny(jobText, STUDY_FIELD_TERMS.get(studyField));
            int score = match ? 25 : 8;
            earned += score;
            String fieldLabel = STUDY_FIELD_TERMS.get(studyField).getFirst();
            criteria.add(new FitCriterionView("专业方向", score, 25,
                    match
                            ? "岗位文本出现了与“" + fieldLabel + "”方向相关的内容。"
                            : "公开岗位文本不足以确认是否接受你的专业方向。"));
            if (match) {
                evidence.add("岗位方向与你填写的专业领域存在文本关联");
            } else {
                toVerify.add("在官网完整职位说明中核对专业限制及可接受的相近专业");
            }
        } else {
            toVerify.add("当前无法从专业名称识别所属方向，请直接核对官网的专业要求");
        }

        List<String> skills = normalizeSkills(request.skills());
        if (!skills.isEmpty()) {
            available += 35;
            List<String> matchedSkills = skills.stream().filter(skill -> containsTerm(jobText, skill)).toList();
            int score = Math.round(35F * matchedSkills.size() / skills.size());
            earned += score;
            criteria.add(new FitCriterionView("技能关键词", score, 35,
                    matchedSkills.isEmpty()
                            ? "岗位文本中暂未找到你填写的技能关键词。"
                            : "岗位文本与这些技能相呼应：" + String.join("、", matchedSkills)));
            if (!matchedSkills.isEmpty()) {
                evidence.add("已匹配技能：" + String.join("、", matchedSkills));
            }

            List<String> mentionedSkills = detectMentionedSkills(jobText);
            List<String> missing = mentionedSkills.stream()
                    .filter(item -> skills.stream().noneMatch(skill -> equivalentSkill(skill, item)))
                    .limit(5)
                    .toList();
            if (!missing.isEmpty()) {
                toVerify.add("岗位文本提到但你的技能清单未填写：" + String.join("、", missing));
            }

            available += 10;
            boolean demonstrated = matchedSkills.stream()
                    .anyMatch(skill -> containsTerm(normalize(request.experienceSummary()), skill));
            int experienceScore = demonstrated ? 10 : hasText(request.experienceSummary()) ? 4 : 0;
            earned += experienceScore;
            criteria.add(new FitCriterionView("经历佐证", experienceScore, 10,
                    demonstrated
                            ? "你的经历描述能够佐证至少一项匹配技能。"
                            : hasText(request.experienceSummary())
                                    ? "已填写经历，但暂未和岗位技能形成明确对应。"
                                    : "尚未填写项目、实习或社团经历。"));
            if (demonstrated) {
                evidence.add("经历描述能够支持部分技能匹配");
            } else {
                actions.add("补充一个能证明相关技能的项目、课程、实习或社团案例");
            }
        } else {
            toVerify.add("至少填写一项真实技能，才能进行技能关键词比较");
            actions.add("整理自己能用项目或作品证明的技能，不要只填写泛化形容词");
        }

        int referenceScore = available == 0 ? 0 : Math.round(100F * earned / available);
        String informationQuality = informationQuality(job, request.fullJobDescription());
        JobFitLevel level = fitLevel(referenceScore, informationQuality);

        if ("LIMITED".equals(informationQuality)) {
            toVerify.add("当前岗位摘要信息有限，建议粘贴官网完整职位说明后重新分析");
            actions.add("打开企业官网，核对职责、必备条件、截止日期和工作地点");
        }
        actions.add("用匹配证据调整简历表述，并保留原始经历事实");
        actions.add("是否投递应同时考虑个人兴趣、发展方向和现实条件");

        return new JobFitAssessmentView(
                String.valueOf(job.getId()),
                job.getCompanyName(),
                job.getPositionTitle(),
                referenceScore,
                level,
                informationQuality,
                summary(level, informationQuality),
                List.copyOf(criteria),
                unique(evidence),
                unique(toVerify),
                unique(actions),
                "匹配结果基于你填写的信息和岗位文本，只用于辅助准备，不代表录用概率，也不构成投递或职业选择建议。"
        );
    }

    private String buildJobText(JobPosting job, String fullDescription) {
        return normalize(String.join(" ",
                safe(job.getPositionTitle()), safe(job.getIndustry()), safe(job.getDescription()),
                safe(job.getRequirements()), safe(fullDescription)));
    }

    private String detectStudyField(String major) {
        String normalizedMajor = normalize(major);
        return STUDY_FIELD_TERMS.entrySet().stream()
                .filter(entry -> containsAny(normalizedMajor, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<String> detectMentionedSkills(String text) {
        return SKILL_TERMS.entrySet().stream()
                .filter(entry -> containsAny(text, entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean equivalentSkill(String candidateSkill, String detectedSkill) {
        if (containsTerm(candidateSkill, detectedSkill) || containsTerm(detectedSkill, candidateSkill)) {
            return true;
        }
        List<String> aliases = SKILL_TERMS.getOrDefault(detectedSkill, List.of(detectedSkill));
        return aliases.stream().anyMatch(alias -> containsTerm(candidateSkill, alias));
    }

    private List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .limit(30)
                .toList();
    }

    private String informationQuality(JobPosting job, String fullDescription) {
        if (hasText(fullDescription) && fullDescription.trim().length() >= 160) {
            return "DETAILED";
        }
        int sourceLength = safe(job.getDescription()).length() + safe(job.getRequirements()).length();
        return sourceLength >= 300 ? "STANDARD" : "LIMITED";
    }

    private JobFitLevel fitLevel(int score, String informationQuality) {
        if (score >= 75 && !"LIMITED".equals(informationQuality)) {
            return JobFitLevel.STRONG_MATCH;
        }
        if (score >= 45) {
            return JobFitLevel.POSSIBLE_MATCH;
        }
        return JobFitLevel.NEEDS_REVIEW;
    }

    private String summary(JobFitLevel level, String informationQuality) {
        if ("LIMITED".equals(informationQuality)) {
            return "目前只能形成初步参考：公开岗位摘要较少，请补充官网完整职位说明后再判断。";
        }
        return switch (level) {
            case STRONG_MATCH -> "你提供的信息与岗位文本存在多项明确对应，可以进一步准备有证据的申请材料。";
            case POSSIBLE_MATCH -> "岗位具备部分匹配点，同时还有若干条件需要核实。";
            case NEEDS_REVIEW -> "当前可确认的匹配证据较少，建议先核对条件并补充真实经历信息。";
        };
    }

    private boolean containsAny(String text, List<String> terms) {
        return terms != null && terms.stream().anyMatch(term -> containsTerm(text, term));
    }

    private boolean containsTerm(String text, String term) {
        if (!hasText(text) || !hasText(term)) {
            return false;
        }
        String haystack = normalize(text);
        String needle = normalize(term);
        if (needle.matches("[a-z0-9+#./ -]+")) {
            Pattern tokenPattern = Pattern.compile(
                    "(?<![a-z0-9])" + Pattern.quote(needle) + "(?![a-z0-9])"
            );
            return tokenPattern.matcher(haystack).find();
        }
        return haystack.contains(needle);
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<String> unique(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static Map<String, List<String>> studyFieldTerms() {
        Map<String, List<String>> terms = new LinkedHashMap<>();
        terms.put("COMPUTING", List.of("计算机与信息技术", "计算机", "软件", "人工智能", "数据", "算法", "信息技术", "computer", "software", "data", "algorithm", "technology", "ai"));
        terms.put("ENGINEERING", List.of("工程技术", "工程", "电气", "电子", "机械", "自动化", "engineering", "electrical", "mechanical", "hardware"));
        terms.put("BUSINESS", List.of("商业与金融", "金融", "会计", "市场", "管理", "经济", "business", "finance", "accounting", "marketing", "management"));
        terms.put("LIFE_SCIENCE", List.of("生命与医疗", "医学", "生物", "药学", "医疗", "medicine", "biology", "medical", "healthcare"));
        terms.put("HUMANITIES", List.of("人文与社会科学", "语言", "传播", "新闻", "社会", "法律", "历史", "language", "communication", "journalism", "social", "law", "history"));
        return Collections.unmodifiableMap(terms);
    }

    private static Map<String, List<String>> skillTerms() {
        Map<String, List<String>> terms = new LinkedHashMap<>();
        terms.put("Java", List.of("java"));
        terms.put("Python", List.of("python"));
        terms.put("SQL", List.of("sql", "数据库"));
        terms.put("C/C++", List.of("c++", "c语言"));
        terms.put("人工智能", List.of("人工智能", " ai ", "machine learning", "机器学习"));
        terms.put("数据分析", List.of("数据分析", "data analysis", "analytics"));
        terms.put("算法", List.of("算法", "algorithm"));
        terms.put("云计算", List.of("云计算", "cloud"));
        terms.put("网络安全", List.of("网络安全", "cybersecurity", "security"));
        terms.put("硬件", List.of("硬件", "hardware"));
        terms.put("Excel", List.of("excel"));
        terms.put("市场营销", List.of("市场营销", "marketing"));
        terms.put("项目管理", List.of("项目管理", "project management"));
        terms.put("沟通协作", List.of("沟通", "协作", "communication", "collaboration"));
        terms.put("英语", List.of("英语", "english"));
        terms.put("粤语", List.of("粤语", "cantonese"));
        terms.put("普通话", List.of("普通话", "mandarin"));
        return Collections.unmodifiableMap(terms);
    }
}
