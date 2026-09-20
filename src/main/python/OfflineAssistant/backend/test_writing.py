"""写作批改测试脚本（llama.cpp llama-server，Gemma 对话模型 @ 8081）

运行方式（在项目根目录）:
    python -m backend.test_writing
"""
import json

from backend.app.services.ai_client import ai_client
from backend.app.services.writing_service import writing_service

# 测试作文
test_essay = """
Nowadays, with the development of technology, more and more people use smartphones.
Smartphones are very useful. We can use them to communicate with friends, search for information, and play games.
I think smartphones have many advantages. First, they are convenient. Second, they can help us save time.
Third, we can learn new things from them. However, smartphones also have disadvantages.
If we use them too much, we may become addicted. So we should use smartphones wisely.
"""

test_topic = "The Advantages and Disadvantages of Smartphones"

print(f"当前批改模型: {ai_client.model}（llama-server 地址: {ai_client.base_url}）")
result = writing_service.grade_essay(test_essay, test_topic, "四级")
print(json.dumps(result, ensure_ascii=False, indent=2))
