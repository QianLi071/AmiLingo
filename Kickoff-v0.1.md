## 交付物1：核心ER图（4张表）

**别搞复杂，先跑通MVP，4张表：**

```sql
-- 1. 用户表（复用登录）
users:
  id, username, password, email, created_at

-- 2. 学习记录表（核心流水，所有分区共用）
learning_records:
  id, user_id, zone (lang/contest/career/interest), 
  task_name, score (0-100), duration_minutes,
  created_at

-- 3. 宠物状态表（每个用户一条）
pets:
  user_id (主键), name, exp, level, 
  evolution_stage (egg/baby/adult/legend),
  last_fed_at, mood (happy/sad/neutral)

-- 4. 积分/成就表（轻量激励）
user_stats:
  user_id (主键), total_exp, total_days, 
  current_streak, badges (JSON数组)
```

**关键约定**：
- `zone`字段用枚举：`lang` / `contest` / `career` / `interest`，B/C/D各自只写自己的值。
- 宠物经验公式统一由A提供工具类：`PetUtil.calcExp(score, duration)`，所有人调用同一个。

---

## 交付物2：统一接口文档模板（3个核心接口）

**所有人必须实现的3个接口，格式完全一致：**

### 接口1：提交学习记录（核心）

```
POST /api/study/submit
请求体：
{
  "zone": "lang",           // B/C/D填自己的
  "taskName": "口语第3课",
  "score": 85,              // AI纠错返回的分数
  "duration": 15            // 分钟
}

响应体（统一格式）：
{
  "code": 200,
  "data": {
    "petExpGained": 30,        // 本次获得的经验
    "petNewLevel": 5,          // 升级后的等级
    "petEvolution": "adult",   // 进化阶段，没变就返回原值
    "badges": ["persistent"]   // 新获得的徽章（如有）
  }
}
```

### 接口2：查询宠物状态（前端轮询）

```
GET /api/pet/status
响应体：
{
  "code": 200,
  "data": {
    "name": "小火龙",
    "level": 5,
    "exp": 320,
    "expToNext": 100,
    "evolution": "adult",
    "mood": "happy"
  }
}
```

### 接口3：各功能区首页数据（各自定义）

```
GET /api/lang/dashboard   → B实现
GET /api/contest/dashboard → C实现
GET /api/career/dashboard  → D实现
GET /api/interest/dashboard → D实现（或E兼）

// 各自返回自己区需要的推荐内容、进度等
```

---

## 交付物3：代码包结构（强制统一）

```
com.amilingo.platform
├── common              // A负责，所有人共用
│   ├── config          // 统一配置
│   ├── util            // PetUtil, AiUtil, AuthUtil
│   └── dto             // 统一返回Result<T>
├── module
│   ├── user            // A负责登录注册
│   ├── pet             // A负责宠物核心逻辑
│   ├── lang            // B负责，独立分包
│   ├── contest         // C负责
│   ├── career          // D负责
│   └── interest        // D负责（或另找人）
└── infra
    ├── ai              // A封装AI调用（Mock→真实切换）
    └── audit           // A封装评论审核
```

**铁律**：
- B/C/D **只能改**自己 `module` 下的代码，不准动 `common` 和 `infra`。
- 如需新增通用方法，找A评估后由A统一加。

---

## Git分支策略（防冲突）

```
main（生产分支，不可直接push）
  ↑
dev（开发主分支）
  ↑
feature/zone-lang    ← B开发
feature/zone-contest ← C开发
feature/zone-career  ← D开发
feature/zone-interest← D或E
feature/frontend     ← E开发
```

---

## 补充接口文档：论文 / 专利 / 科研项目台账模块

> 以下三个模块为学术成果台账，独立于宠物成长主流程，供用户记录和管理个人学术经历。
> 响应体统一使用 `Result<T>` 包装，格式与上方一致：`{ "code": 200, "message": "ok", "data": ... }`

---

### 论文台账 `/api/paper`

#### 查询用户所有论文
```
GET /api/paper?userId={userId}
响应体：
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "title": "论文标题",
      "authorOrder": 1,
      "journal": "期刊/会议名称",
      "level": "SCI Q1",          // SCI Q1 / SCI Q2 / EI / CCF-A / CCF-B / 核心期刊 / 普通期刊
      "status": "PUBLISHED",      // WRITING / SUBMITTED / ACCEPTED / PUBLISHED
      "submissionDate": "2024-03-01",
      "acceptanceDate": "2024-05-10",
      "publicationDate": "2024-08-01",
      "doi": "10.1000/xyz123",
      "attachments": ["manuscript_v3.pdf", "review_comments.docx"]
    }
  ]
}
```

#### 查询单条论文
```
GET /api/paper/{id}
响应体：同上单条对象，不存在时 code=404
```

#### 新增论文
```
POST /api/paper
请求体：
{
  "userId": 1,
  "title": "论文标题",
  "authorOrder": 1,
  "journal": "期刊名称",
  "level": "EI",
  "status": "WRITING",
  "submissionDate": null,
  "acceptanceDate": null,
  "publicationDate": null,
  "doi": null,
  "attachments": []
}
响应体：新增后的完整论文对象（含 id）
```

#### 更新论文
```
PUT /api/paper/{id}
请求体：同新增，传入需要更新的字段
响应体：更新后的完整论文对象，不存在时 code=404
```

#### 删除论文
```
DELETE /api/paper/{id}
响应体：{ "code": 200, "data": null }，不存在时 code=404
```

---

### 专利台账 `/api/patent`

#### 查询用户所有专利
```
GET /api/patent?userId={userId}
响应体：
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "name": "专利名称",
      "type": "INVENTION",         // INVENTION=发明 / UTILITY=实用新型 / DESIGN=外观设计
      "applicationNumber": "CN202410001234.1",
      "grantNumber": null,
      "inventorOrder": 1,
      "status": "APPLIED",         // APPLIED / SUBSTANTIVE_EXAM / GRANTED / REJECTED
      "applicationDate": "2024-01-15",
      "grantDate": null,
      "attachments": ["patent_draft.pdf"]
    }
  ]
}
```

#### 查询单条专利
```
GET /api/patent/{id}
响应体：同上单条对象，不存在时 code=404
```

#### 新增专利
```
POST /api/patent
请求体：
{
  "userId": 1,
  "name": "专利名称",
  "type": "INVENTION",
  "applicationNumber": "CN202410001234.1",
  "grantNumber": null,
  "inventorOrder": 1,
  "status": "APPLIED",
  "applicationDate": "2024-01-15",
  "grantDate": null,
  "attachments": []
}
响应体：新增后的完整专利对象（含 id）
```

#### 更新专利
```
PUT /api/patent/{id}
请求体：同新增，传入需要更新的字段
响应体：更新后的完整专利对象，不存在时 code=404
```

#### 删除专利
```
DELETE /api/patent/{id}
响应体：{ "code": 200, "data": null }，不存在时 code=404
```

---

### 科研项目台账 `/api/research`

#### 查询用户所有科研项目
```
GET /api/research?userId={userId}
响应体：
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "projectName": "项目名称",
      "role": "核心成员",
      "supervisor": "指导教师姓名",
      "laboratory": "所属实验室",
      "startDate": "2023-09-01",
      "endDate": null,
      "status": "ONGOING",         // ONGOING / COMPLETED / SUSPENDED
      "outcome": "成果描述",
      "attachments": ["report.pdf"]
    }
  ]
}
```

#### 查询单条科研项目
```
GET /api/research/{id}
响应体：同上单条对象，不存在时 code=404
```

#### 新增科研项目
```
POST /api/research
请求体：
{
  "userId": 1,
  "projectName": "项目名称",
  "role": "核心成员",
  "supervisor": "指导教师姓名",
  "laboratory": "所属实验室",
  "startDate": "2023-09-01",
  "endDate": null,
  "status": "ONGOING",
  "outcome": null,
  "attachments": []
}
响应体：新增后的完整科研项目对象（含 id）
```

#### 更新科研项目
```
PUT /api/research/{id}
请求体：同新增，传入需要更新的字段
响应体：更新后的完整科研项目对象，不存在时 code=404
```

#### 删除科研项目
```
DELETE /api/research/{id}
响应体：{ "code": 200, "data": null }，不存在时 code=404
```