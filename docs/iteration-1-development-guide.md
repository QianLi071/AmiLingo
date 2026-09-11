# AmiLingo Platform · 迭代 1 开发文档

> 版本：迭代 1 · 2026-09-12
> 读者：全体后端开发人员
> 目的：统一对项目架构、业务链路、共享基础设施的理解；明确**哪些能力已经封装好必须复用、哪些是禁止重复实现**的，避免各人各写一套。

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
│  common/       通用层（DTO / 异常 / Util / Config）—— 全员共享 │
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
│   └── user/            # AuthController, UserController
├── common/              # 全员共享（重点！禁止在别处重复实现）
│   ├── config/          # SecurityConfig, RedisConfig, JwtAuthenticationFilter, login/
│   ├── dto/             # ApiResponse, UserDTO, request/*
│   ├── exceptions/      # 异常体系
│   └── util/            # JwtUtil, Snowflake
├── component/           # 业务组件
│   ├── abstracts/       # IUserService 等业务接口
│   ├── caching/         # UserCache, EmailCodeCache
│   ├── login/           # EmailPasswordStrategy
│   ├── redis/           # AbstractCacheEngine, ICacheable, RedisService, RedisDistributedLock
│   ├── repository/      # UserRepository
│   ├── services/user/   # UserService, MailService
│   ├── BaseLoginStrategy, ILoginStrategy, LoginStrategyFactory, LoginAttemptService
├── entity/user/         # User
├── module/              # 业务领域占位（迭代2+）：career/contest/interest/lang/pet/user
├── infra/               # 基础设施占位（迭代2+）：ai/audit
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
| `services/user/UserService` | 用户 CRUD + 登录校验，实现 `IUserService` | 新业务 Service 放 `services/<域>/`，并在 `abstracts/` 定义接口 |
| `services/user/MailService` | 邮件发送 + 验证码缓存读写 | 邮件相关统一走这里，不要在别处直接用 JavaMailSender |
| `repository/UserRepository` | JPA 数据访问 | 新实体的 Repository 放 `repository/`，继承 `JpaRepository` |
| `caching/UserCache` / `EmailCodeCache` | 继承 `AbstractCacheEngine` 的具体缓存 | 新缓存继承 `AbstractCacheEngine<T, ID>`，实现三个抽象方法 |
| `BaseLoginStrategy` / `ILoginStrategy` / `LoginStrategyFactory` | 登录策略模板 | 新登录方式继承 `BaseLoginStrategy`，在 `LoginStrategyConfig` 注册 Bean |
| `LoginAttemptService` | 登录失败计数 + 分布式锁 | **不要自己写失败计数**，复用此 Service |
| `redis/RedisService` | Redis 基础操作封装 | 所有 Redis 操作走这里，不要在业务代码中直接注入 `RedisTemplate` |
| `redis/RedisDistributedLock` | 基于 Lua 的分布式锁 | 需要分布式互斥时注入此 Bean，不要自己 `setIfAbsent` |
| `redis/AbstractCacheEngine` | 缓存模板（序列化/反序列化/回源） | 新缓存继承它 |

### 3.3 `common/` — 通用层（**全员共享，禁止重复实现**）

详见第四章。

### 3.4 `entity/` — 实体层

| 实体 | 表 | 关键字段 |
|---|---|---|
| `User` | `users` | `id`(Long, 雪花ID), `username`, `passwordHash`, `email`, `createdAt` |

---

## 四、关键共享 Util & 基础设施（防重复造轮子 · 重点）

以下能力**已经封装完毕，所有开发人员必须直接注入使用，严禁自行实现同类逻辑**。

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

### 4.4 `RedisService` — Redis 操作封装

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

### 4.5 `RedisDistributedLock` — 分布式锁

**位置**：`component/redis/RedisDistributedLock.java`

| 方法 | 用途 |
|---|---|
| `tryLock(key, lockTtl)` | 尝试一次加锁，立即返回 token 或 null |
| `lock(key, lockTtl, waitTime)` | 自旋等待加锁，超时返回 null |
| `unlock(key, token)` | Lua 原子释放（token 匹配才删） |

**强制**：需要分布式互斥（如登录计数、库存扣减）时注入此 Bean，**不要**自己用 `setIfAbsent` + `delete` 实现（会有锁误删风险）。

### 4.6 `AbstractCacheEngine` + `ICacheable` — 缓存模板

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

### 4.7 `ApiResponse` — 统一响应体

**位置**：`common/dto/ApiResponse.java`

```java
ApiResponse.ok(data)                    // { success:true, message:"OK", data }
ApiResponse.ok("自定义消息", data)
ApiResponse.error("错误描述")           // { success:false, message, data:null }
```

**强制**：所有接口返回体统一用 `ApiResponse`（文件上传等特殊场景除外）。`AuthController` 中目前混用了 `Map.of(...)`，新代码请统一到 `ApiResponse`。

### 4.8 异常体系 + `GlobalExceptionHandler`

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

### 4.9 `PasswordEncoder` — 密码哈希

**位置**：`common/config/security/SecurityConfig.java`（`BCryptPasswordEncoder` Bean）

**强制**：密码哈希/比对一律注入 `PasswordEncoder`，**不要**自己 `MessageDigest` 或存明文。

---

## 五、强制开发规范

### 5.1 包与命名

| 规则 | 说明 |
|---|---|
| Controller 放 `api/<域>/` | 如 `api/user/`、未来 `api/career/` |
| Service 放 `component/services/<域>/` | 接口放 `component/abstracts/` |
| Repository 放 `component/repository/` | 统一管理 |
| 缓存类放 `component/caching/` | 继承 `AbstractCacheEngine` |
| 实体放 `entity/<域>/` | |
| DTO 放 `common/dto/`，请求 DTO 放 `common/dto/request/` | |
| 异常放 `common/exceptions/` | 继承 `ApiException` 或 `RuntimeException` |
| 配置类放 `common/config/` | |

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
| `module/` 与 `infra/` 均为 `Placeholder` | 迭代 2 开始填充业务领域。 |

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

*本文档随迭代持续更新。如有疑问或发现文档与代码不一致，以代码为准并同步更新本文档。*
