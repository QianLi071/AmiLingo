package com.amilingo.platform.module.lang.enums;

/** 分科（与前端 profile 的 L/R/W/S 键对齐）。 */
public enum Section {
    LISTENING("L", "听力"),
    READING("R", "阅读"),
    WRITING("W", "写作"),
    SPEAKING("S", "口语");

    private final String code;
    private final String displayName;

    Section(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Section fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("分科代码不能为空");
        }
        for (Section s : values()) {
            if (s.code.equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知分科代码: " + code);
    }
}
