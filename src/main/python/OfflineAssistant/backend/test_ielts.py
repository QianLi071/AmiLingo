"""验证 IELTS 评分端点返回结构。运行：python -m backend.test_ielts"""
import json

import httpx

BASE = "http://localhost:8000"

body = {
    "content": (
        "Nowadays, smartphones have become an indispensable part of our daily lives. "
        "While they bring considerable convenience, they also raise concerns about their "
        "negative impacts. This essay will examine both sides of the issue. "
        "On the one hand, smartphones offer remarkable benefits. First and foremost, they "
        "enable instant communication with friends and family regardless of distance. "
        "Moreover, users can access vast amounts of information within seconds, which "
        "greatly improves learning efficiency. On the other hand, the excessive use of "
        "smartphones may lead to serious problems. Many people become addicted to social "
        "media and games, which harms their physical health. In conclusion, smartphones "
        "are a double-edged sword. The key lies not in avoiding them but in using them "
        "wisely and moderately."
    ),
    "topic": "Some people think smartphones have made our lives better, while others disagree. Discuss both views and give your own opinion.",
    "level": "Academic",
    "exam_type": "IELTS_A",
}

r = httpx.post(f"{BASE}/api/v1/writing/grade", json=body, timeout=300)
result = r.json()

print("外层字段:", list(result.keys()))
if "data" in result:
    d = result["data"]
    print("内层字段数:", len(d))
    print("total_score:", d.get("total_score"))
    print("band:", d.get("band"))
    print("scores (关键):", json.dumps(d.get("scores", {}), ensure_ascii=False))
    print("dimension_comments:", json.dumps(d.get("dimension_comments", {}), ensure_ascii=False))
    print("sentences 数量:", len(d.get("sentences", [])))
    print("similar_essays 数量:", len(d.get("similar_essays", [])))