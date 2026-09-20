"""单独验证 llama-server（Gemma）能否调通。

运行方式（必须在项目根目录，以模块方式启动）:
    python -m backend.test_llama
"""
import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]   # test_llama.py → backend/ → CET assistant/
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from backend.app.services.ai_client import ai_client as llama_client

# 1. 健康检查：不在线则直接退出，避免后面抛出大段连接错误堆栈
print(f"正在检查 llama-server（{llama_client.base_url}）...")
if not llama_client.health():
    print(
        "llama-server 不在线。请先双击运行 backend\\scripts\\start_llama.bat，\n"
        '等到控制台出现 "server is listening" / "all slots are ready" 后再执行本脚本。'
    )
    sys.exit(1)

print("llama-server 在线")

# 2. 简单对话
reply = llama_client.chat([
    {"role": "user", "content": "用中文回复：你好，请介绍一下你自己。"}
])
print("回复:", reply)

# 3. JSON 输出测试
json_reply = llama_client.chat([
    {"role": "user", "content": '请严格输出 JSON：{"msg": "ok", "score": 8}'}
], format="json")
print("JSON:", json.loads(json_reply))
