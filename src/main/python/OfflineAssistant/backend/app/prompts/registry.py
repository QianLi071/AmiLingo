"""Prompt 注册表：根据 exam_type 选择对应 Prompt。

新增考试类型只需：

1. 写一个 xxx_prompts.py 定义评分 / 逐句批改 Prompt

2. 在下方 GRADE_PROMPTS / SUGGESTION_PROMPTS 注册一行
其他业务代码（writing_service / 路由）零改动。
"""
from backend.app.prompts.writing_prompts import GRADE_PROMPT as CET_GRADE, SUGGESTION_PROMPT as CET_SUGGEST
from backend.app.prompts.ielts_prompts import IELTS_GRADE_PROMPT

GRADE_PROMPTS = {
    "CET4": CET_GRADE,
    "CET6": CET_GRADE,
    "IELTS_A": IELTS_GRADE_PROMPT,
    "IELTS_G": IELTS_GRADE_PROMPT,
}

SUGGESTION_PROMPTS = {
    "CET4": CET_SUGGEST,
    "CET6": CET_SUGGEST,
    "IELTS_A": CET_SUGGEST,   # 雅思暂复用四六级逐句批改 Prompt
    "IELTS_G": CET_SUGGEST,
}

def get_grade_prompt(exam_type: str) -> str:
    if exam_type not in GRADE_PROMPTS:
        raise ValueError(f"不支持的 exam_type: {exam_type}")
    return GRADE_PROMPTS[exam_type]

def get_suggestion_prompt(exam_type: str) -> str:
    if exam_type not in SUGGESTION_PROMPTS:
        raise ValueError(f"不支持的 exam_type: {exam_type}")
    return SUGGESTION_PROMPTS[exam_type]
