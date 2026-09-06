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