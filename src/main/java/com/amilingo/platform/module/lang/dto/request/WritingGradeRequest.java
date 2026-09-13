package com.amilingo.platform.module.lang.dto.request;

import java.util.Map;

public record WritingGradeRequest(
    String examType,   //IELTS/TOEFL/GMAT
    String prompt,     //作文题目
    String essay,      //作文内容
    Double targetTotal, //目标总分
    Map<String, Double> targetParts  //单科目标分
) {
}
