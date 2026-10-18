package com.amilingo.platform.module.lang.enums;

/** 考试类型。 */
public enum ExamType {
    CET4("四级", "CET-4", 15.0),
    CET6("六级", "CET-6", 15.0),
    IELTS_A("Academic", "IELTS 学术类", 9.0),
    IELTS_G("General", "IELTS 培训类", 9.0),
    TOEFL("TOEFL", "托福", 120.0),
    GMAT("GMAT", "经企管理研究生入学考试", 800.0);

    private final String level;
    private final String displayName;
    private final double maxScore;

    ExamType(String level, String displayName, double maxScore) {
        this.level = level;
        this.displayName = displayName;
        this.maxScore = maxScore;
    }

    public String getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMaxScore() {
        return maxScore;
    }

    /** 容错解析：支持 "IELTS" 归一到 IELTS_A，非法值默认 IELTS_A。 */
    public static ExamType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return IELTS_A;
        }
        String normalized = code.trim().toUpperCase();
        if ("IELTS".equals(normalized)) {
            return IELTS_A;
        }
        for (ExamType t : values()) {
            if (t.name().equals(normalized)) {
                return t;
            }
        }
        return IELTS_A;
    }
}
