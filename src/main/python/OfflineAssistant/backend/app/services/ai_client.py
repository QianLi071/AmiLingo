"""llama.cpp HTTP 客户端封装

统一封装对两个 llama-server 实例的调用：
- LLM:        http://localhost:8081  (Gemma / 评分 / 润色)
- Embedding:  http://localhost:8082  (向量化 / 范文检索)
"""
import httpx
from typing import Any

from backend.app.core.config import settings


class LlamaClient:
    """单个 llama-server 实例的 HTTP 客户端。

    Attributes:
        base_url: 服务地址（如 http://localhost:8081）。
        model: 模型标识（llama-server 不校验，但接口要求必传）。
        timeout: 请求超时秒数。
    """

    def __init__(self, base_url: str, model: str = "local-model", timeout: int = 120):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout

    # ---------- 对话 ----------
    def chat(
        self,
        messages: list[dict[str, Any]],
        model: str | None = None,
        format: str | None = None,
        temperature: float = 0.7,
        max_tokens: int | None = None,
    ) -> str:
        """OpenAI 兼容的 /v1/chat/completions。

        messages 的 content 支持两种形式（原样透传给 llama-server）：
        - 纯文本：{"role": "user", "content": "文本"}
        - 多模态：{"role": "user", "content": [
              {"type": "text", "text": "..."},
              {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
          ]}
          （需 llama-server 以 --mmproj 启动才能识别图片）
        """
        body: dict[str, Any] = {
            "model": model or self.model,
            "messages": messages,
            "temperature": temperature,
            "stream": False,
        }
        if format == "json":
            body["response_format"] = {"type": "json_object"}
        if max_tokens:
            body["max_tokens"] = max_tokens

        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.post(f"{self.base_url}/v1/chat/completions", json=body)
                resp.raise_for_status()
                return resp.json()["choices"][0]["message"]["content"]
        except httpx.ConnectError as exc:
            raise RuntimeError(
                f"无法连接 llama-server（{self.base_url}）。"
                f"请先启动 backend\\scripts\\start_llama.bat 并等 all slots are ready。"
            ) from exc
        except httpx.HTTPStatusError as exc:
            raise RuntimeError(
                f"llama-server 返回 {exc.response.status_code}: {exc.response.text[:200]}"
            ) from exc

    def think(self, messages: list[dict[str, str]], **kwargs) -> dict[str, str]:
        """兼容原 Ollama 版 think() 的返回值结构。llama.cpp 无思考模式，直接返回 chat。"""
        content = self.chat(messages, **kwargs)
        return {"thinking": "", "content": content}

    # ---------- 向量 ----------
    def embed(self, texts: str | list[str], model: str | None = None) -> list[list[float]]:
        """OpenAI 兼容的 /v1/embeddings，返回向量列表（顺序与输入一致）。"""
        if isinstance(texts, str):
            texts = [texts]
        body = {"model": model or self.model, "input": texts}
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.post(f"{self.base_url}/v1/embeddings", json=body)
                resp.raise_for_status()
                return [item["embedding"] for item in resp.json()["data"]]
        except httpx.ConnectError as exc:
            raise RuntimeError(
                f"无法连接 embedding 服务（{self.base_url}）。"
                f"请先启动 backend\\scripts\\start_embed.bat。"
            ) from exc

    @staticmethod
    def find_similar(a: list[float], b: list[float]) -> float:
        """余弦相似度。"""
        if len(a) != len(b):
            raise ValueError(f"向量维度不一致: {len(a)} != {len(b)}")
        dot = sum(x * y for x, y in zip(a, b))
        na = sum(x * x for x in a) ** 0.5
        nb = sum(y * y for y in b) ** 0.5
        return 0.0 if na == 0 or nb == 0 else dot / (na * nb)

    def health(self) -> bool:
        try:
            with httpx.Client(timeout=5) as c:
                return c.get(f"{self.base_url}/v1/models").status_code == 200
        except Exception:
            return False


# 两个单例：ai_client 保留旧名字，业务代码零改动
ai_client = LlamaClient(settings.LLM_BASE_URL, settings.LLM_MODEL, timeout=settings.LLAMA_TIMEOUT)
embed_client = LlamaClient(settings.EMBEDDING_BASE_URL, settings.EMBEDDING_MODEL, timeout=settings.LLAMA_TIMEOUT)