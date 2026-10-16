"""响应 Schema"""
from typing import Any, Dict, Generic, List, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    """统一响应包装。

    Attributes:
        success: 是否成功，默认 True。
        message: 消息，默认 "ok"。
        data: 业务数据，泛型 T。
    """

    success: bool = True
    message: str = "ok"
    data: T | None = None


class WritingGradeResponse(BaseModel):
    """作文批改响应（与 writing_service.grade_essay 返回值一一对应）。"""

    # ---- 基本信息 ----
    content: str
    topic: str
    level: str
    exam_type: str
    word_count: int
    basic_issues: List[Dict[str, Any]]

    # ---- 总分 ----
    total_score: float
    overall: float
    band: str
    band_comment: str
    total_106: float
    total_score_percent: float

    # ---- 诊断 ----
    scores: Dict[str, float]
    dimensions: Dict[str, float]
    dimension_labels: Dict[str, str]
    dimension_comments: Dict[str, str]
    overall_comment: str
    feedback: str
    next_actions: List[str]

    # ---- 逐句批改 ----
    sentences: List[Dict[str, Any]]
    summary: str

    # ---- 范文 ----
    similar_essays: List[Dict[str, Any]]
    generated_at: str
