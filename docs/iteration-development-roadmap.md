# AmiLingo Platform · 迭代 1 开发文档

> 版本：迭代 1 · 2026-09-12
> 读者：全体后端开发人员
> 目的：统一对项目架构、业务链路、共享基础设施的理解,包括JwtUtil, PetUtil, SecurityUtil和架构链路；明确**哪些能力已经封装好必须复用、哪些是禁止重复实现**的，避免各人各写一套。

---

## 一、整体架构

### 1.1 技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Spring Boot 4.1.1 / Spring Security / Spring Data JPA / Spring Data Redis |
| 数据库 | MySQL（Hibernate `ddl-auto: update`） |
| 缓存 | Redis（Lettuce 连接池） |
| 鉴权 | JJWT 0.12.6（`jjwt-api` + `jjwt-impl` + `jjwt-jackson`） |
| 工具 | Lombok、Jackson 3（`tools.jackson`） |
| JDK | 21 |

### 1.2 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│  api/          接口层（Controller）—— 仅做参数接收与响应封装   │
├──────────────────────────────────────────────────────────────┤
│  component/    业务组件层（Service / Strategy / Cache / Repo）│
├──────────────────────────────────────────────────────────────┤
│  common/       通用层（DTO / 异常 / Util / Config / 注解（包括限流 @RateLimit、认证检查 @RequireAuth））—— 全员共享 │
├──────────────────────────────────────────────────────────────┤
│  entity/       数据实体（JPA @Entity）                        │
└──────────────────────────────────────────────────────────────┘
```

**分层原则（强制）**：
- `api` 层**只负责**：接收 HTTP 请求、调用 `component` 层、封装返回体。**不得**在 Controller 中写业务逻辑、直接操作 Repository/Redis。
- `component` 层承载全部业务逻辑，向下访问 `entity`/`repository`，向上被 `api` 调用。
- `common` 层是**横切关注点**，所有层都可以依赖；但 `common` **不得反向依赖** `api`/`component`/`entity`（`JwtUtil` 引用 `User` 属历史遗留，新增代码禁止再让 `common` 依赖 `entity`）。

### 1.3 包结构总览

```
com.amilingo.platform
├── api/                 # HTTP 接口
│   ├── pet/             # Pet controller
│   └── user/            # AuthController, UserController
├── common/              # 全员共享（重点！禁止在别处重复实现）
│   ├── annotation/      # Java元注解，包括速率限制 @RateLimit、认证检查 @RequireAuth
│   ├── aspect/          # 自定义Java元注解注入器、执行器
│   ├── config/          # SecurityConfig, RedisConfig, JwtAuthenticationFilter, login/*
│   ├── dto/             # ApiResponse, UserDTO, request/*
│   ├── exceptions/      # 异常体系
│   └── util/            # JwtUtil, Snowflake
├── component/           # 业务组件
│   ├── abstracts/       # IUserService, IpetService 等业务接口
│   ├── caching/         # UserCache, EmailCodeCache
│   ├── login/           # EmailPasswordStrategy
│   ├── redis/           # AbstractCacheEngine, ICacheable, RedisService, RedisDistributedLock
│   ├── services/user/   # UserService, MailService
│   ├── BaseLoginStrategy, ILoginStrategy, LoginStrategyFactory, LoginAttemptService
├── module/              # 业务领域占位
│   ├── user/            # 用户模块
│   │   ├── entity/  
│   │   │   ├── user/             # 用户数据库实体类
│   │   │   └── achievement/      # 成就数据库实体
│   │   ├── repository/           # UserRepository
│   │   └── service/              # 用户相关服务
│   ├── pet/                      # 宠物模块
│   │   ├── entity/pet/           # 宠物数据库实体类
│   │   ├── repository/           # PetRepository
│   │   └── service/              # 宠物相关服务
│   └── career contest interest lang/ # 业务领域占位（迭代2+）
├── infra/                        # 基础设施占位（迭代2+）：ai/audit
└── GlobalExceptionHandler, PlatformApplication
```

---

## 二、核心业务链路

迭代 1 已落地三条可联调链路。以下按**请求流转顺序**描述，涉及的类在各链路中说明其职责。

### 2.1 链路一：用户认证（登录 / 注册 / 请求鉴权）

这是迭代 1 最核心的链路，贯穿 `api → component → common`。

#### 2.1.1 登录

```
POST /api/v1/auth/portal/login
        │
        ▼
AuthController.login()
        │  ① 取出 loginType + credential
        ▼
LoginStrategyFactory.getStrategy(loginType)
        │  ② 按 type 路由到具体策略（当前：EMAIL_PWD）
        ▼
BaseLoginStrategy.authenticate()          ← 模板方法，所有登录方式共用
        │  ③ LoginAttemptService.checkLockedOrThrow()  （Redis 检查是否被锁）
        │  ④ 调用子类 doAuthenticate()
        ▼
EmailPasswordStrategy.doAuthenticate()
        │  ⑤ UserService.loginViaEmailPwd(email, password)
        │     → UserRepository.findUserByEmail → BCrypt 比对
        ▼
        │  ⑥ 成功：LoginAttemptService.clearAttempts()
        │     失败：LoginAttemptService.recordFailedAttempt()
        │           · 用 RedisDistributedLock 保证计数原子性
        │           · 达到上限写入 lock key，抛 LoginFailedException(locked)
        ▼
AuthController.login()
        │  ⑦ JwtUtil.generateToken(user) 签发 JWT
        │  ⑧ 写入 HttpOnly Cookie: access_token（7天）
        ▼
返回 { success, access_token, data: UserDTO }
```

**关键设计点**：
- **策略模式**：新增登录方式（短信、微信、邮箱验证码）只需：① 新建一个类继承 `BaseLoginStrategy`；② 实现 `getType()`、`doAuthenticate()`、`extractIdentityKey()`；③ 在 `LoginStrategyConfig` 中以 `@Bean("TYPE_NAME")` 注册。**无需修改** `AuthController` 和 `LoginStrategyFactory`。
- **失败锁定**：`BaseLoginStrategy` 已内置 5 次失败 → 锁定 30 分钟的逻辑，子类通过 `needAttemptTracking()` 决定是否启用（验证码登录可覆盖为 `false`）。**不要**在策略里自己写计数逻辑。

#### 2.1.2 注册

```
POST /api/v1/auth/portal/register
        │
        ▼
AuthController.register()
        │
        ▼
UserService.register()
        │  ① Snowflake.nextId() 生成分布式 ID
        │  ② PasswordEncoder.encode() 哈希密码
        │  ③ UserRepository.save()
        ▼
返回 201 + token（注册即登录）
```

#### 2.1.3 请求鉴权（每次受保护请求）

```
Any Request → JwtAuthenticationFilter（Spring Security 链中，位于 UsernamePasswordAuthenticationFilter 之前）
        │
        │  ① extractToken()：优先 Authorization: Bearer xxx，其次 access_token cookie
        │
        ▼
JwtUtil.validateToken(token)     ← 验签 + 过期检查
        │  通过
        ▼
JwtUtil.extractUserId(token)     ← 从 payload 的 userId claim 取出用户ID
        │
        ▼
UserRepository.findUserById(userId)
        │
        ▼
构造 AuthenticatedUser（实现 UserDetails）
        │
        ▼
SecurityContextHolder.setAuthentication(...)
        │
        ▼
后续 Controller 中通过 SecurityUtil 获取当前用户
```

**重要**：`JwtAuthenticationFilter` 中捕获了所有异常并只记日志，**不会中断过滤链**。这意味着鉴权失败时请求仍会到达 Controller，因此**受保护接口必须在 Controller / Service 中主动调用 `SecurityUtil.requireAuthentication()` 或 `SecurityUtil.getCurrentUserId()`**（后者会抛 `UserNotFoundException`）。

### 2.2 链路二：用户数据读写（缓存优先）

```
Controller → UserService
        │
        ├─ 读（getUserById / getUserByEmail）
        │     ① 先查 UserCache（Redis）
        │     ② CacheMissedException → 查 UserRepository（MySQL）
        │     ③ 查到后回写 Redis，再返回
        │
        └─ 写（saveUser）
              ① 尝试 UserCache.cache() 写 Redis
              ② CacheException 时降级写 MySQL
```

**关键设计点**：
- **Cache-Aside 模式**已由 `AbstractCacheEngine` 封装，`UserCache` 只需实现 `getKeyPrefix()`、`getId()`、`deserializeCachedObject()` 三个方法。
- `UserCache` 额外维护 `email → userId` 的二级映射（`getUserByEmail` / `setUserEmailKey`），避免每次按邮箱查库。

### 2.3 链路三：邮箱验证码

```
POST /api/v1/users/send  (需登录)
        │
        ▼
UserController.sendEmailValidationCode()
        │  ① SecurityUtil.getCurrentUserId() 取当前用户
        │  ② UserService.getUserById() 取用户邮箱
        ▼
MailService.sendEmailBindingCode(email, userId)
        │  ① EmailCodeCache.cache(code) 存 Redis（key = email + userId）
        │  ② JavaMailSender 发送邮件
        ▼
返回 "验证码已发送"
```

校验验证码时调用 `MailService.validateEmailCode(user, code)`，内部 `getAndDelete` 取出并删除缓存中的验证码（一次性）。

---

## 三、模块职责与可联调清单

### 3.1 `api/` — 接口层

| Controller | 路径前缀 | 职责 |
|---|---|---|
| `AuthController` | `/api/v1/auth/portal` | 登录、注册、登出。**登录走策略工厂，注册直接调 UserService** |
| `UserController` | `/api/v1/users` | 用户列表、发送邮箱验证码。**需登录的接口必须调用 `SecurityUtil.requireAuthentication()`** |

### 3.2 `component/` — 业务组件层

| 子包/类 | 职责 | 新增同类功能时的规范 |
|---|---|---|
| `services/MailService` | 邮件发送 + 验证码缓存读写 | 邮件相关统一走这里，不要在别处直接用 JavaMailSender |
| `caching/UserCache` / `EmailCodeCache` | 继承 `AbstractCacheEngine` 的具体缓存 | 新缓存继承 `AbstractCacheEngine<T, ID>`，实现三个抽象方法 |
| `BaseLoginStrategy` / `ILoginStrategy` / `LoginStrategyFactory` | 登录策略模板 | 新登录方式继承 `BaseLoginStrategy`，在 `LoginStrategyConfig` 注册 Bean |
| `LoginAttemptService` | 登录失败计数 + 分布式锁 | **不要自己写失败计数**，复用此 Service |
| `redis/RedisService` | Redis 基础操作封装 | 所有 Redis 操作走这里，不要在业务代码中直接注入 `RedisTemplate` |
| `redis/RedisDistributedLock` | 基于 Lua 的分布式锁 | 需要分布式互斥时注入此 Bean，不要自己 `setIfAbsent` |
| `redis/AbstractCacheEngine` | 缓存模板（序列化/反序列化/回源） | 新缓存继承它 |

### 3.2.1 `module/` — 业务组件层
| 子包/类 | 职责 | 新增同类功能时的规范 |
|---|---|---|
| `module/<域>/repository/UserRepository` | JPA 数据访问 | 新实体的 Repository 放 `module/<域>/repository/`，继承 `JpaRepository` |
| `module/user/service/UserService` | 用户 CRUD + 登录校验，实现 `IUserService` | 新业务 Service 放 `module/<域>/service/`，并在 `component/abstracts/` 定义接口 |


### 3.3 `common/` — 通用层（**全员共享，禁止重复实现**）

详见第四章。

### 3.4 `module/<域>/entity/` — 实体层

| 实体               | 表                  | 关键字段                                                                                                                           |
|------------------|--------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `User`           | `users`            | `id`(Long, 雪花ID), `username`, `passwordHash`, `email`, `createdAt`                                                             |
| `LearningRecord` | `learning_records` | id(Long, 雪花ID), user_id(Long, 雪花ID), zone (lang/contest/career/interest),task_name, score (0-100), duration_minutes,created_at |
| `Pet`            | `pets`             | user_id (主键(Long, 雪花ID)), name, exp, level,evolution_stage (egg/baby/adult/legend),last_fed_at, mood (happy/sad/neutral)       |
| `UserStat`       | `user_stats`       | user_id (主键(Long, 雪花ID)), total_exp, total_days, current_streak, badges (一对多外键关联Badges实体)                                      |
| `Badge`          | `badages`          | `id`(Long, 雪花ID), `name`, `discription`, `value`                                                                               |

---

## 四、关键共享 Util & 通用元注解 & 基础设施（防重复造轮子 · 重点）

以下能力**已经封装完毕，所有开发人员必须直接注入使用，严禁自行实现同类逻辑**。

### 4.0.1 通用java元注解 ` @RateLimit` - 同IP限流注解 

**位置**：`common/annotation/RateLimit.java`  
**参数**：
* timeWindow 计数重置时间（默认60，**单位：秒**）
* maxRequests 计数重置时间内的最大访问数（默认5，**单位：次**）

**可以添加的位置**：controller 方法上  
**使用方法**：  
```java
@RateLimit(timeWindow = 60, maxRequests = 5)
@GetMapping("/info")
public ApiResponse<String> info() {
    return ApiResponse.ok("信息");
}
```

超限返回 HTTP 429 与 {"success":false,"message":"请求过于频繁，请稍后再试","data":null} 。

### 4.0.2 通用java元注解 ` @RequireAuth` - 用户登录检查注解

**位置**：`common/annotation/RequireAuth.java`  
**参数**：无  
**可以添加的位置**：controller 方法上

**使用方法**：  
在 Controller 方法上加 @RequireAuth 即可，等价于在方法体首行写 SecurityUtil.requireAuthentication() ：
```java
@RequireAuth
@GetMapping("/send")
public ApiResponse<Void> sendEmailValidationCode() {
  // 到这里一定已登录，可以直接用 SecurityUtil.getCurrentUserId()
  Long userId = SecurityUtil.getCurrentUserId();
    ...
}
```
- 自动调用 SecurityUtil.requireAuthentication() ，未登录时抛 SecurityException
- GlobalExceptionHandler 已有 SecurityException → 401 的处理，无需额外配置

### 4.1 `JwtUtil` — JWT 签发与解析

**位置**：`common/util/JwtUtil.java`

| 方法 | 用途 | 注意事项 |
|---|---|---|
| `generateToken(User)` | 签发 token，内含 `userId` claim | **统一入口**，不要自己 `Jwts.builder()` |
| `extractUserId(token)` | 从 token 取出 userId（String） | 内部已处理 Bearer 前缀和类型兼容 |
| `validateToken(token)` | 验签 + 过期检查 | 返回 Boolean，不抛异常 |

**配置**：`jwt.secret`、`jwt.expiration`（单位**毫秒**，`application.yml` 默认 86400000 = 1天）。

**禁止**：在任何地方重复编写 `Jwts.builder()...compact()` 或 `Jwts.parser()...`。

### 4.2 `SecurityUtil` — 当前登录用户获取

**位置**：`common/config/security/SecurityUtil.java`（静态方法工具类）

| 方法 | 用途 |
|---|---|
| `getCurrentUserId()` | 获取当前用户 ID，未登录抛 `UserNotFoundException` |
| `getCurrentAuthenticatedUser()` | 获取 `AuthenticatedUser`（含 userId/username/role），未登录返回 null |
| `isAuthenticated()` | 判断是否已登录 |
| `requireAuthentication()` | 未登录抛 `SecurityException`（被全局异常处理转 401） |
| `requireAdmin()` / `isAdmin()` | 管理员鉴权 |

**强制**：所有需要"当前用户"的 Controller/Service，一律调用 `SecurityUtil.getCurrentUserId()`，**不要**自己从 `SecurityContextHolder` 取。

### 4.3 `Snowflake` — 分布式 ID 生成

**位置**：`common/util/Snowflake.java`

| 方法 | 用途 |
|---|---|
| `Snowflake.nextId()` | 生成全局唯一 Long 型 ID |

**配置**：`microservice.workerid`、`microservice.dadacenterid`（注意：配置项拼写为 `dadacenterid`，不要改动）。

**强制**：所有需要主键 ID 的新实体，统一用 `Snowflake.nextId()`，**不要**用数据库自增或 UUID。

### 4.4 `PetUtil`
| 方法 | 用途           |
|---|--------------|
| `PetUtil.calcExp(score, duration)` | 计算 Long 型经验值 |

**注意**  
score （0-100）
duration单位为分钟

宠物经验公式统一由A提供工具类：PetUtil.calcExp(score, duration)，所有人调用同一个。

输入约束

- score 钳制到 [0, 100]， duration 钳制到 ≥ 0，防止异常输入
  基础经验

- score * 10 ，满分 100 → 基础 1000 点
  时长加成（递减收益）

- sqrt(duration/60 + 1) 实现递减增长，避免长时间挂机导致经验发散
- 参考值：0分钟→1.0x，60分钟→1.41x，240分钟→2.0x，1440分钟(24h)→5.0x
  硬性上限

- 最终结果封顶 10000，防止数值过大或溢出
### 4.5 `RedisService` — Redis 操作封装

**位置**：`component/redis/RedisService.java`

| 方法 | 用途 |
|---|---|
| `setValue(key, value[, timeout])` | 写字符串值 |
| `getValue(key)` | 取值 |
| `setIfAbsent(key, value, timeout)` | SETNX |
| `getExpireSeconds(key)` | 查 TTL |
| `deleteValue(key)` | 删除 |
| `deleteIfValueMatches(key, expected)` | Lua 原子比较删除 |

**强制**：业务代码中**不要直接注入 `RedisTemplate`**，统一走 `RedisService`。若现有方法不够，在 `RedisService` 中补充，不要绕过。

### 4.6 `RedisDistributedLock` — 分布式锁

**位置**：`component/redis/RedisDistributedLock.java`

| 方法 | 用途 |
|---|---|
| `tryLock(key, lockTtl)` | 尝试一次加锁，立即返回 token 或 null |
| `lock(key, lockTtl, waitTime)` | 自旋等待加锁，超时返回 null |
| `unlock(key, token)` | Lua 原子释放（token 匹配才删） |

**强制**：需要分布式互斥（如登录计数、库存扣减）时注入此 Bean，**不要**自己用 `setIfAbsent` + `delete` 实现（会有锁误删风险）。

### 4.7 `AbstractCacheEngine` + `ICacheable` — 缓存模板

**位置**：`component/redis/AbstractCacheEngine.java`

| 方法 | 说明 |
|---|---|
| `cache(T object)` | 序列化后写入 Redis |
| `getCachedById(ID id)` | 读缓存，未命中抛 `CacheMissedException` |
| `getAndDeleteCacheById(id)` | 读并删（一次性验证码场景） |
| `getKeyPrefix()` | **子类实现**：key 前缀 |
| `getId(T object)` | **子类实现**：从对象取 ID |
| `deserializeCachedObject(json)` | **子类实现**：反序列化 |

**强制**：新增缓存一律继承 `AbstractCacheEngine`，Cache-Aside 的"查缓存→未命中查库→回写"模式在 Service 层实现（参考 `UserService.getUserById`）。

### 4.8 `ApiResponse` — 统一响应体

**位置**：`common/dto/ApiResponse.java`

```java
ApiResponse.ok(data)                    // { success:true, message:"OK", data }
ApiResponse.ok("自定义消息", data)
ApiResponse.error("错误描述")           // { success:false, message, data:null }
```

**强制**：所有接口返回体统一用 `ApiResponse`（文件上传等特殊场景除外）。`AuthController` 中目前混用了 `Map.of(...)`，新代码请统一到 `ApiResponse`。

### 4.9 异常体系 + `GlobalExceptionHandler`

**位置**：`common/exceptions/` + `GlobalExceptionHandler.java`

| 异常 | 父类 | HTTP 状态 | 用途 |
|---|---|---|---|
| `ApiException` | RuntimeException | 自定义（构造时传入） | 通用业务异常 |
| `LoginFailedException` | ApiException | 400 / 423 | 登录失败（含剩余次数/锁定时长） |
| `UnauthorizedException` | ApiException | — | 未授权 |
| `UserNotFoundException` | RuntimeException | 400 | 用户不存在 |
| `EmailNotFoundException` | RuntimeException | 400 | 邮箱不存在 |
| `PasswordIncorrectException` | RuntimeException | — | 密码错误 |
| `EmailBindingDeliveryException` | RuntimeException | — | 邮件发送失败 |
| `CacheException` | RuntimeException | — | 缓存写入失败 |
| `CacheMissedException` | RuntimeException | — | 缓存未命中（用于控制流，非错误） |

**强制**：
- 业务错误抛 `ApiException` 或其子类，**不要**在 Controller 里 `try-catch` 后手动 `return ResponseEntity.status(...)`。
- `GlobalExceptionHandler` 已统一处理 `SecurityException`→401、`ApiException`→对应状态码、`IllegalArgumentException`→400、参数校验异常→400。**不要**自己写 `@ExceptionHandler`。

### 4.10 `PasswordEncoder` — 密码哈希

**位置**：`common/config/security/SecurityConfig.java`（`BCryptPasswordEncoder` Bean）

**强制**：密码哈希/比对一律注入 `PasswordEncoder`，**不要**自己 `MessageDigest` 或存明文。

---

## 五、强制开发规范

### 5.1 包与命名

| 规则                                                | 说明 |
|---------------------------------------------------|---|
| Controller 放 `api/<域>/`                           | 如 `api/user/`、未来 `api/career/` |
| Service 放 `module/<域>/service/`                | 接口放 `component/abstracts/` |
| Repository 放 `module/<域>/repository/`             | 统一管理 |
| 缓存类放 `component/caching/`                         | 继承 `AbstractCacheEngine` |
| 实体放 `module/<域>/entity/<域>/`                      | |
| DTO 放 `common/dto/`，请求 DTO 放 `common/dto/request/` | |
| 异常放 `common/exceptions/`                          | 继承 `ApiException` 或 `RuntimeException` |
| 配置类放 `common/config/`                             | |

### 5.2 新增登录方式（强制流程）

1. 新建 `component/login/XxxStrategy.java`，继承 `BaseLoginStrategy`
2. 实现 `getType()`（返回如 `"SMS_CODE"`）、`doAuthenticate()`、`extractIdentityKey()`
3. 如需关闭失败计数，覆盖 `needAttemptTracking()` 返回 `false`
4. 在 `common/config/login/LoginStrategyConfig.java` 中注册 `@Bean("SMS_CODE")`
5. **不要修改** `AuthController.login()`、`LoginStrategyFactory`、`BaseLoginStrategy`

### 5.3 新增缓存（强制流程）

1. 新建 `component/caching/XxxCache.java`，继承 `AbstractCacheEngine<实体, ID类型>`
2. 实现 `getKeyPrefix()`、`getId()`、`deserializeCachedObject()`
3. 在 Service 中按 Cache-Aside 模式使用（先 `getCachedById`，catch `CacheMissedException` 后查库再 `cache`）

### 5.4 新增受保护接口（强制）

1. Controller 方法首行调用 `SecurityUtil.requireAuthentication()` 或 `SecurityUtil.getCurrentUserId()`
2. 当前用户信息从 `SecurityUtil` 获取，**不要**从请求参数/cookie 中取用户 ID

### 5.5 响应与异常

- 成功：`return ResponseEntity.ok(ApiResponse.ok(data))`
- 业务错误：`throw new ApiException(HttpStatus.XXX, "消息")`
- **不要**在 Controller 中返回 `null`、空 `Map` 或裸字符串作为错误响应

### 5.6 依赖注入

- 统一用**构造器注入**（参考现有 Controller/Service），不要字段注入（`@Autowired` 字段）
- `BaseLoginStrategy` 中字段注入是历史遗留，新代码不要效仿

### 5.7 日志

- 统一用 Lombok `@Slf4j`，**不要** `System.out.println`
- 异常日志用 `log.error("...", e)`（带堆栈），不要 `log.error(e.getMessage())`

---

## 六、禁止事项（Anti-Pattern 清单）

> 以下行为在 Code Review 中会被打回。

1. **禁止重复造轮子**：不要自己写 JWT 签发/解析、Redis 操作、分布式锁、ID 生成、密码哈希、缓存模板。一律用第四章列出的已有组件。
2. **禁止绕过分层**：Controller 不得直接操作 Repository / RedisTemplate；Service 不得返回 `HttpServletResponse` 或处理 HTTP 头。
3. **禁止在 Controller 写业务逻辑**：Controller 只做"收参 → 调 Service → 封装响应"。
4. **禁止硬编码配置**：密钥、超时、URL 等必须走 `application.yml` + `@Value`。
5. **禁止吞异常**：catch 后要么重新抛出业务异常，要么 `log.error` 并降级，**不要**空 catch。
6. **禁止返回裸 Map 作为接口响应**：用 `ApiResponse` 或明确的 DTO。
7. **禁止在 `common` 层依赖 `entity`/`component`**：`common` 是最底层，只能被依赖，不能反向依赖。
8. **禁止直接操作 `SecurityContextHolder`**：用 `SecurityUtil`。

---

## 七、迭代 1 已知待办 / 风险点

> 非阻塞，但开发时请注意。

| 项 | 说明 |
|---|---|
| `SecurityConfig` 中 `anyRequest().permitAll()` | 当前所有接口都放行，鉴权靠 Controller 主动调 `SecurityUtil.requireAuthentication()`。后续需收紧为按路径鉴权。 |
| `JwtUtil` 依赖 `User` 实体 | `common` 层反向依赖 `entity`，历史遗留。后续可改为传 `userId + username` 两个参数解耦。 |
| `Snowflake` 的 `@Value` 注入静态字段 | Spring 不支持注入静态字段，当前 `datacenterId`/`workerId` 实际为默认值 0。多实例部署前需修复（改为非静态或 `@PostConstruct`）。 |
| `UserService.loginViaEmailPwd` 中 `passwordEncoder.matches` 参数顺序 | 当前是 `matches(hash, rawPassword)`，BCrypt 的 `matches(rawPassword, hash)` 才是标准签名，需确认是否写反。 |
| `UserCache.setUserEmailKey` 中 key 用了硬编码 `emailPrefix` 字段 | 与 `getUserByEmail` 中 `getKeyPrefix() + email` 不一致，可能导致邮箱查缓存失效。 |
| `AuthController` 响应格式不统一 | 登录/注册返回 `Map.of(...)`，未走 `ApiResponse`。 |
---

## 八、快速联调 Checklist

开发新功能前，请确认：

- [ ] 我需要的能力在第四章是否已有？有就直接注入复用。
- [ ] 我的代码是否遵守了分层（Controller → Service → Repository）？
- [ ] 受保护接口是否调用了 `SecurityUtil.requireAuthentication()`？
- [ ] 响应是否用了 `ApiResponse`？异常是否抛 `ApiException` 系列？
- [ ] 是否避免了直接注入 `RedisTemplate` / 直接操作 `SecurityContextHolder` / 自己写 JWT？
- [ ] 新实体 ID 是否用了 `Snowflake.nextId()`？

---
---

# AmiLingo Platform · 迭代 1 补充开发文档（lang 模块与 AI 网关）

> 版本：迭代 1 · 2026-09-13 补充
> 负责人：QianLi071
> 读者：全体后端开发人员（尤其是 lang / AI 方向后续接手者）
> 目的：作为迭代 1 的追加内容，定义 **AI 能力网关 `infra/ai`** 的统一抽象与使用方式；落地 **lang 语言学习模块骨架**与第一条可联调的 **AI 写作评分链路**；明确 lang 模块各层职责、与迭代 1 共享基础设施的复用关系，以及当前已知偏差与待办。
> 说明：上文 §1.3 包结构中 `lang/`、`infra/ai` 的"业务领域占位（迭代2+）"状态已被本补充内容取代——二者在迭代 1 内已开始落地。

---

## 九、迭代 1 补充范围与业务链路（lang / AI）

### 9.1 本次交付物

| 交付物 | 状态 |
|---|---|
| `infra/ai/AiGateway`：统一 AI 能力接口（对话 / 强制 JSON / 向量 / 相似度） | ✅ 已完成 |
| `infra/ai/NoopAiGateway`：桩实现，保证 Spring 上下文可启动 | ✅ 已完成 |
| `module/lang` 分层骨架：controller / service / repository / entity / dto / enums / planner / ai / job | ✅ 已完成（占位为主） |
| AI 写作评分链路：`WritingScorer` + `WritingPrompt` + `WritingGradeRequest/Response` | ✅ 已完成（真实模型接入前不可调用） |
| `planner/PlanGenerator`：AI 排程引擎设计草案（Javadoc 五步） | 🔄 进行中（仅设计，无实现） |
| REST 接口、JPA 实体化、口语评分、薄弱点分析 | 📋 待办 |

### 9.2 业务链路：AI 写作评分（本迭代唯一有实质逻辑的链路）

```
客户端
  │  POST（写作提交接口，PracticeController —— 📋 尚未实现）
  ▼
module/lang/controller/PracticeController        ← 占位：只收参，不写业务
  │
  ▼
module/lang/service/WritingService               ← 占位：后续编排（取目标分/历史趋势、记流水、发宠物经验）
  │
  ▼
module/lang/ai/WritingScorer.score(WritingGradeRequest)
  │  ① 读取 WritingPrompt.SYSTEM（考官角色设定）
  │  ② WritingPrompt.USER_TEMPLATE.formatted(
  │        targetTotal, targetParts, "N/A"(历史趋势暂硬编码), prompt, essay)
  ▼
infra/ai/AiGateway.chatJson(system, user, WritingGradeResponse.class)
  │  ③ 强制模型按 JSON schema 输出，直接反序列化为 WritingGradeResponse
  ▼
当前实现：NoopAiGateway → 抛 UnsupportedOperationException（真实模型未接入）
目标实现：返回 WritingGradeResponse(overall, breakDown, feedback, nextActions)
```

**联调说明**：
- 目前入口 Controller 和 Service 均为空类，链路只能从 `WritingScorer.score()` 直接调起（单测/临时 main），且运行期会在 `NoopAiGateway` 处抛异常——这是预期行为。
- 接入真实模型时：**新增一个 `AiGateway` 实现类**即可，`WritingScorer` 无需改动（见 11.3 双 Bean 注意事项）。
- 后续 `WritingService` 还需负责：评分后写 `learning_records`（zone=lang）、调 `PetUtil.calcExp(score, duration)` 发宠物经验，**禁止在 Scorer 里做这些**。

### 9.3 规划中链路：AI 排程引擎（planner）

`PlanGenerator` 的 Javadoc 已固化五步设计，供后续实现者严格按此落地，禁止另起一套：

```
PlanResult generate(PlanInput input)
  Step 1  quotaCalculator.calc(input)                      按阶段模型+差距档位算总任务量
  Step 2  slotExtractor.extract(userId, examDate)         从课表抽时间槽，过滤 <30 分钟
  Step 3  taskAllocator.allocate(quota, slots, weak)      贪心分配：摸底>强化>冲刺，薄弱优先
  Step 4  continuityRule.adjust(tasks)                    连续性规则：同分科不连续 3 天
  Step 5  aiGateway.chat(PLAN_NARRATOR_PROMPT, context)   AI 生成本周目标文案
```

依赖类型（`PlanInput`/`PlanResult`/`WeeklyQuota`/`TimeSlot`/`StudyTask` 及 quotaCalculator、slotExtractor）**尚不存在**，实现时在 planner 包内补齐；AI 调用必须走 `AiGateway`，不得自建 HTTP 客户端。

---

## 十、lang 模块结构与职责

```
module/lang/
├── controller/     # REST 接口（注意：按 §5.1 规范最终须迁移到 api/lang/，见 11.1）
│   ├── ScheduleController.java   # 学习课表/时间块
│   ├── GoalController.java       # 考试目标、目标分数
│   ├── PlannerController.java    # AI 排程计划
│   └── PracticeController.java   # 做题/写作/口语练习提交（写作评分入口）
├── service/        # 业务编排
│   ├── ScheduleService / GoalService / PlannerService / WritingService
├── repository/     # 数据访问（当前为空接口，落地时继承 JpaRepository）
│   ├── ScheduleBlockRepository / ScoreTargetRepository / PracticeAttemptRepository
├── entity/         # 数据库实体（当前为空类，落地时加 @Entity，ID 用 Snowflake）
│   ├── ScheduleBlock / ExamGoal / ScoreTarget / PracticeQuestion / PracticeAttempt
├── dto/
│   ├── request/WritingGradeRequest.java    # record，见 10.2
│   └── response/WritingGradeResponse.java  # record，见 10.2
├── enums/
│   ├── ExamType.java   # 空枚举，待补 IELTS / TOEFL / GMAT
│   └── Section.java    # 空枚举，待补听力/阅读/写作/口语等分科
├── planner/        # AI 排程引擎子包（§9.3）
│   ├── PlanGenerator（五步设计草案）/ StageModel / TaskAllocator / ContinuityRule
├── ai/             # lang 专属 AI 编排（不是基础设施；基础设施只有 infra/ai/AiGateway）
│   ├── WritingScorer.java       # ✅ 已实现
│   ├── SpeakingScorer.java      # 📋 占位，仿 WritingScorer
│   ├── WeaknessAnalyzer.java    # 📋 占位，基于 PracticeAttempt 流水分析薄弱分科
│   ├── PlanNarrator.java        # 📋 占位，排程文案（配合 planner Step 5）
│   └── prompt/
│       ├── WritingPrompt.java   # ✅ IELTS TR/CC/LR/GRA 评分提示词
│       └── SpeakingPrompt.java  # 📋 占位
└── job/            # 定时任务
    ├── TimelineJob.java         # 📋 学习时间线相关
    └── SeatReminderJob.java     # 📋 考位提醒
```

### 10.1 `infra/ai/AiGateway` —— AI 能力统一入口（全员复用，禁止另建客户端）

**位置**：`infra/ai/AiGateway.java`

| 方法 | 用途 | 典型调用方 |
|---|---|---|
| `String chat(String systemPrompt, String userPrompt)` | 通用对话，返回纯文本 | PlanNarrator、反馈润色 |
| `<T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType)` | 强制 JSON 输出并反序列化 | WritingScorer、SpeakingScorer、WeaknessAnalyzer |
| `List<float[]> embed(List<String> texts)` | 向量嵌入（范文检索、同义替换） | lang 后续检索类功能 |
| `double similarity(float[] a, float[] b)` | 余弦相似度 | 配合 embed 做匹配 |

**强制**：
- 所有大模型 / 向量模型调用一律注入 `AiGateway`，**禁止**在 lang 或其他模块里新建 OkHttp/RestClient 直连模型厂商。
- Prompt 文本放各模块自己的 `ai/prompt/` 包，以 `public static final String` 常量（文本块）维护，**不要**散落在方法体里。
- 需要结构化输出时一律用 `chatJson` + record DTO，不要自己 `ObjectMapper.readTree` 手解析。

**位置**：`infra/ai/NoopAiGateway.java`（`@Component`）：4 个方法全部抛 `UnsupportedOperationException("...尚未接入实现")`，作用是在真实模型接入前让 Spring 上下文与编译保持绿色。

### 10.2 写作评分 DTO（Java record）

| 类型 | 字段 |
|---|---|
| `WritingGradeRequest` | `String examType`（IELTS/TOEFL/GMAT）、`String prompt`（题目）、`String essay`（作文）、`Double targetTotal`（目标总分）、`Map<String,Double> targetParts`（单科目标分） |
| `WritingGradeResponse` | `Double overall`（总分）、`Map<String,Integer> breakDown`（TR/CC/LR/GRA 分项）、`String feedback`（≤200字中文建议）、`List<String> nextActions`（下一步动作，每条≤15字） |

`WritingPrompt.USER_TEMPLATE` 约定模型输出 JSON schema：`overall`、`breakdown`、`feedback`、`next_actions`，并约束分项为 0–9 整数、禁止免责套话。

---

## 十一、与迭代 1 规范的对接要求（防冲突 · 重点）

lang 骨架先行，落地实现时**必须**向迭代 1 的强制规范对齐：

1. **包路径偏差（已知，待迁移）**：当前 Controller 在 `module/lang/controller/`、DTO 在 `module/lang/dto/`。按 §5.1：
   - Controller 最终放 `api/lang/`（如 `api/lang/PracticeController.java`）；
   - 通用请求/响应 DTO 放 `common/dto/`；仅 lang 内部使用的 DTO 可保留在模块内，但跨模块联调的（如评分结果）应上移。
   - 迁移时只移动包路径，不改类名，避免其他人引用断裂。
2. **Controller 三件套**：`@RequireAuth` 做登录校验（或首行 `SecurityUtil.requireAuthentication()`）、需要限流的提交接口加 `@RateLimit`、返回体一律 `ApiResponse.ok(...)`，禁止裸 Map。
3. **Service 规范**：放 `module/lang/service/`，接口定义到 `component/abstracts/`（如 `IWritingService`），构造器注入；`WritingScorer` 属 AI 编排组件，不直接处理 HTTP/持久化。
4. **实体与 Repository**：实体补 `@Entity`，主键用 `Snowflake.nextId()`；Repository 继承 `JpaRepository<实体, Long>`，放 `module/lang/repository/`（现有空接口直接 `extends JpaRepository` 即可）。
5. **学习流水与宠物经验**：每次练习评分后写 `learning_records`（zone=`lang`），经验值统一调 `PetUtil.calcExp(score, duration)`，**禁止**自造经验公式。
6. **枚举**：`ExamType`/`Section` 补值后，实体字段优先用枚举而不是字符串；`WritingGradeRequest.examType` 后续也应改为枚举类型。

### 11.3 AI 接入注意

- 真实实现上线后，用 `@Primary` 或 `@ConditionalOnMissingBean` 让 `NoopAiGateway` 自动退让，避免容器中出现两个 `AiGateway` Bean 导致注入冲突。
- 模型密钥、base-url、超时走 `application.yml` 占位符 + 环境变量（与 DATABASE_PASSWORD 等同样方式），**禁止硬编码**。
- 真实模型不可用时应降级抛 `ApiException`（503 语义），由 `GlobalExceptionHandler` 统一处理，不要吞异常返回空评分。

---

## 十二、迭代 1 补充内容的已知风险 / 待办

| # | 项 | 说明 | 处理建议 |
|---|---|---|---|
| 1 | 🔴 JSON 字段名不一致 | Prompt 约定输出 `breakdown` / `next_actions`，record 字段为 `breakDown` / `nextActions`，直接 `chatJson` 反序列化会得到 null | 接入真实模型前给 DTO 字段加 `@JsonProperty("breakdown")` / `@JsonProperty("next_actions")`，或统一改名 |
| 2 | 🔴 AI 未接入 | `NoopAiGateway` 全方法抛异常，调 `WritingScorer.score()` 运行期必失败 | 提供真实 `AiGateway` 实现 + 配置项后再开放接口 |
| 3 | 🟠 空枚举 | `ExamType`、`Section` 无枚举值，字符串比较无法编译 | 尽快补 IELTS/TOEFL/GMAT 与四个分科 |
| 4 | 🟠 包路径偏差 | Controller/DTO 暂在 module 内，与 §5.1 不一致 | 实现接口前迁移到 `api/lang/`、`common/dto/` |
| 5 | 🟠 Planner 依赖缺失 | `PlanGenerator` 引用的 5+ 个类型/组件不存在 | 按 §9.3 在 planner 包内补齐，勿在 service 包散建 |
| 6 | 🟡 历史趋势硬编码 | `WritingScorer` 中"最近3次写作趋势"固定传 `"N/A"` | 由 WritingService 从 PracticeAttempt 流水聚合后传入 |
| 7 | 🟡 Repository/Entity 未 JPA 化 | 空接口/空类不产生表结构 | 随首个接口落地时补注解与继承关系 |
| 8 | 🟡 无测试 | 仅有上下文加载测试 | 补 WritingScorer 单测（mock AiGateway，验证模板 5 个占位符顺序）与 NoopAiGateway 行为测试 |

---

## 十三、迭代 1 补充部分任务看板（图例见 docs/util/scrum.md）

| 任务 | 负责人 | 状态 |
|---|---|---|
| AiGateway 接口 + NoopAiGateway 桩 | QianLi071 | ✅ 已完成 |
| lang 模块分层骨架（9 个子包） | QianLi071 | ✅ 已完成 |
| WritingScorer + WritingPrompt + 评分 DTO | QianLi071 | ✅ 已完成 |
| 评分 DTO 字段名对齐（@JsonProperty） | QianLi071 | 📋 待办 🔥 |
| PlanGenerator 排程引擎实现 | QianLi071 | 🔄 进行中 |
| PracticeController + WritingService（首个可联调接口） | QianLi071 | 📋 待办 🔥 |
| 实体 JPA 化 + Repository 继承 JpaRepository | QianLi071 | 📋 待办 |
| Controller/DTO 包路径迁移对齐 §5.1 | QianLi071 | 📋 待办 |
| SpeakingScorer / SpeakingPrompt | （认领） | 📋 待办 |
| WeaknessAnalyzer / PlanNarrator | （认领） | 📋 待办 |
| TimelineJob / SeatReminderJob | （认领） | 📋 待办 |
| 真实 AiGateway 实现（模型接入） | （认领） | 📋 待办 |

---
---

# AmiLingo Platform · 迭代 1 补充开发文档（二）（Python AI 服务落地与前端真实联调）

> 版本：迭代 1 · 2026-09-20 补充（二）
> 负责人：QianLi071
> 读者：全体后端 / 前端开发人员（尤其是 AI 链路、lang 方向接手者）
> 目的：记录 AI 写作评分从 Noop 桩到**真实本地大模型链路**的落地方式：FastAPI 路由层、Prompt 单一数据源、统一响应契约、Java 侧 HTTP 桥接、多模态图片评分、以及 IELTS 前端 Demo 的真实 AI 接入与离线降级约定。
> 与补充（一）的关系：§9-13 中"真实模型未接入"的状态描述已被本文档更新；§12 风险表 #1、#2 的关闭/延续状态见第二十章；§9.1 交付物状态以本文档第十四章为准。前端调用的完整请求/响应示例见独立文档 [writing-frontend-api.md](writing-frontend-api.md)。

---

## 十四、补充（二）范围与总体架构

### 14.1 本次交付物

| 交付物 | 状态 |
|---|---|
| FastAPI 路由层：`POST /api/v1/writing/grade`、`POST /api/v1/writing/grade-image`、`GET /api/v1/writing/health` | ✅ 已完成（前端↔Python 已端到端验证） |
| Pydantic v2 请求/响应 Schema（含泛型 `ApiResponse[T]`、23 字段 `WritingGradeResponse`） | ✅ 已完成 |
| Prompt 单一数据源 `prompts/registry.py`，按 `exam_type` 分发（CET4/CET6/IELTS_A/IELTS_G） | ✅ 已完成 |
| AI 客户端从 Ollama 迁移至 llama.cpp（GGUF）：`LlamaClient` 支持文本/多模态 chat、embed、health | ✅ 已完成 |
| 启动脚本：`start_llama.bat`（8081，检测到 mmproj 自动挂载多模态）、`start_embed.bat`（8082） | ✅ 已完成 |
| Java `PythonAiGateway`（`@Primary`，HTTP 桥接 FastAPI）+ `WritingScorer` 改发 exam_type/topic/content/level | ✅ 已完成（🔴 Java↔Python 解包待修，见第二十章 #1） |
| IELTS 前端练习页：文本评分接真实 AI，失败降级 mock，显示「（离线模拟）」 | ✅ 已完成 |
| IELTS onboarding 测评：`Assessment.scoreWritingRemote` + 1-3 分钟 loading 提示 | ✅ 已完成 |
| 图片作文识别 + 评分链路（多模态 mmproj，单次推理出识别原文+评分） | 🔄 进行中（代码完成，mmproj 端到端待实测验收） |
| 前端接口文档 `docs/writing-frontend-api.md` | ✅ 已完成 |
| PracticeController / WritingService REST 化、planner、口语评分、薄弱点分析 | 📋 待办（沿用 §13 看板） |

### 14.2 进程拓扑与目录结构

```
浏览器 IELTS Demo(localhost:8080 静态)
    │  fetch（CORS 已放行 8080/5173/3000/127.0.0.1:8080/null）
    ▼
FastAPI 网关 0.0.0.0:8000（main.py）
    │  writing_service 编排：基础检查 → 评分 → 逐句批改 → 范文检索
    ├─► 对话 llama-server 127.0.0.1:8081（Gemma q4_0 + 可选 mmproj，-c 8192）
    └─► 向量 llama-server 127.0.0.1:8082（Qwen3-Embedding-0.6B Q8_0）

Spring Boot 主后端 :8080
    └─ PythonAiGateway（@Primary，RestTemplate）
         └─ HTTP POST http://localhost:8000/api/v1/writing/grade
```

> 端口说明：Java 后端与 IELTS 静态 Demo 默认都是 8080，二者不会同时对前端提供服务，本地按场景二选一启动；Python 侧可通过 `.env` / 环境变量覆盖地址（`LLM_BASE_URL`、`EMBEDDING_BASE_URL`、`LLAMA_TIMEOUT` 默认 300s）。

```
src/main/python/OfflineAssistant/
├── main.py                       # FastAPI 入口：CORS + 注册 writing/quiz/stats 路由 + GET /health
├── requirements.txt
├── backend/
│   ├── app/
│   │   ├── api/v1/writing.py     # 三个写作端点；RuntimeError→503，其余异常→500
│   │   ├── core/config.py        # CORS 白名单、BASE_DIR、GGUF 路径、端口、超时
│   │   ├── prompts/
│   │   │   ├── registry.py       # get_grade_prompt / get_suggestion_prompt（唯一入口）
│   │   │   ├── writing_prompts.py# CET4/CET6 评分 + 逐句批改
│   │   │   └── ielts_prompts.py  # IELTS TR/CC/LR/GRA 评分（含 next_actions 硬约束）
│   │   ├── schemas/
│   │   │   ├── request.py        # WritingGradeRequest / WritingGradeImageRequest
│   │   │   └── response.py       # ApiResponse[T] 泛型 + WritingGradeResponse
│   │   └── services/
│   │       ├── ai_client.py      # LlamaClient + 单例 ai_client(8081)、embed_client(8082)
│   │       ├── embedding_service.py
│   │       └── writing_service.py# 批改编排（单例 writing_service）
│   ├── scripts/
│   │   ├── start_llama.bat       # 8081；mmproj 存在则追加 --mmproj，缺失仅警告
│   │   └── start_embed.bat       # 8082
│   └── data/                     # SQLite / 向量缓存（不入库）
├── frontend/IELTS/               # 纯静态 Demo（无 npm/构建工具）
│   ├── index.html
│   └── js/api.js  data.js  assessment.js  onboarding.js  app.js ...
├── models/                       # 3 个 GGUF（不入库）
└── llama.cpp/                    # llama.cpp 源码与构建产物（不入库）
```

---

## 十五、核心业务链路

### 15.1 链路四：文本作文评分（前端 → FastAPI → 对话模型）

```
IELTS 练习页（app.js renderWriting）
  │  scoreWritingRemote(essay, topic)
  │    → window.AmiAPI.callWritingGrade({content, topic, examType:'IELTS_A', level:'Academic'})
  ▼
POST http://localhost:8000/api/v1/writing/grade          # api/v1/writing.py
  │  Pydantic 校验 WritingGradeRequest（exam_type 为 Literal 四值，非法→422）
  ▼
writing_service.grade_essay(content, topic, level, exam_type)
  │  ① _basic_check()                 词数（CET4 120-180/CET6 150-200/IELTS 250-300）+ 模板句检测（不走 AI）
  │  ② _get_grade()                   registry.get_grade_prompt(exam_type).format(...)
  │       └─ ai_client.chat(format='json', max_tokens=2048) → 去 markdown 包裹 / 正则兜底抽 JSON
  │  ③ _get_suggestions()             registry.get_suggestion_prompt(exam_type) → 逐句批改
  │  ④ _find_similar_essays()         embedding_service.find_similar() → 8082 向量检索 top2
  ▼
ApiResponse{success:true, message:'ok', data: WritingGradeResponse(23 字段)}
  │  llama-server 不可达：ai_client 抛 RuntimeError → 路由转 HTTP 503 {"detail": "AI 服务不可用: ..."}
  │  其他异常：→ HTTP 500；模型 JSON 偶发解析失败：service 内部降级为空评分，不抛 500

前端 api.js 字段映射（d = json.data）：
  d.total_score → overall / overallText(保留1位小数)
  d.scores.TR/CC/LR/GRA → breakdown.{TR,CC,LR,GRA}
  d.band / d.band_comment / d.next_actions / d.dimension_comments / d.sentences
  统一追加 source:'ai'
```

**联调要点**：本地 Gemma 单次评分实测约 1-3 分钟（168-194 秒量级），属正常现象；前端在等待期间必须展示 loading，不得让按钮可重复点击。

### 15.2 链路五：图片作文识别 + 评分（多模态，单次推理）

```
app.js：隐藏的 <input type="file"> 由按钮触发
  │  FileReader.readAsDataURL(file) → 去掉 "data:image/xxx;base64," 前缀
  │  imageType 取 file.type（'image/jpg' 归一为 'jpeg'）
  ▼
AmiAPI.callWritingGradeImage({imageBase64, topic, examType, level, imageType})
  │  AbortController 10 分钟超时（600000ms）
  ▼
POST /api/v1/writing/grade-image
  │  路由拼 data URL：f"data:image/{image_type};base64,{image_base64}"
  ▼
writing_service.grade_essay_image()
  │  构造多模态 messages：content = [ {type:'text'}, {type:'image_url', image_url:{url}} ]
  │  文本指令：逐字转录（不改写/不翻译）+ 按考试标准评分，JSON 额外输出 recognized_essay
  │  ai_client.chat(messages, format='json', max_tokens=3072)   ← 单次调用
  ▼
返回结构与 /grade 完全一致；其中：
  · content = recognized_essay（识别原文，前端放入 <details open>「AI 识别出的原文（请核对）」）
  · word_count / basic_issues 基于识别文本重新计算
  · sentences=[]、summary=""、similar_essays=[]（图片模式不做第二次长耗时推理）
```

前置条件：8081 启动时已挂 `--mmproj models\gemma-4-E2B-it-mmproj.gguf`（脚本检测到文件才追加，缺失时纯文本启动并警告）；未挂载时图片请求会在模型侧失败并以 503/500 返回，前端走 mock 降级。图片评分期间隐藏 `#word-count`，结束（finally）后恢复。

### 15.3 链路六：Java 主后端 → Python（PythonAiGateway）

```
WritingScorer.score(WritingGradeRequest record)
  │  组装 payload：exam_type（缺省 CET4）/ topic / content / level（mapLevel 映射）
  │  objectMapper.writeValueAsString(payload)
  ▼
aiGateway.chatJson(PythonAiGateway.ROUTE_WRITING_GRADE /* "writing_grade" */, json, WritingGradeResponse.class)
  │  注入实际命中 @Primary 的 PythonAiGateway（NoopAiGateway 自动退让）
  ▼
PythonAiGateway.gradeWriting()
  │  RestTemplate.postForObject(pythonBaseUrl + "/api/v1/writing/grade")
  │  配置项 ai.python.base-url（application.yml，默认 http://localhost:8000，支持 AI_PYTHON_BASE_URL 覆盖）
  ▼
objectMapper.readValue(responseJson, WritingGradeResponse.class)
```

- **systemPrompt 当路由键用**：只有 `"writing_grade"` 被路由到 Python；未知键与 `chat`/`embed`/`similarity` 方法一律抛 `UnsupportedOperationException`（待后续迭代补通用对话/向量端点）。
- **Java 端不持有任何 Prompt 文本**：`WritingPrompt` 已标 `@Deprecated`，Prompt 单一数据源在 Python `registry`。
- 🔴 当前该链路**尚未端到端验证通过**，原因与修复方案见第二十章 #1（ApiResponse 未解包 + snake/camel 命名未映射）。

### 15.4 降级链路：Python 不可用时前端 mock 兜底（强制，禁止白屏）

```
AmiAPI 调用抛错（网络不通 / !resp.ok / json.success=false / 503 / 超时）
  ▼
练习页 app.js：catch → 回退原有 mockWritingScore()，结果区追加灰色「（离线模拟）」标签
onboarding assessment.js：Assessment.scoreWritingRemote() catch → 回退原 scoreWriting() mock
  │
  ├─ 按钮状态恢复必须放在 finally（防止异常后按钮永久禁用）
  ├─ 评分失败的错误信息用红色展示
  └─ speaking/listening/reading 三个模块的既有 mock 保持不变，本次未改动
```

---

## 十六、Python 侧关键模块与可联调清单

| 文件 / 单例 | 职责 | 联调 / 扩展约定 |
|---|---|---|
| `main.py` | FastAPI 应用、CORS、路由前缀 `/api/v1` | 新 router 在 v1 目录建好后到此 `include_router` |
| `api/v1/writing.py` | grade / grade-image / health | 业务错误用 `HTTPException`；`RuntimeError` 语义固定为 503 |
| `schemas/request.py` | `WritingGradeRequest`（level 默认「四级」/exam_type 默认 CET4）、`WritingGradeImageRequest`（level 默认 Academic/exam_type 默认 IELTS_A/image_type 默认 jpeg） | 新考试类型先扩 `Literal` 再注册 Prompt |
| `schemas/response.py` | `ApiResponse[T]`（success/message/data）、`WritingGradeResponse`（23 字段全必填，无默认值） | 前端契约，**加字段可以、删/改字段名必须同步前端 api.js 与 writing-frontend-api.md** |
| `services/writing_service.py`（单例 `writing_service`） | 四步编排、JSON 提取兜底、档位判定、词数/模板规则、图片单次多模态 | 业务编排只写在这里；`_extract_json` 静态方法可复用 |
| `services/ai_client.py`（单例 `ai_client` 8081、`embed_client` 8082） | llama-server HTTP 客户端：chat 支持多模态可选参数、embed、health/think/find_similar | 所有模型 HTTP 调用走它，**禁止另建客户端**；可选参数不破坏既有文本调用 |
| `services/embedding_service.py` | 向量化 + 相似范文检索 | 8082 不可用时不应拖垮评分主链路 |
| `prompts/registry.py` | `GRADE_PROMPTS` / `SUGGESTION_PROMPTS` 两张表 + getter | 新增考试类型：写 `xxx_prompts.py` + 注册表加一行，业务代码零改动 |
| `core/config.py`（单例 `settings`） | CORS 白名单、BASE_DIR、GGUF 路径、端口、`LLAMA_TIMEOUT=300` | CORS 白名单为硬约束：5173/3000/8080/127.0.0.1:8080/`null` |

**IELTS Prompt 硬约束**（`ielts_prompts.IELTS_GRADE_PROMPT`）：输出 JSON 顶层必须含 `next_actions`，固定 3 条、每条 ≤20 个中文字、可执行，且分别覆盖 TR/CC/LR/GRA 不同维度；service 与前端对空数组都要有兜底。

---

## 十七、统一响应契约（前后端联调重点）

### 17.1 包装层

所有业务接口统一：`{ "success": true, "message": "ok", "data": <T> }`。
HTTP 层错误不走 ApiResponse（FastAPI 约定）：503/500 → `{"detail": "..."}`；请求体校验失败 → 422 `{"detail": [{...}]}`。前端只靠 `resp.ok` + `json.success` 判断，不依赖错误体结构。

### 17.2 data 字段分组（WritingGradeResponse）

| 分组 | 字段 | 说明 |
|---|---|---|
| 基本信息 | `content` `topic` `level` `exam_type` `word_count` `basic_issues[]` | 图片接口的 content 即识别原文 |
| 总分 | `total_score` `overall` `band` `band_comment` `total_106` `total_score_percent` | `total_score` 与 `overall` **始终同值**，供不同端各取一个；IELTS 0-9，CET 0-15 |
| 诊断 | `scores` `dimensions` `dimension_labels` `dimension_comments` `overall_comment` `feedback` `next_actions[]` | `scores` 与 `dimensions` 同值；`overall_comment` 与 `feedback` 同值；IELTS 键为 TR/CC/LR/GRA，CET 为 8 维 |
| 逐句批改 | `sentences[]` `summary` | 图片模式返回空 |
| 范文 | `similar_essays[]` | 图片模式返回空 |
| 元信息 | `generated_at` | ISO 时间字符串 |

双名字段（total_score/overall 等）是有意设计：前端一份渲染代码同时服务校园 CET 与 IELTS 两种语义习惯，**禁止删掉其中一组**。

### 17.3 前端映射（`frontend/IELTS/js/api.js`）

| Python 响应 | 前端归一结构 |
|---|---|
| `data.total_score` | `overall`（number）+ `overallText`（1 位小数字符串） |
| `data.scores.TR/CC/LR/GRA` | `breakdown.TR/CC/LR/GRA` |
| `data.band` / `data.band_comment` | `band` / `bandComment` |
| `data.next_actions` / `data.dimension_comments` / `data.sentences` | `nextActions` / `dimensionComments` / `sentences`（均带 `\|\| []/{}` 兜底） |
| `data.content`（图片） | `recognizedText` |
| —（追加） | `source: 'ai'`，mock 结果为 `_source: 'mock'` |

---

## 十八、IELTS 前端 Demo 接入约定（防回归 · 重点）

1. **全局对象，非 ES Module**：API 客户端固定在 `js/api.js`，挂 `window.AmiAPI = { callWritingGrade, callWritingGradeImage, API_BASE }`；`index.html` 中必须**先于 app.js 加载**（当前 api.js?v=2，app.js?v=3）。禁止引入 npm/webpack/vite，保持双击 / 静态服务器可运行。
2. **不改 HTML 结构与 CSS**：上传按钮复用现有 `.btn` 类；文件输入框隐藏、由按钮点击触发；识别原文用 `<details open>` + 摘要「AI 识别出的原文（请核对）」。
3. **超时**：图片请求用 AbortController 10 分钟；文本接口依赖浏览器默认超时但必须有 loading 文案（onboarding 文案：「AI 正在评分（调用本地大模型），约需 1-3 分钟，请耐心等待…」，内联样式写在既有模板字符串里）。
4. **降级与状态**：fetch 任何失败都回退既有 mock，绝不白屏；离线结果展示灰色「（离线模拟）」；错误信息红色；按钮恢复放 `finally`；图片评分期间隐藏 `#word-count`，完成后恢复。
5. **命名空间**：assessment.js 新增函数一律挂到 `Assessment` 命名空间导出（当前导出于文件末尾对象），避免与 app.js 的同名 `scoreWritingRemote` 全局冲突。
6. **缓存破坏（已踩坑）**：`python -m http.server` 存在启发式缓存，修改 api.js / app.js 后必须同步递增 `index.html` 中对应 `?v=` 版本号，否则浏览器跑旧代码。
7. **speaking / listening / reading 模块的 mock 与 Web Speech 逻辑保持不动**。

---

## 十九、Java 侧对接更新（对 §10 / §11.3 的修订）

1. 容器中同时存在 `NoopAiGateway`（@Component 桩）与 `PythonAiGateway`（@Component + `@Primary`），注入点统一拿到 Python 实现；新增第二实现时继续用 `@Primary` 或条件注解解决冲突，不要删除桩。
2. `WritingScorer.score()` 不再拼 Prompt 模板，改为发 4 字段 payload（`exam_type`/`topic`/`content`/`level`），目标分字段暂不下发；`WritingPrompt` 仅留作 @Deprecated 历史。
3. 配置：`application.yml` 新增 `ai.python.base-url`（`${AI_PYTHON_BASE_URL:http://localhost:8000}`），与数据库/Redis 一样走环境变量，禁止硬编码。
4. Python 不可用时 gateway 包装为 `RuntimeException` 抛出；后续 REST 化时应由 GlobalExceptionHandler 映射 503 语义（沿用 §11.3 第 3 条），不要吞异常返回空评分。
5. PythonAiGateway 复用 Spring 自带 Jackson（pom 已含 spring-boot-starter-json），**禁止为该桥接引入新 HTTP 客户端**。

---

## 二十、补充（二）已知风险 / 待办

| # | 项 | 说明 | 处理建议 |
|---|---|---|---|
| 1 | 🔴 Java↔Python 响应未解包 + 命名未映射 | Python 返回 `{success,message,data:{snake_case}}`；gateway 直接 readValue 成 Java `WritingGradeResponse`（camelCase、无 @JsonProperty），字段会全部为 null；§12 #1 在 Java 侧仍未关闭 | gateway 先用 JsonNode 取 `data`，再用 `SNAKE_CASE` 命名策略的 ObjectMapper 转 record；或给 record 补 `@JsonProperty`（total_106、basic_issues 等逐个对齐） |
| 2 | 🟠 RestTemplate 无超时配置 | `new RestTemplate()` 默认无读取超时，3 分钟推理期间线程可能无限阻塞 | 配 `SimpleClientHttpRequestFactory`（connect 5s / read ≥360s）或迁移 RestClient，超时走 §19.4 降级 |
| 3 | 🟠 多模态仅代码完成 | mmproj 实测验收未做（test_vision.py 已备好） | 重启 8081 确认加载 mmproj 后 `python -m backend.test_vision <图片路径>` + 浏览器上传按钮端到端验证 |
| 4 | 🟠 同步长连接体验 | 文本 1-3 分钟、图片 2-5 分钟，HTTP 同步阻塞；前端靠 loading 兜底 | 迭代后续做任务化（提交→轮询/SSE），当前不阻塞演示 |
| 5 | 🟡 图片模式无逐句/范文 | sentences/summary/similar_essays 固定空，避免二次长推理 | 验收后如需，再串行补调 8081/8082 |
| 6 | 🟡 评分 JSON 偶发不合规 | service 已做空评分兜底 + next_actions 空数组兜底，但会出现「成功返回空评分」 | 前端遇到全 0 分提示重试；后续在 Prompt 中固化 schema 示例（已部分完成） |
| 7 | 🟡 向量服务依赖 | 8082 未启动时范文检索不可用（不影响评分主链路） | health 端点已暴露状态；嵌入失败需在 embedding_service 内静默返回空列表 |
| 8 | ✅ §12 #2 关闭情况 | 「AI 未接入」对**前端↔Python** 链路已关闭；Java 链路随本页 #1 修复后关闭 | — |

---

## 二十一、补充（二）任务看板（图例见 docs/util/scrum.md）

| 任务 | 负责人 | 状态 |
|---|---|---|
| FastAPI 路由层（grade / grade-image / health）+ Pydantic Schema | QianLi071 | ✅ 已完成 |
| Prompt 单一数据源 registry + IELTS next_actions 硬约束 | QianLi071 | ✅ 已完成 |
| llama.cpp 迁移（LlamaClient 文本/多模态/embed）+ 启动脚本（mmproj 条件挂载） | QianLi071 | ✅ 已完成 |
| 统一响应契约（ApiResponse + 23 字段）与 CORS 白名单 | QianLi071 | ✅ 已完成 |
| Java PythonAiGateway（@Primary 路由桥）+ WritingScorer 改 payload + yml 配置 | QianLi071 | 🔄 进行中（待 #1 解包修复后端到端验收） |
| IELTS 练习页真实 AI 接入 + mock 降级 + 离线标签 | QianLi071 | ✅ 已完成 |
| onboarding 测评 Assessment.scoreWritingRemote + loading 文案 | QianLi071 | ✅ 已完成 |
| 图片上传识别评分前端流程（FileReader / details 核对 / 字数隐藏恢复） | QianLi071 | 🔄 进行中（待 mmproj 实测） |
| 前端接口文档 docs/writing-frontend-api.md | QianLi071 | ✅ 已完成 |
| Java 端 ApiResponse.data 解包 + snake_case 映射（第二十章 #1） | QianLi071 | 📋 待办 🔥 |
| RestTemplate 超时 / 失败降级配置（第二十章 #2） | QianLi071 | 📋 待办 |
| 多模态端到端验收（test_vision.py + 浏览器） | QianLi071 | 📋 待办 🔥 |
| PracticeController + WritingService REST 化（沿用 §13） | QianLi071 | 📋 待办 🔥 |
| PlanGenerator 排程引擎实现（沿用 §13） | QianLi071 | 🔄 进行中 |
| SpeakingScorer / WeaknessAnalyzer / PlanNarrator / 定时任务 | （认领） | 📋 待办 |

---

*本文档随迭代持续更新。如有疑问或发现文档与代码不一致，以代码为准并同步更新本文档。*
