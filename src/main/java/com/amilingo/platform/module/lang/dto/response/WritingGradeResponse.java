package com.amilingo.platform.module.lang.dto.response;

import java.util.List;
import java.util.Map;

public record WritingGradeResponse(
    Double overall, //总分
    Map<String, Integer> breakDown,  //成绩分解
    String feedback,  //反馈
    List<String> nextActions  //下一步建议
) {
}
