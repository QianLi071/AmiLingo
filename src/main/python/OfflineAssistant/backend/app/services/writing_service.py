"""作文批改服务 - 核心业务逻辑"""
import json
import logging
from datetime import datetime
from typing import Any, Dict, List

from backend.app.services.ai_client import ai_client as llama_client
from backend.app.services.embedding_service import embedding_service
from backend.app.prompts.registry import get_grade_prompt, get_suggestion_prompt

logger = logging.getLogger(__name__)

# 各考试类型的维度中文标签（用于统一 schema 的 dimension_labels 字段）
DIMENSION_LABELS: dict[str, dict[str, str]] = {
    "CET4": {
        "content": "内容切题", "structure": "结构清晰", "logic": "逻辑连贯",
        "grammar": "语法准确", "vocabulary": "词汇丰富", "sentence_variety": "句式多样",
        "naturalness": "表达自然", "template_trace": "原创程度",
    },
    "CET6": {
        "content": "内容切题", "structure": "结构清晰", "logic": "逻辑连贯",
        "grammar": "语法准确", "vocabulary": "词汇丰富", "sentence_variety": "句式多样",
        "naturalness": "表达自然", "template_trace": "原创程度",
    },
    "IELTS_A": {
        "TR": "任务回应", "CC": "连贯衔接", "LR": "词汇资源", "GRA": "语法多样与准确",
    },
    "IELTS_G": {
        "TR": "任务回应", "CC": "连贯衔接", "LR": "词汇资源", "GRA": "语法多样与准确",
    },
}


class WritingService:
    """作文批改服务类"""

    def grade_essay(
        self,
        content: str,
        topic: str,
        level: str = "四级",
        exam_type: str = "CET4",
    ) -> Dict[str, Any]:
        """批改一篇作文。

        Args:
            content: 作文正文。
            topic: 作文题目。
            level: 考试级别（四级/六级），用于词数规则和 Prompt。
            exam_type: 考试类型（CET4 / CET6 / IELTS_A / IELTS_G），用于选 Prompt。

        Returns:
            批改报告。
        """
        basic_check = self._basic_check(content, exam_type=exam_type)
        grade_result = self._get_grade(content, topic, level, exam_type)
        suggestions = self._get_suggestions(content, topic, level, exam_type)
        similar_essays = self._find_similar_essays(content, exam_type=exam_type)

        return {
            "content": content,
            "topic": topic,
            "level": level,
            "exam_type": exam_type,
            "word_count": basic_check["word_count"],
            "basic_issues": basic_check["issues"],

            # ---- 总分 ----
            "total_score": grade_result.get("total_score", 0),
            "overall": grade_result.get("total_score", 0),  # 统一字段：总分
            "band": grade_result.get("band", ""),
            "band_comment": grade_result.get("band_comment", ""),
            "total_106": grade_result.get("total_106", 0),
            "total_score_percent": grade_result.get("total_score_percent", 0),

            # ---- 诊断 ----
            "scores": grade_result.get("scores", {}),
            "dimensions": grade_result.get("scores", {}),  # 统一字段：各维度分数
            "dimension_labels": grade_result.get("dimension_labels", {}),  # 统一字段：维度中文标签
            "dimension_comments": grade_result.get("dimension_comments", {}),
            "overall_comment": grade_result.get("overall_comment", ""),
            "feedback": grade_result.get("overall_comment", ""),  # 统一字段：总评
            "next_actions": grade_result.get("next_actions", []),  # 统一字段：下一步建议

            # ---- 逐句批改 ----
            "sentences": suggestions.get("sentences", []),
            "summary": suggestions.get("summary", ""),

            "similar_essays": similar_essays,
            "generated_at": datetime.now().isoformat(),
        }

    # ------------------------------------------------------------------
    # 图片作文批改（多模态）
    # ------------------------------------------------------------------
    def grade_essay_image(
        self,
        image_data_url: str,
        topic: str,
        level: str = "Academic",
        exam_type: str = "IELTS_A",
    ) -> Dict[str, Any]:
        """识别图片中的英文作文并批改（需 llama-server 以 --mmproj 启动）。

        单次多模态调用：模型先 OCR 识别图片中的作文，再按对应考试标准评分。
        输出 JSON 在评分 schema 基础上额外包含 recognized_essay 字段（识别原文）。
        图片模式暂不做逐句批改与范文检索（避免两次长耗时推理），
        sentences / summary / similar_essays 返回空默认值，结构与 /grade 一致。

        Args:
            image_data_url: 图片 data URL，如 data:image/jpeg;base64,xxx。
            topic: 作文题目。
            level: 考试级别（Academic/General/四级/六级）。
            exam_type: 考试类型，用于选 Prompt。

        Returns:
            批改报告，字段与 grade_essay 完全一致；content 为识别出的作文原文。

        Raises:
            RuntimeError: llama-server 不可达或未启用 mmproj。
            ValueError: 模型输出无法解析为 JSON。
        """
        base_prompt = get_grade_prompt(exam_type).format(
            level=level, topic=topic, essay="（以图片中识别出的英文作文为准）"
        )
        text_part = (
            "请先准确识别图片中的英文作文（逐字转录，不要改写、不要翻译），"
            "再对识别出的作文按以下要求进行评分。\n"
            "在原有字段之外，输出 JSON 必须额外包含 \"recognized_essay\" 字段"
            "（字符串，识别出的作文完整原文）。\n\n" + base_prompt
        )
        messages = [{
            "role": "user",
            "content": [
                {"type": "text", "text": text_part},
                {"type": "image_url", "image_url": {"url": image_data_url}},
            ],
        }]

        raw = llama_client.chat(messages=messages, format="json", max_tokens=3072)
        data = self._extract_json(raw)

        recognized = str(data.get("recognized_essay", "")).strip()
        if not recognized:
            raise ValueError(f"模型未返回识别出的作文原文，原始输出: {raw[:200]}")

        scores = data.get("scores", {})
        total_score = round(float(data.get("total_score") or 0), 1)
        if total_score == 0 and scores:
            total_score = round(sum(scores.values()) / len(scores), 1)
        band = data.get("band") or ""
        band_comment = data.get("band_comment") or ""
        basic_check = self._basic_check(recognized, exam_type=exam_type)

        return {
            "content": recognized,
            "topic": topic,
            "level": level,
            "exam_type": exam_type,
            "word_count": basic_check["word_count"],
            "basic_issues": basic_check["issues"],

            # ---- 总分 ----
            "total_score": total_score,
            "overall": total_score,
            "band": band,
            "band_comment": band_comment,
            "total_106": round(total_score / 15 * 106.5, 1),
            "total_score_percent": round(total_score / 15 * 100, 1),

            # ---- 诊断 ----
            "scores": scores,
            "dimensions": scores,
            "dimension_labels": DIMENSION_LABELS.get(exam_type, {}),
            "dimension_comments": data.get("dimension_comments", {}),
            "overall_comment": data.get("overall_comment", ""),
            "feedback": data.get("overall_comment", ""),
            "next_actions": data.get("next_actions", []),

            # ---- 逐句批改（图片模式暂不启用）----
            "sentences": [],
            "summary": "",

            "similar_essays": [],
            "generated_at": datetime.now().isoformat(),
        }

    @staticmethod
    def _extract_json(text: str) -> Dict[str, Any]:
        """从模型输出中提取 JSON 对象（清理 markdown 包裹 + 正则兜底）。"""
        content_text = text.strip()
        if content_text.startswith("```json"):
            content_text = content_text[7:]
        if content_text.startswith("```"):
            content_text = content_text[3:]
        if content_text.endswith("```"):
            content_text = content_text[:-3]
        content_text = content_text.strip()

        try:
            return json.loads(content_text)
        except json.JSONDecodeError:
            import re
            match = re.search(r"\{.*\}", content_text, re.DOTALL)
            if not match:
                raise
            return json.loads(match.group(0))

    # ------------------------------------------------------------------
    # 基础规则检查
    # ------------------------------------------------------------------
    def _basic_check(self, content: str, exam_type: str = "CET4") -> Dict[str, Any]:
        """基础规则检查（不走 AI）"""
        word_count = len(content.split())
        issues = []

        # 按考试类型给不同词数标准
        word_rules = {
            "CET4": (120, 180),
            "CET6": (150, 200),
            "IELTS_A": (250, 300),
            "IELTS_G": (250, 300),
        }
        min_w, max_w = word_rules.get(exam_type, (120, 180))
        if word_count < min_w:
            issues.append({"type": "word_count",
                           "message": f"词数不足（当前{word_count}词，要求{min_w}-{max_w}词）"})
        elif word_count > max_w:
            issues.append({"type": "word_count",
                           "message": f"词数过多（当前{word_count}词，要求{min_w}-{max_w}词）"})

        template_patterns = [
            "with the development of",
            "as far as i am concerned",
            "every coin has two sides",
        ]
        found = [p for p in template_patterns if p in content.lower()]
        if found:
            issues.append({
                "type": "template",
                "message": f"检测到模板化表达: {', '.join(found)}",
                "suggestion": "建议用更自然的表达替代模板句",
            })

        return {"word_count": word_count, "issues": issues}

    # ------------------------------------------------------------------
    # 档位判定
    # ------------------------------------------------------------------
    @staticmethod
    def _classify_band(score: float) -> tuple[str, str]:
        """按四六级官方标准判定档位。"""
        if score >= 13:
            return "13-15档", "切题，表达思想清楚，文字通顺、连贯，基本无语言错误"
        if score >= 10:
            return "10-12档", "切题，表达思想清楚，文字连贯，但有少量语言错误"
        if score >= 7:
            return "7-9档", "基本切题，有些地方表达不够清楚，文字勉强连贯"
        if score >= 4:
            return "4-6档", "基本切题，表达思想不清楚，连贯性差，有较多语言错误"
        if score >= 1:
            return "1-3档", "条理不清，思路紊乱，语言支离破碎"
        return "0分", "白卷或所写内容与题目无关"

    @staticmethod
    def _empty_grade(msg: str) -> Dict[str, Any]:
        """评分失败时的空结果。"""
        return {
            "scores": {},
            "total_score": 0,
            "band": "",
            "band_comment": "",
            "total_106": 0,
            "total_score_percent": 0,
            "dimension_labels": {},
            "dimension_comments": {},
            "overall_comment": msg,
            "next_actions": [],
        }

    # ------------------------------------------------------------------
    # 评分
    # ------------------------------------------------------------------
    def _get_grade(self, content: str, topic: str, level: str, exam_type: str) -> Dict[str, Any]:
        """调用 AI 按对应考试标准评分。"""
        prompt = get_grade_prompt(exam_type).format(level=level, topic=topic, essay=content)

        try:
            content_text = llama_client.chat(
                messages=[{"role": "user", "content": prompt}],
                format="json",
                max_tokens=2048,
            ).strip()

            # 清理 markdown 包裹
            if content_text.startswith("```json"):
                content_text = content_text[7:]
            if content_text.startswith("```"):
                content_text = content_text[3:]
            if content_text.endswith("```"):
                content_text = content_text[:-3]
            content_text = content_text.strip()

            # 尝试直接解析，失败则正则提取首个 JSON 对象
            try:
                data = json.loads(content_text)
            except json.JSONDecodeError:
                import re
                match = re.search(r"\{.*\}", content_text, re.DOTALL)
                if not match:
                    raise
                data = json.loads(match.group(0))

            scores = data.get("scores", {})
            total_score = round(float(data.get("total_score") or 0), 1)

            # 兜底：模型没给总分时用 8 维平均
            if total_score == 0 and scores:
                total_score = round(sum(scores.values()) / len(scores), 1)

            # 档位：优先用模型给的，缺失时用代码判定
            band = data.get("band") or ""
            band_comment = data.get("band_comment") or ""
            if not band:
                band, band_comment = self._classify_band(total_score)

            total_106 = round(total_score / 15 * 106.5, 1)

            return {
                "scores": scores,
                "total_score": total_score,
                "band": band,
                "band_comment": band_comment,
                "total_106": total_106,
                "total_score_percent": round(total_score / 15 * 100, 1),
                "dimension_labels": DIMENSION_LABELS.get(exam_type, {}),
                "dimension_comments": data.get("dimension_comments", {}),
                "overall_comment": data.get("overall_comment", ""),
                "next_actions": data.get("next_actions", []),
            }

        except json.JSONDecodeError as e:
            logger.error(f"JSON解析失败: {e}, 原始输出: {content_text[:200]}")
            return self._empty_grade("AI 评分解析失败，请稍后重试")
        except Exception as e:
            logger.error(f"评分调用失败: {e}")
            return self._empty_grade(f"服务异常: {e}")

    # ------------------------------------------------------------------
    # 逐句批改
    # ------------------------------------------------------------------
    def _get_suggestions(self, content: str, topic: str, level: str, exam_type: str) -> Dict[str, Any]:
        """逐句批改。"""
        prompt = get_suggestion_prompt(exam_type).format(level=level, topic=topic, essay=content)

        try:
            result = llama_client.chat(
                messages=[{"role": "user", "content": prompt}],
                format="json",
                max_tokens=2048,
            )

            content_text = result.strip()
            if content_text.startswith("```json"):
                content_text = content_text[7:]
            if content_text.startswith("```"):
                content_text = content_text[3:]
            if content_text.endswith("```"):
                content_text = content_text[:-3]
            content_text = content_text.strip()

            try:
                data = json.loads(content_text)
            except json.JSONDecodeError:
                import re
                match = re.search(r"\{.*\}", content_text, re.DOTALL)
                if not match:
                    raise
                data = json.loads(match.group(0))

            return {
                "sentences": data.get("sentences", []),
                "summary": data.get("summary", ""),
            }

        except Exception as e:
            logger.error(f"批改建议调用失败: {e}")
            return {"sentences": [], "summary": "AI 批改建议生成失败，请稍后重试"}

    # ------------------------------------------------------------------
    # 范文检索
    # ------------------------------------------------------------------
    def _find_similar_essays(self, content: str, exam_type: str = "CET4", top_k: int = 2) -> List[Dict[str, Any]]:
        """向量检索相似范文。"""
        return embedding_service.find_similar(content, exam_type=exam_type, top_k=top_k)


writing_service = WritingService()