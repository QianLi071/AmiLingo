"""写作批改模块"""
from typing import Any, Dict

from fastapi import APIRouter, HTTPException

from backend.app.core.config import settings
from backend.app.schemas.request import WritingGradeImageRequest, WritingGradeRequest
from backend.app.schemas.response import ApiResponse, WritingGradeResponse
from backend.app.services.ai_client import ai_client, embed_client
from backend.app.services.writing_service import writing_service

router = APIRouter(prefix="/writing", tags=["writing"])


@router.post("/grade", response_model=ApiResponse[WritingGradeResponse])
async def grade(req: WritingGradeRequest) -> ApiResponse[WritingGradeResponse]:
    """提交作文并获取 AI 批改报告。

    Args:
        req: 批改请求，包含作文正文、题目、考试类型等。

    Returns:
        ApiResponse 包装的批改报告。

    Raises:
        HTTPException 503: llama-server 未启动或不可达。
        HTTPException 500: 其他服务异常。
    """
    try:
        result = writing_service.grade_essay(
            content=req.content,
            topic=req.topic,
            level=req.level,
            exam_type=req.exam_type,
        )
        return ApiResponse[WritingGradeResponse](success=True, message="ok", data=result)
    except RuntimeError as e:
        # llama-server 未启动时 ai_client 会抛 RuntimeError
        raise HTTPException(status_code=503, detail=f"AI 服务不可用: {e}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"批改失败: {e}")


@router.post("/grade-image", response_model=ApiResponse[WritingGradeResponse])
async def grade_image(req: WritingGradeImageRequest) -> ApiResponse[WritingGradeResponse]:
    """提交作文图片，由多模态模型识别并批改。

    需要 llama-server 以 --mmproj 启动（backend\\scripts\\start_llama.bat 已配置）。
    返回结构与 /grade 一致，其中 content 字段为模型识别出的作文原文。

    Args:
        req: 图片批改请求，包含图片 base64、题目、考试类型等。

    Returns:
        ApiResponse 包装的批改报告。

    Raises:
        HTTPException 503: llama-server 未启动或不支持图片。
        HTTPException 500: 其他服务异常（如模型输出解析失败）。
    """
    try:
        data_url = f"data:image/{req.image_type};base64,{req.image_base64}"
        result = writing_service.grade_essay_image(
            image_data_url=data_url,
            topic=req.topic,
            level=req.level,
            exam_type=req.exam_type,
        )
        return ApiResponse[WritingGradeResponse](success=True, message="ok", data=result)
    except RuntimeError as e:
        # llama-server 未启动 / 未启用 mmproj 时 ai_client 会抛 RuntimeError
        raise HTTPException(status_code=503, detail=f"AI 服务不可用: {e}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"图片批改失败: {e}")


@router.get("/health")
async def health() -> Dict[str, Any]:
    """检查两个 llama-server（对话 8081 / 向量 8082）的健康状态。

    Returns:
        包含 llm 和 embedding 两个服务地址及状态的字典。
    """
    return {
        "llm": {
            "url": settings.LLM_BASE_URL,
            "status": "ok" if ai_client.health() else "unreachable",
        },
        "embedding": {
            "url": settings.EMBEDDING_BASE_URL,
            "status": "ok" if embed_client.health() else "unreachable",
        },
    }
