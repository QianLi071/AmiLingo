"""API 端点连通性测试。运行：python -m backend.test_api.py"""
import json

import httpx

BASE = "http://localhost:8000"

r = httpx.get(f"{BASE}/health", timeout=10)
print("[1] /health:", r.json())

r = httpx.get(f"{BASE}/api/v1/writing/health", timeout=10)
print("[2] /writing/health:", json.dumps(r.json(), ensure_ascii=False))

body = {
    "content": (
        "Nowadays smartphones are widely used. We can use them to communicate, "
        "search information, and play games. However, we may become addicted. "
        "So we should use them wisely."
    ),
    "topic": "Smartphones",
    "exam_type": "CET4",
}
r = httpx.post(f"{BASE}/api/v1/writing/grade", json=body, timeout=300)
result = r.json()

print("\n[3] /writing/grade")
print("  外层字段:", list(result.keys()))
if "data" in result:
    d = result["data"]
    print("  内层字段数:", len(d))
    print("  内层字段:", list(d.keys()))
    print("  total_score:", d.get("total_score"))
    print("  band:", d.get("band"))
    print("  total_106:", d.get("total_106"))
    print("  similar_essays 数量:", len(d.get("similar_essays", [])))