"""应用配置"""
import os
from pathlib import Path

from dotenv import load_dotenv

# 项目根目录：本文件位于 <项目根>/backend/app/core/config.py，向上 3 级
BASE_DIR: Path = Path(__file__).resolve().parents[3]

# 显式指定 .env 路径，避免依赖进程启动时的工作目录
load_dotenv(BASE_DIR / ".env")


def _get_int_env(key: str, default: int) -> int:
    """读取整数类型环境变量。

    Args:
        key: 环境变量名。
        default: 环境变量缺失或值非法（如空字符串）时的回退值。

    Returns:
        解析后的整数；解析失败时返回 default。
    """
    try:
        return int(os.getenv(key, str(default)))
    except (TypeError, ValueError):
        return default


class Settings:
    """基础配置项"""

    APP_NAME: str = "OfflineAssistant"
    API_V1_PREFIX: str = "/api/v1"
    APP_VERSION: str = "0.1.0"
    DEBUG: bool = os.getenv("DEBUG", "True").lower() == "true"

    # CORS 允许的前端来源
    CORS_ORIGINS: list[str] = [
        "http://localhost:5173",
        "http://localhost:3000",
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        "null",
    ]

    # 数据库（SQLite）：默认使用基于项目根目录的绝对路径，避免受工作目录影响
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        f"sqlite:///{(BASE_DIR / 'backend' / 'data' / 'db' / 'offline_assistant.db').as_posix()}",
    )

    # Ollama 本地大模型
    #OLLAMA_BASE_URL: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    #OLLAMA_CHAT_MODEL: str = os.getenv("OLLAMA_CHAT_MODEL", "qwen3:0.6b")
    #OLLAMA_EMBEDDING_MODEL: str = os.getenv(
    #    "OLLAMA_EMBEDDING_MODEL", "qwen3-embedding:0.6b"
    #)

    # llama-server 对话服务（Gemma，端口 8081）
    LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "http://localhost:8081")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "gemma-4-e2b")

    # llama.cpp 与模型目录（用于启动 / 校验本地 llama-server）
    MODELS_DIR: Path = BASE_DIR / "models"
    GGUF_MODEL_PATH: Path = MODELS_DIR / "gemma-4-E2B_q4_0-it.gguf"

    # llama-server 向量服务（Qwen3-Embedding，端口 8082）
    EMBEDDING_BASE_URL: str = os.getenv("EMBEDDING_BASE_URL", "http://localhost:8082")
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "qwen3-embedding")
    # 实际模型文件名（与 models/ 目录下的文件保持一致）
    EMBEDDING_GGUF_PATH: Path = MODELS_DIR / "Qwen3-Embedding-0.6B-Q8_0.gguf"

    # llama-server 可执行文件：Visual Studio 生成器产物在 Release 子目录，
    # Ninja 等单配置生成器直接在 bin 下；优先选择已存在的那个
    _LLAMA_BUILD_BIN: Path = BASE_DIR / "llama.cpp" / "llama.cpp" / "build" / "bin"
    LLAMA_SERVER_EXE: Path = next(
        (
            p
            for p in (
                _LLAMA_BUILD_BIN / "Release" / "llama-server.exe",
                _LLAMA_BUILD_BIN / "llama-server.exe",
            )
            if p.exists()
        ),
        _LLAMA_BUILD_BIN / "Release" / "llama-server.exe",
    )

    # llama-server 地址（端口和 Ollama 的 11434 区分开）
    #LLAMA_SERVER_URL: str = os.getenv("LLAMA_SERVER_URL", "http://localhost:8081")
    # llama-server 请求超时（秒）。Gemma 等小模型逐句批改生成较长，默认 300s。
    LLAMA_TIMEOUT: int = _get_int_env("LLAMA_TIMEOUT", 300)


settings = Settings()
