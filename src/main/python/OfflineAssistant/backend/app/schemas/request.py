"""请求 Schema"""
from typing import Literal

from pydantic import BaseModel, Field


class WritingGradeRequest(BaseModel):
    """作文批改请求。

    Attributes:
        content: 作文正文（必填）。
        topic: 作文题目（必填）。
        level: 考试级别，如 "四级" / "六级" / "Academic"。
        exam_type: 考试类型，用于选择对应 Prompt。
    """

    content: str = Field(..., description="作文正文")
    topic: str = Field(..., description="作文题目")
    level: str = Field("四级", description="考试级别，如 四级/六级/Academic")
    exam_type: Literal["CET4", "CET6", "IELTS_A", "IELTS_G"] = Field(
        "CET4", description="考试类型: CET4/CET6/IELTS_A/IELTS_G"
    )


class WritingGradeImageRequest(BaseModel):
    """图片作文批改请求（多模态：先识别图片中的英文作文，再评分）。

    Attributes:
        image_base64: 作文图片的 base64 编码（不含 data: 前缀）。
        topic: 作文题目（必填）。
        level: 考试级别，如 "Academic" / "四级"。
        exam_type: 考试类型，用于选择对应 Prompt。
    """

    image_base64: str = Field(..., description="作文图片 base64（不含 data: 前缀）")
    topic: str = Field(..., description="作文题目")
    level: str = Field("Academic", description="考试级别，如 Academic/General/四级")
    exam_type: Literal["CET4", "CET6", "IELTS_A", "IELTS_G"] = Field(
        "IELTS_A", description="考试类型: CET4/CET6/IELTS_A/IELTS_G"
    )
    image_type: str = Field("jpeg", description="图片格式，如 jpeg/png/webp")
