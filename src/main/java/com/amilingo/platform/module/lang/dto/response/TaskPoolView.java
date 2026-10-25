package com.amilingo.platform.module.lang.dto.response;

import java.util.List;

/** 任务池只读视图（接口约定②：任务类型、工作量、难度标签）。 */
public record TaskPoolView(List<Entry> tasks) {

    public record Entry(String code, String section, String difficulty, int minutes,
                        String taskType, String title, String objective) {
    }
}
