"""AI Tutor - AI 备考平台后端入口"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.app.api.v1 import quiz, stats, writing
from backend.app.core.config import settings

app = FastAPI(
    title=settings.APP_NAME,
    version="0.1.0",
    description="OfflineAssistant 后端 API",
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(writing.router, prefix=settings.API_V1_PREFIX)
app.include_router(quiz.router, prefix=settings.API_V1_PREFIX)
app.include_router(stats.router, prefix=settings.API_V1_PREFIX)


@app.get("/health", tags=["system"])
def health_check():
    """健康检查"""
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
