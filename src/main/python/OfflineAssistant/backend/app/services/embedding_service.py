"""文本向量 / 嵌入服务"""
"""范文检索服务：基于向量相似度的范文推荐。

职责：
1. 从 data/essays/*.json 加载范文库
2. 批量向量化后缓存到 data/vector_store/（首次计算，之后直接加载）
3. 提供 find_similar(text, top_k) 接口
"""
import hashlib
import json
import logging
from pathlib import Path
from typing import Any

from backend.app.core.config import BASE_DIR, settings
from backend.app.services.ai_client import embed_client

logger = logging.getLogger(__name__)


class EmbeddingService:
    """范文向量检索。

    使用懒加载：首次调用 find_similar 时才加载范文 + 计算/读取缓存，
    避免应用启动时就依赖 embedding server 已启动。
    """

    def __init__(self) -> None:
        self.essay_dir: Path = BASE_DIR / "backend" / "data" / "essays"
        self.cache_dir: Path = BASE_DIR / "backend" / "data" / "vector_store"
        self._essays: list[dict[str, Any]] = []
        self._vectors: list[list[float]] = []
        self._loaded = False

    # ------------------------------------------------------------------
    # 内部：加载与缓存
    # ------------------------------------------------------------------
    def _load_essays(self) -> None:
        if self._essays:
            return
        if not self.essay_dir.exists():
            logger.warning(f"范文目录不存在: {self.essay_dir}")
            return
        for json_file in sorted(self.essay_dir.glob("*.json")):
            try:
                with open(json_file, encoding="utf-8") as f:
                    data = json.load(f)
                if isinstance(data, list):
                    self._essays.extend(data)
                    logger.info(f"从 {json_file.name} 加载 {len(data)} 篇范文")
            except Exception as exc:
                logger.error(f"加载范文失败 {json_file}: {exc}")

    def _cache_key(self) -> str:
        """范文内容指纹；内容变化时自动失效重算。"""
        content = "|".join(e.get("content", "") for e in self._essays)
        return hashlib.md5(content.encode("utf-8")).hexdigest()[:12]

    def _ensure_loaded(self) -> None:
        if self._loaded:
            return
        self._load_essays()
        if not self._essays:
            self._loaded = True
            return

        # 确保向量缓存目录存在（防御性：__init__ 中 mkdir 可能因父目录缺失而未生效）
        self.cache_dir.mkdir(parents=True, exist_ok=True)

        cache_file = self.cache_dir / f"fanwen_{self._cache_key()}.json"
        if cache_file.exists():
            with open(cache_file, encoding="utf-8") as f:
                self._vectors = json.load(f)
            logger.info(f"从缓存加载向量 {len(self._vectors)} 条")
        else:
            # 清理旧指纹缓存
            for old in self.cache_dir.glob("fanwen_*.json"):
                try:
                    old.unlink()
                except OSError:
                    pass
            texts = [e["content"] for e in self._essays]
            self._vectors = embed_client.embed(texts)
            with open(cache_file, "w", encoding="utf-8") as f:
                json.dump(self._vectors, f)
            logger.info(f"向量化完成并缓存 {len(self._vectors)} 条 → {cache_file.name}")

        self._loaded = True

    # ------------------------------------------------------------------
    # 对外接口
    # ------------------------------------------------------------------
    # exam_type → 范文 level 映射（用于按考试类型过滤范文库）
    EXAM_TO_LEVEL: dict[str, str] = {
        "CET4": "四级",
        "CET6": "六级",
        "IELTS_A": "Academic",
        "IELTS_G": "General",
    }

    def find_similar(
        self,
        text: str,
        exam_type: str = "CET4",
        top_k: int = 3,
    ) -> list[dict[str, Any]]:
        """返回与 text 最相似的 top_k 篇范文。

        Args:
            text: 待检索文本（当前学生作文）。
            exam_type: 考试类型，用于过滤匹配 level 的范文。
            top_k: 返回条数。

        Returns:
            每项包含 title / topic / level / content / score / similarity。
            服务异常或无范文时返回空列表（不抛异常，避免阻塞主流程）。
        """
        if not text or not text.strip():
            return []
        try:
            self._ensure_loaded()
        except Exception as exc:
            logger.error(f"范文向量加载失败: {exc}")
            return []

        if not self._essays or not self._vectors:
            return []

        # 按考试类型过滤范文 level
        target_level = self.EXAM_TO_LEVEL.get(exam_type)

        try:
            query_vec = embed_client.embed(text)[0]
        except Exception as exc:
            logger.error(f"当前作文向量化失败: {exc}")
            return []

        scored: list[dict[str, Any]] = []
        for essay, vec in zip(self._essays, self._vectors):
            # 只检索匹配考试类型的范文；level 不匹配则跳过
            if target_level and essay.get("level") != target_level:
                continue
            sim = embed_client.find_similar(query_vec, vec)
            scored.append({
                "id": essay.get("id", ""),
                "title": essay.get("title", ""),
                "topic": essay.get("topic", ""),
                "level": essay.get("level", ""),
                "content": essay.get("content", ""),
                "score": essay.get("score"),
                "similarity": round(sim, 4),
            })
        scored.sort(key=lambda x: x["similarity"], reverse=True)
        return scored[:top_k]

    def reload(self) -> None:
        """清空内存缓存，下次调用重新加载（改完范文 JSON 后可手动触发）。"""
        self._essays = []
        self._vectors = []
        self._loaded = False


embedding_service = EmbeddingService()