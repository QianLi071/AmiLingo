# OfflineAssistant — 本地化 AI 写作批改服务

## 项目简介
基于 llama.cpp 本地推理的雅思写作批改系统。前端提交作文 →
后端调 Gemma-4-E2B（多模态评分）+ Qwen3-Embedding（范文检索）
→ 返回雅思四维评分 + 逐句批改 + 改进建议。全程无云端 API。

## 核心特性
- 支持文字输入和图片上传（多模态 OCR + 评分）
- 雅思官方四维评分（TR/CC/LR/GRA）
- 后端不可用时自动降级到本地 mock
- 相似范文向量检索

## 技术栈
- 后端：Python 3.13 + FastAPI + Pydantic v2
- 模型：llama.cpp（llama-server）+ GGUF
  - gemma-4-E2B_q4_0-it.gguf（8081，对话 + 多模态）
  - gemma-4-E2B-it-mmproj.gguf（视觉投影）
  - qwen3-embedding-0.6b（8082，向量）
- 前端：原生 HTML/CSS/JS

## 目录结构
（用 ls 扫描生成 2 层树，忽略 __pycache__ / llama.cpp / .venv）

## 环境准备
1. Python 3.13
2. pip install -r requirements.txt
3. 下载 GGUF 模型到 models/
4. 编译 llama.cpp（简述：VS Build Tools + cmake -B build）

## 启动（4 个窗口）
（依次列出 start_llama.bat / start_embed.bat / python main.py / http.server 8080）

## 测试
- python -m backend.test_llama（模型连通）
- python -m backend.test_embed（向量连通）
- python -m backend.test_ielts（IELTS 评分）
- python -m backend.test_vision 图片.png（多模态）
- python test_api.py（HTTP 接口）

## API 接口
- POST /api/v1/writing/grade — 文字作文批改
- POST /api/v1/writing/grade-image — 图片作文批改（多模态）
- GET /api/v1/writing/health — 服务健康检查

## 已知限制
- 单次评分约 3-5 分钟（CPU 推理）
- 口语/听力/阅读模块前端为 mock
- 图片识别仅支持印刷体，手写效果差
