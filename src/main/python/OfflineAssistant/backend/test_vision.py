"""验证多模态图片评分端点。运行：python -m backend.test_vision <图片路径>

前置：
1. llama-server 需以 --mmproj 启动（backend\\scripts\\start_llama.bat 已配置）
2. FastAPI 已启动（python main.py，端口 8000）
"""
import base64
import json
import sys

import httpx

BASE = "http://localhost:8000"

# 默认图片路径（命令行未传参时使用）
DEFAULT_IMAGE = r"backend\data\test_essay.jpg"


def main() -> None:
    """读取图片 -> base64 -> 调 /grade-image -> 打印识别原文和评分。"""
    image_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_IMAGE

    try:
        with open(image_path, "rb") as f:
            image_b64 = base64.b64encode(f.read()).decode("ascii")
    except FileNotFoundError:
        print(f"[错误] 图片不存在: {image_path}")
        print("用法: python -m backend.test_vision <图片路径>")
        sys.exit(1)

    print(f"[图片] {image_path}（base64 长度 {len(image_b64)}）")

    body = {
        "image_base64": image_b64,
        "topic": "Some people think smartphones have made our lives better, while others disagree. Discuss both views and give your own opinion.",
        "level": "Academic",
        "exam_type": "IELTS_A",
    }

    print("[请求] POST /api/v1/writing/grade-image（多模态推理约需 2-5 分钟，请耐心等待）...")
    r = httpx.post(f"{BASE}/api/v1/writing/grade-image", json=body, timeout=600)
    print("[状态码]", r.status_code)

    result = r.json()
    if not result.get("success"):
        print("[失败]", json.dumps(result, ensure_ascii=False, indent=2))
        sys.exit(1)

    d = result["data"]
    print("\n========== 识别出的作文原文 ==========")
    print(d.get("content", ""))
    print(f"\n[词数] {d.get('word_count')}")
    print(f"[基础检查] {json.dumps(d.get('basic_issues', []), ensure_ascii=False)}")

    print("\n========== 评分结果 ==========")
    print("total_score:", d.get("total_score"))
    print("band:", d.get("band"))
    print("scores:", json.dumps(d.get("scores", {}), ensure_ascii=False))
    print("dimension_comments:", json.dumps(d.get("dimension_comments", {}), ensure_ascii=False))
    print("overall_comment:", d.get("overall_comment"))
    print("next_actions:", json.dumps(d.get("next_actions", []), ensure_ascii=False))


if __name__ == "__main__":
    main()
