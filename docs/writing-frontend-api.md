# 写作评分接口文档（Python OfflineAssistant · 供前端调用）

> 服务：OfflineAssistant FastAPI（独立于 Java 主后端，本地推理）
> Base URL：`http://localhost:8000/api/v1`
> CORS 已放行：`http://localhost:8080`、`http://127.0.0.1:8080`、`http://localhost:5173`、`http://localhost:3000`、`null`
> 架构与降级约定见 [iteration-development-roadmap.md](iteration-development-roadmap.md) 第十四至十八章。

**调用前必读：**

1. **耗时长**：本地模型推理，文本评分约 1-3 分钟，图片识别评分约 2-5 分钟。请求期间必须展示 loading、禁止重复提交；图片请求前端设置 10 分钟超时（AbortController）。
2. **必须降级**：网络不通、非 2xx、`success=false` 或超时时，前端必须回退本地 mock 评分并提示「（离线模拟）」，**禁止白屏**。
3. **统一判断方式**：HTTP 状态靠 `resp.ok`；业务状态靠 `json.success`；错误响应不是统一包装体（见文末错误码），不要按成功结构读 `data`。
4. **统一包装**：成功响应均为 `{ "success": true, "message": "ok", "data": { ... } }`。
5. **base64 不带前缀**：图片接口传纯 base64 字符串，不要带 `data:image/...;base64,`。

---

# 提交文本作文评分

POST http://localhost:8000/api/v1/writing/grade
Content-Type: application/json

### 请求参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| content | string | 是 | — | 作文正文 |
| topic | string | 是 | — | 作文题目 |
| level | string | 否 | `"四级"` | 级别：四级 / 六级 / Academic / General |
| exam_type | string | 否 | `"CET4"` | `CET4` / `CET6` / `IELTS_A` / `IELTS_G`（非法值返回 422） |

词数规则（仅用于提示，不拦截提交）：CET4 120-180 词，CET6 150-200 词，IELTS_A/G 250-300 词。

### 示例请求（IELTS Academic）

```json
{
  "content": "In recent years, online education has become increasingly popular. ...",
  "topic": "Some people believe that online learning is more effective than traditional classroom learning. Discuss both views and give your own opinion.",
  "level": "Academic",
  "exam_type": "IELTS_A"
}
```

### 示例返回（200）

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "content": "In recent years, online education has become increasingly popular. ...",
    "topic": "Some people believe that online learning is more effective than traditional classroom learning. Discuss both views and give your own opinion.",
    "level": "Academic",
    "exam_type": "IELTS_A",
    "word_count": 268,
    "basic_issues": [],

    "total_score": 6.5,
    "overall": 6.5,
    "band": "6.5",
    "band_comment": "Competent user: generally effective command despite some inaccuracies.",
    "total_106": 46.2,
    "total_score_percent": 43.3,

    "scores": { "TR": 6.5, "CC": 6.0, "LR": 6.5, "GRA": 6.5 },
    "dimensions": { "TR": 6.5, "CC": 6.0, "LR": 6.5, "GRA": 6.5 },
    "dimension_labels": {
      "TR": "任务回应",
      "CC": "连贯衔接",
      "LR": "词汇资源",
      "GRA": "语法多样与准确"
    },
    "dimension_comments": {
      "TR": "观点明确，双边讨论完整，结论回应题目。",
      "CC": "段落组织合理，个别过渡略机械。",
      "LR": "词汇较丰富，搭配基本准确。",
      "GRA": "句式多样，存在少量冠词与时态错误。"
    },
    "overall_comment": "整体表现接近 6.5 分，论证充分、结构清晰；注意减少语法小错并丰富衔接手段。",
    "feedback": "整体表现接近 6.5 分，论证充分、结构清晰；注意减少语法小错并丰富衔接手段。",
    "next_actions": [
      "TR：每段首句写明分论点",
      "CC：积累三组让步衔接词组",
      "GRA：自查主谓一致与冠词"
    ],

    "sentences": [
      {
        "original": "Online learning is more better than classroom.",
        "suggestion": "Online learning can be more effective than classroom instruction.",
        "reason": "比较级重复、缺名词化收尾"
      }
    ],
    "summary": "共发现 6 处可优化表达，集中在比较级与介词搭配。",

    "similar_essays": [
      {
        "title": "Online education band 7 sample",
        "snippet": "The debate over whether digital classrooms outperform traditional ones ...",
        "score": 0.82
      }
    ],
    "generated_at": "2026-09-20T21:14:33.512748"
  }
}
```

> 说明：示例数值仅演示结构，以模型实际返回为准。CET4/CET6 的 `scores`/`dimensions` 键为 8 维（content/structure/logic/grammar/vocabulary/sentence_variety/naturalness/template_trace），分制 0-15。

### data 字段速查（共 23 个必填字段）

| 分组 | 字段 | 备注 |
|---|---|---|
| 基本信息 | content、topic、level、exam_type、word_count、basic_issues | basic_issues 为 `[{type,message,suggestion?}]`，可为空数组 |
| 总分 | total_score、overall、band、band_comment、total_106、total_score_percent | **total_score 与 overall 同值**（IELTS 0-9 / CET 0-15） |
| 诊断 | scores、dimensions、dimension_labels、dimension_comments、overall_comment、feedback、next_actions | scores 与 dimensions 同值；overall_comment 与 feedback 同值；next_actions 固定 3 条、≤20 中文字/条，可能为空数组需兜底 |
| 逐句 | sentences、summary | 模型异常时为空数组 / 提示文案 |
| 范文 | similar_essays | `[{title,snippet,score}]`，向量服务不可用时为空数组 |
| 元信息 | generated_at | ISO 时间字符串 |

---

# 提交图片作文评分（多模态：识别 + 评分）

POST http://localhost:8000/api/v1/writing/grade-image
Content-Type: application/json

需 8081 对话服务以 `--mmproj` 启动（`backend\scripts\start_llama.bat` 检测到 mmproj 文件会自动挂载）。**模型单次推理同时完成识别和评分**，耗时 2-5 分钟，前端超时上限设 10 分钟。

### 请求参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| image_base64 | string | 是 | — | 作文照片 base64，**不含** `data:image/...;base64,` 前缀 |
| topic | string | 是 | — | 作文题目 |
| level | string | 否 | `"Academic"` | 同 /grade |
| exam_type | string | 否 | `"IELTS_A"` | 同 /grade |
| image_type | string | 否 | `"jpeg"` | `jpeg` / `png` / `webp`（前端从 file.type 转换，jpg 归一为 jpeg） |

### 示例请求

```json
{
  "image_base64": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBD...（纯 base64，省略）",
  "topic": "The charts below show changes in energy production in a country since 2000.",
  "level": "Academic",
  "exam_type": "IELTS_A",
  "image_type": "jpeg"
}
```

### 示例返回（200）

结构与 `/grade` **完全一致**，差异仅三处：

1. `data.content` 为模型识别出的作文原文（前端展示在「AI 识别出的原文（请核对）」折叠区，供用户核对）；
2. `word_count`、`basic_issues` 基于识别文本重新计算；
3. `sentences` 为 `[]`、`summary` 为 `""`、`similar_essays` 为 `[]`（图片模式不做第二次长耗时推理）。

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "content": "The two charts illustrate how energy production changed ...",
    "topic": "The charts below show changes in energy production in a country since 2000.",
    "level": "Academic",
    "exam_type": "IELTS_A",
    "word_count": 174,
    "basic_issues": [
      { "type": "word_count", "message": "词数不足（当前174词，要求250-300词）" }
    ],
    "total_score": 5.5,
    "overall": 5.5,
    "band": "5.5",
    "band_comment": "",
    "total_106": 39.1,
    "total_score_percent": 36.7,
    "scores": { "TR": 5.0, "CC": 5.5, "LR": 6.0, "GRA": 5.5 },
    "dimensions": { "TR": 5.0, "CC": 5.5, "LR": 6.0, "GRA": 5.5 },
    "dimension_labels": { "TR": "任务回应", "CC": "连贯衔接", "LR": "词汇资源", "GRA": "语法多样与准确" },
    "dimension_comments": {},
    "overall_comment": "识别文本未达 250 词，任务回应不完整，影响 TR 得分。",
    "feedback": "识别文本未达 250 词，任务回应不完整，影响 TR 得分。",
    "next_actions": [],
    "sentences": [],
    "summary": "",
    "similar_essays": [],
    "generated_at": "2026-09-20T21:20:07.118204"
  }
}
```

---

# AI 服务健康检查

GET http://localhost:8000/api/v1/writing/health

无需鉴权、无请求体。检查两个 llama-server（对话 8081 / 向量 8082）是否可达。

### 示例返回（200）

```json
{
  "llm": {
    "url": "http://localhost:8081",
    "status": "ok"
  },
  "embedding": {
    "url": "http://localhost:8082",
    "status": "unreachable"
  }
}
```

另：FastAPI 进程级健康检查为 `GET http://localhost:8000/health` → `{"status": "ok"}`。

---

# 错误响应

HTTP 错误体为 FastAPI 格式 `{ "detail": "..." }`，**不是** `{success,message,data}`：

### 503：llama-server 未启动 / 不可达 / 未启用多模态

```json
{
  "detail": "AI 服务不可用: Connection refused at http://localhost:8081"
}
```

前端处理：同网络失败，降级 mock + 「（离线模拟）」标签。

### 500：批改过程异常（如模型输出无法解析）

```json
{
  "detail": "图片批改失败: 模型未返回识别出的作文原文"
}
```

### 422：请求体校验失败（如 exam_type 非法）

```json
{
  "detail": [
    {
      "type": "literal_error",
      "loc": ["body", "exam_type"],
      "msg": "Input should be 'CET4', 'CET6', 'IELTS_A' or 'IELTS_G'"
    }
  ]
}
```

---

# 前端接入参考（js/api.js）

IELTS Demo 已封装 `window.AmiAPI`（`frontend/IELTS/js/api.js`，须先于 app.js 引入），字段归一规则：

| 后端 data 字段 | 前端返回字段 |
|---|---|
| total_score | `overall`（number）、`overallText`（保留 1 位小数） |
| scores.TR / CC / LR / GRA | `breakdown.TR/CC/LR/GRA` |
| band / band_comment | `band` / `bandComment` |
| next_actions / dimension_comments / sentences | `nextActions` / `dimensionComments` / `sentences`（空值兜底） |
| content（图片接口） | `recognizedText` |
| —（客户端追加） | `source: 'ai'`（mock 结果为 `_source: 'mock'`） |

调用方式：`AmiAPI.callWritingGrade({ content, topic, examType, level })`、`AmiAPI.callWritingGradeImage({ imageBase64, topic, examType, level, imageType })`。修改 api.js / app.js 后记得递增 index.html 中的 `?v=` 版本号以防浏览器缓存。
