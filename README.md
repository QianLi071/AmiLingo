# AmiLingo

> AI 学习平台 —— Java 在线业务后端 + Python 本地 AI 推理 + 前端


## 架构概览

```
AmiLingo/platform/
├── src/main/java/com/amilingo/platform/   ← Java 主后端（Spring Boot 4.1.1）
│   ├── api/          REST 控制器（Auth / User / Pet）
│   ├── common/       注解、AOP 切面、配置、DTO、异常、工具类
│   ├── component/    Redis 缓存引擎、登录策略、邮件服务
│   ├── infra/ai/     AI 网关抽象（AiGateway 接口 + NoopAiGateway + PythonAiGateway）
│   └── module/       业务模块（user / pet / lang / career / contest / interest）
├── src/main/python/OfflineAssistant/      ← Python 离线 AI 助手（FastAPI + llama.cpp）
│   ├── backend/      API 路由、Prompt 管理、AI 客户端、写作批改服务
│   ├── frontend/     IELTS Demo 前端（纯静态 HTML + JS）
│   ├── models/       GGUF 模型文件（不入库）
│   └── llama.cpp/    llama.cpp 源码（不入库）
└── docs/             团队规范、迭代路线图
```

| 端 | 技术栈 | 端口 | 职责 |
|---|---|---|---|
| Java 主后端 | Spring Boot 4.1.1 + Java 21 | 8080 | 在线业务：登录注册、宠物、JWT 鉴权、Redis 缓存、AOP 限流、AI 网关 |
| Python 离线助手 | FastAPI + llama.cpp (GGUF) | 8000 | 本地 AI 推理：IELTS/CET 写作评分、逐句批改、范文检索、图片识别评分 |
| IELTS 前端 Demo | 原生 HTML + JS（无构建工具） | 8080 | 写作/口语/听力/阅读练习页、onboarding 测评 |

---

## Java 后端

### 本机运行
```bash
mvn clean package -DskipTests
```
确保安装了：
* Redis 8.0.x
* MySQL
* Java 21+

运行
```bash
java -DDATABASE_PASSWORD=你的数据库密码 -DDATABASE_HOST=你的数据库主机URL -DDATABASE_USERNAME=你的数据库用户名 -DREDIS_HOST=你的Redis主机 -DREDIS_PASSWORD=你的Redis密码 -DJWT_SECRET=不少于32字符的jwt密钥 -jar target/build-0.0.1-SNAPSHOT.jar
```

### AI 网关

Java 端 `AiGateway` 接口定义了 4 个原子能力（chat / chatJson / embed / similarity），有两个实现：

| 实现 | 说明 |
|---|---|
| `NoopAiGateway` | 桩实现，抛 `UnsupportedOperationException`，Python 未启动时上下文兜底 |
| `PythonAiGateway` | `@Primary` 实现，通过 HTTP 调用 Python FastAPI 服务，支持 `exam_type` 参数路由 |

---

## Python 离线助手（OfflineAssistant）

### 本机运行

**前置条件：**
* Python 3.13+
* llama.cpp 已编译（或使用预编译 `llama-server.exe`）
* 两个 GGUF 模型：`gemma-4-E2B_q4_0-it.gguf`（对话）+ `gemma-4-E2B-it-mmproj.gguf`（多模态）+ `Qwen3-Embedding-0.6B-Q8_0.gguf`（向量）

**启动 3 个服务：**

```bash
# 以下命令均在 platform/src/main/python/OfflineAssistant 目录下执行

# 窗口 1：对话 llama-server（Gemma，127.0.0.1:8081，检测到 mmproj 时自动启用图片识别）
backend\scripts\start_llama.bat

# 窗口 2：向量 llama-server（Qwen3-Embedding，127.0.0.1:8082，范文检索用）
backend\scripts\start_embed.bat

# 窗口 3：FastAPI 网关（:8000）
pip install -r requirements.txt
python main.py
```

**启动前端 Demo（窗口 4）：**
```bash
cd frontend\IELTS
python -m http.server 8080
# 浏览器打开 http://localhost:8080
```

前置条件：Python 3.13+；`models/` 目录下放置 `gemma-4-E2B_q4_0-it.gguf`、`gemma-4-E2B-it-mmproj.gguf`（多模态）、`Qwen3-Embedding-0.6B-Q8_0.gguf`（模型文件不入库）；llama.cpp 已编译生成 `llama-server.exe`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/writing/grade` | 文本作文评分（CET4/CET6/IELTS_A/IELTS_G） |
| POST | `/api/v1/writing/grade-image` | 图片作文识别 + 评分（多模态，需 mmproj） |
| GET | `/api/v1/writing/health` | 两个 llama-server 健康检查 |

### Prompt 管理

Prompt 单一数据源放在 Python 侧（`backend/app/prompts/`）：

```
prompts/
├── registry.py          ← 按 exam_type 分发
├── writing_prompts.py   ← CET4/CET6 评分 + 逐句批改
└── ielts_prompts.py     ← IELTS TR/CC/LR/GRA 评分
```

新增考试类型只需写一个 `xxx_prompts.py` + 在 `registry.py` 注册一行。

### 统一响应结构

所有评分接口返回统一 JSON schema（前端一份代码渲染所有考试）：

```json
{
  "exam_type": "IELTS_A",
  "overall": 6.5,
  "dimensions": { "TR": 6.5, "CC": 6.0, "LR": 6.5, "GRA": 6.5 },
  "dimension_labels": { "TR": "任务回应", "CC": "连贯衔接", ... },
  "feedback": "整体表现接近 6.5 分...",
  "next_actions": ["...", "...", "..."],
  "sentences": [...]
}
```

---

## 贡献

> [!NOTE]  
> 所有人必须在开发前遵循以下规范，以避免开发中的代码冲突、重复：
> - 禁止重复实现已有的类、实体、功能、util等
> - 请勿直接push main分支，所有人必须创建自己的分支，然后推送代码到dev分支，然后pull request到main，见[git.md](docs/util/git.md)
> - 所有人在完成迭代任务后，基于[iteration-development-roadmap.md](docs/iteration-development-roadmap.md)追加对目前更改（迭代）的架构、从模块到类的开发文档，用于为其他开发人员制定严格的开发规范和理解代码。要防止其他人开发重复的代码。保持文档逻辑清晰，避免按每个类分别介绍，而是从业务链路、架构到模块再到关键的可联调类和util
> - 完成迭代任务后，根据[scrum.md](docs/util/scrum.md)图标，在表格每一栏恰当地描述个人任务完成状态。  
> - 所有人后端开发者100%基于此文档开发：[iteration-development-roadmap.md](docs/iteration-development-roadmap.md)  

## 版本：迭代 1 · 2026-09-12
| 任务             | 负责人    | 状态  | 构建  | 测试  | 后端文档（必读）                                                                      | 前端API文档（必读）                                     |
|----------------|--------|-----|-----|-----|---------------------------------------------------------------------------|-------------------------------------------------|
| 应用基础，登录，数据库，缓存 | Lotiyu | 已完成 | ✅通过 | ✅通过 | [iteration-development-roadmap.md](docs/iteration-development-roadmap.md) | [frontend-api-doc.md](docs/frontend-api-doc.md) |
| lang 语言学习模块骨架、`infra/ai` AI 网关（AiGateway / NoopAiGateway / PythonAiGateway）、AI 写作评分链路（WritingScorer）、Python 离线助手 OfflineAssistant（FastAPI + llama.cpp：文本/图片作文评分、Prompt 注册表、逐句批改、范文检索）、为 WYK300 的IELTS 前端 Demo 接入真实 AI（含离线 mock 降级） | QianLi071 | 🔄 进行中 | ✅通过 | ✅通过 | [iteration-development-roadmap.md](docs/iteration-development-roadmap.md) | [writing-frontend-api.md](docs/writing-frontend-api.md) |
| IELTS 前端 Demo（页面 / 课表 / 排程 / 测评流程） | WYK300 | ✅ 已完成 | ✅通过 | ✅通过 | — | — |
