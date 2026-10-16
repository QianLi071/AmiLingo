package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 任务池（任务模块的只读桩实现，对应接口约定②）：
 * 提供任务类型、预估工作量（分钟）、难度标签与默认学习目标，供 AI 做时段匹配分配。
 * 后续任务模块自研落地后，可用真实任务池实现替换本类。
 */
@Component
public class TaskPoolCatalog {

    /** 任务难度：HIGH 高难度只进 🟢；MEDIUM 中等进 ⚪/🟢；LIGHT 轻量进 🟡（也可填 ⚪）。 */
    public enum Difficulty { HIGH, MEDIUM, LIGHT }

    /**
     * @param code       任务模板编码
     * @param section    分科
     * @param difficulty 难度
     * @param minutes    预估工作量（分钟）
     * @param taskType   任务类型
     * @param title      任务标题
     * @param objective  绑定的学习目标
     */
    public record TaskEntry(String code, Section section, Difficulty difficulty, int minutes,
                            String taskType, String title, String objective) {
    }

    private static final List<TaskEntry> POOL = List.of(
            // ---------------- 听力 ----------------
            new TaskEntry("L_H_1", Section.LISTENING, Difficulty.HIGH, 50, "听力套题",
                    "听力套题限时训练", "完成一套完整听力，错题精听归因，目标正确率 ≥80%"),
            new TaskEntry("L_H_2", Section.LISTENING, Difficulty.HIGH, 50, "精听",
                    "Section 精听训练", "听写关键句 + 跟读纠音，攻克连读与定位词"),
            new TaskEntry("L_M_1", Section.LISTENING, Difficulty.MEDIUM, 30, "听力题型",
                    "听力题型专练", "选择/地图/匹配题各 1 组，总结题型信号词"),
            new TaskEntry("L_L_1", Section.LISTENING, Difficulty.LIGHT, 15, "泛听",
                    "泛听磨耳朵", "真题音频/播客 15 分钟，捕捉主旨与 3 个细节"),
            new TaskEntry("L_L_2", Section.LISTENING, Difficulty.LIGHT, 10, "词汇背诵",
                    "听力场景词背诵", "背诵 15 个场景词汇并跟读发音"),
            // ---------------- 阅读 ----------------
            new TaskEntry("R_H_1", Section.READING, Difficulty.HIGH, 50, "阅读套题",
                    "阅读套题限时训练", "限时完成 3 篇阅读，错题回原文定位归因"),
            new TaskEntry("R_H_2", Section.READING, Difficulty.HIGH, 50, "精读",
                    "真题文章精读", "长难句拆解 5 处 + 同义替换整理 10 组"),
            new TaskEntry("R_M_1", Section.READING, Difficulty.MEDIUM, 30, "阅读题型",
                    "阅读题型专练", "T/F/NG 与 Heading 题各 1 组，总结干扰项规律"),
            new TaskEntry("R_L_1", Section.READING, Difficulty.LIGHT, 25, "词汇背诵",
                    "高频词背诵", "背诵 30 个核心高频词并完成自测"),
            new TaskEntry("R_L_2", Section.READING, Difficulty.LIGHT, 10, "语法",
                    "长难句卡片", "拆解 2 个长难句结构并翻译"),
            // ---------------- 写作 ----------------
            new TaskEntry("W_H_1", Section.WRITING, Difficulty.HIGH, 50, "写作训练",
                    "Task 2 议论文成文", "限时完成一篇大作文并用 AI 评分，目标 ≥6.0"),
            new TaskEntry("W_M_1", Section.WRITING, Difficulty.MEDIUM, 45, "写作训练",
                    "Task 1 图表作文", "限时完成一篇小作文，核对数据描述完整性"),
            new TaskEntry("W_M_2", Section.WRITING, Difficulty.MEDIUM, 30, "写作素材",
                    "范文与论点积累", "学习 1 篇范文，积累 3 个论点与 5 个连接词"),
            new TaskEntry("W_L_1", Section.WRITING, Difficulty.LIGHT, 15, "写作素材",
                    "好句背诵", "背诵并默写 5 个高分模板句"),
            // ---------------- 口语 ----------------
            new TaskEntry("S_M_1", Section.SPEAKING, Difficulty.MEDIUM, 45, "口语训练",
                    "Part 2 话题独白", "话题卡限时准备 1 分钟、作答 2 分钟并录音复盘"),
            new TaskEntry("S_M_2", Section.SPEAKING, Difficulty.MEDIUM, 30, "口语训练",
                    "Part 1/3 问答串练", "题库问答 8 题，录音检查流利度与时态"),
            new TaskEntry("S_L_1", Section.SPEAKING, Difficulty.LIGHT, 15, "口语素材",
                    "跟读模仿", "影子跟读真题音频 15 分钟，纠正语音语调"),
            new TaskEntry("S_L_2", Section.SPEAKING, Difficulty.LIGHT, 10, "词汇背诵",
                    "口语语料积累", "积累 5 个地道表达并造句")
    );

    public List<TaskEntry> all() {
        return POOL;
    }

    /** 该分科在指定类型时间槽中可放置的任务（按偏好排序：高难度优先进 🟢，轻量只进 🟡）。 */
    public List<TaskEntry> candidates(Section section, TypedTimeSlot.Kind slotKind) {
        List<TaskEntry> list = new ArrayList<>();
        for (TaskEntry e : POOL) {
            if (e.section() != section) continue;
            if (allowed(e.difficulty(), slotKind)) list.add(e);
        }
        // 偏好：槽类型对应的首选难度排最前
        Difficulty preferred = switch (slotKind) {
            case DEEP -> Difficulty.HIGH;
            case FREE -> Difficulty.MEDIUM;
            case FRAGMENT -> Difficulty.LIGHT;
        };
        list.sort((a, b) -> Boolean.compare(b.difficulty() == preferred, a.difficulty() == preferred));
        return list;
    }

    /** 难度 × 槽类型匹配规则。 */
    public static boolean allowed(Difficulty difficulty, TypedTimeSlot.Kind slotKind) {
        return switch (slotKind) {
            case DEEP -> difficulty == Difficulty.HIGH || difficulty == Difficulty.MEDIUM;
            case FREE -> true; // 高/中/低均可（高强度溢出时可回落普通空闲）
            case FRAGMENT -> difficulty == Difficulty.LIGHT; // 碎片只放轻量短任务
        };
    }

    public Optional<TaskEntry> byCode(String code) {
        return POOL.stream().filter(e -> e.code().equals(code)).findFirst();
    }
}
