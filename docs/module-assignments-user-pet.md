# AmiLingo 平台 — User / Pet 模块 Assignment & Task 清单

> 生成日期：2026-10-31
> 对应源码模块：`module/user`、`module/pet`

---

## 一、User 模块（用户域）

### Assignment 概述

用户域核心模块，承载以下四大职责：

1. **认证入口**：邮箱密码登录 + 手机号验证码登录（策略模式扩展）
2. **登录安全验证**：密码校验通过后评估"常用位置与设备"指纹（IP + UA/Accept-Language/Sec-CH-UA/客户端 deviceId），受信任直接登录，陌生环境触发邮箱验证码二次验证（一次性 challenge、fail-closed）
3. **账号绑定**：已登录用户可绑定/换绑邮箱、手机号（各自绑定接口共用统一验证码模块，场景隔离防跨场景重放）
4. **个人资料管理**：已登录用户可修改用户名、昵称、头像、个人介绍（PATCH 语义）

**跨模块依赖**：`component/ILoginStrategy`、`component/login/LoginSecurityService`、`component/login/PhoneCodeStrategy`、`component/services/{MailService, SmsService, VerificationCodeService}`、`component/caching/UserCache`、`common/util/{Snowflake, StringUtil, SecurityUtil}`

---

## Task 清单

### Task 1: 用户注册（邮箱密码注册）

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/auth/portal/register` |
| **鉴权** | 无（公开） |
| **限流** | `@RateLimit(maxRequests = 2)` |
| **Controller 文件** | `AuthController.java` |

**请求体** (`RegisterRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | String | ✅ | 邮箱地址 |
| `name` | String | ✅ | 用户名 |
| `password` | String | ✅ | 明文密码 |

**响应**：

| 场景 | HTTP | 说明 |
|---|---|---|
| 成功 | `201 Created` | 响应体为 `UserDTO.fromUser()`（本人视角，完整邮箱） |
| 密码不合法 | `400 Bad Request` | 仅大小写字母+数字，8-32 位 |
| 用户名/邮箱冲突 | `409 Conflict` | 数据库唯一约束由 `GlobalExceptionHandler` 捕获 |

**实现链路**：
```
AuthController.register
  → UserService.register                              (Snowflake 生成用户 ID)
    → passwordEncoder.encode(password)
    → UserRepository.save(user)                       (真实落库)
    → bestEffortCache → UserCache.setUserEmailKey    (邮箱→userId 二级缓存，Redis 异常降级不影响主流程)
  → JwtUtil.generateToken                             (签发 JWT)
  → JwtUtil.produceCookie(Duration.ofDays(7), token) → Set-Cookie: access_token=<jwt>; HttpOnly; Path=/
```

---

### Task 2: 统一登录入口（策略工厂路由）

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/auth/portal/login` |
| **鉴权** | 无（公开） |
| **限流** | `@RateLimit`（默认 5 次/分钟/IP） |
| **Controller 文件** | `AuthController.java` |

**请求体** (`LoginRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `loginType` | String | ✅ | 登录策略标识 |
| `credential` | Map\<String, Object\> | ✅ | 策略专属参数 Map |

**`loginType` 路由表**：

| loginType | 策略实现 | credential 字段 | 设备信任检查 |
|---|---|---|---|
| `EMAIL_PASSWORD`（默认） | `EmailPasswordStrategy` | `email`, `password`, 可选 `deviceId` | ✅ 指纹比对 |
| `PHONE_CODE` | `PhoneCodeStrategy` | `phone`, `code` | ❌ 短信本身为持有因子 |

**返回** (`LoginResultDTO` record)：

| 场景 | verificationRequired | challengeId | maskedEmail | user |
|---|---|---|---|---|
| 受信任设备直接登录 | `false` | null | null | ✅ `UserDTO.fromUser` |
| 陌生环境二次验证 | `true` | ✅ 256-bit | ✅ 脱敏 | null |

> ⚠️ 二次验证场景 **不签发任何 Cookie**，客户端需引导用户完成邮箱验证码验证后调用 `/login/verify`

**实现链路**：
```
AuthController.login
  → LoginStrategyFactory.getStrategy(loginType)
  → strategy.authenticate(credential)                // 策略内完成凭证校验
  → if strategy.requiresDeviceTrustVerification():
      LoginSecurityService.evaluate(user, request, deviceId)
        → RequestFingerprintUtil.computeLoginFingerprint (6 标签 SHA-256)
        → Redis 比对 login:trust:{userId} 有界 List
        → 未命中 → 生成 challengeId (Base64URL) + MailService.sendLoginStepUpCode
      → challenge != null → 返回 LoginResultDTO.challenge(...)  （不发 Cookie）
  → issueAuthCookie → JwtUtil.produceCookie            // 仅受信任环境或 PHONE_CODE 签发 Cookie
  → return LoginResultDTO.direct(user)
```

**安全设计要点**：
- 受信任设备 List 有界（最多 5 条）+ 滚动 TTL（默认 30 天），由 Lua 脚本 `rpush + ltrim + expire` 原子维护
- challengeId = 256-bit `SecureRandom`（32 字节 → Base64URL），不可预测
- challenge 载荷：`userId|fingerprint`，格式非法一律按过期处理（防探测）
- Redis 异常 **fail-closed**：不降级为"当作受信任设备"
- 邮箱投递失败 **fail-closed**：登录终止，挑战随 TTL 自然作废

---

### Task 3: 登录二次验证（提交邮箱验证码）

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/auth/portal/login/verify` |
| **鉴权** | 无（public，但需 challengeId） |
| **限流** | `@RateLimit(maxRequests = 10)` |
| **Controller 文件** | `AuthController.java` |

**请求体** (`LoginVerifyRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `challengeId` | String | ✅ | 登录入口返回的 challengeId |
| `code` | String | ✅ | 邮箱验证码 |

**响应**：

| 场景 | HTTP | 说明 |
|---|---|---|
| 验证成功 | `200 OK` | `Set-Cookie: access_token=<jwt>` + `LoginResultDTO.direct(user)` |
| challenge 不存在/过期 | `401 Unauthorized` | "登录验证会话不存在或已过期" |
| 验证码错误 | `400 Bad Request` | "验证码错误或已过期" |
| 错误次数达上限 | `401 Unauthorized` | 整个挑战被作废（需重新登录） |

**实现链路**：
```
LoginSecurityService.verifyChallenge(challengeId, code)
  → loadPending → Redis.get("login:pending:{id}")  → parsePending(userId|fingerprint)
  → MailService.validateLoginStepUpCode(email, code)  (VerificationScene.EMAIL_LOGIN_STEP_UP)
    → VerificationCodeService.verify(...)  // 常量时间比较 + Lua 原子一次性消费 + 防爆破计数
  → Redis.delete("login:pending:{id}", "login:pending:fail:{id}")
  → recordTrustedDevice(userId, fingerprint)  // 注意：记发起登录时的指纹，不是本次验证请求的指纹
  → 返回 User → AuthController.issueAuthCookie
```

**防爆破**：单个挑战验证码错误 ≥ 5 次，挑战被整体删除（fail-closed），必须重新走密码登录；计数 key `login:pending:fail:{challengeId}` 与挑战同 TTL。

---

### Task 4: 重发登录二次验证验证码

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/auth/portal/login/resend` |
| **鉴权** | 无（public，但需 challengeId） |
| **限流** | `@RateLimit(maxRequests = 3)` |
| **Controller 文件** | `AuthController.java` |

**请求体** (`ChallengeResendRequest`)：

| 字段 | 类型 | 必填 |
|---|---|---|
| `challengeId` | String | ✅ |

**安全保护**：`VerificationCodeService` 的 `resend-cooldown-seconds`（默认 60s）SETNX 冷却，防止短信轰炸；冷却内调用直接返回"发送过于频繁"。

---

### Task 5: 发送手机号登录验证码

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/auth/portal/phone/send` |
| **鉴权** | 无（公开） |
| **限流** | `@RateLimit(maxRequests = 3)` |
| **Controller 文件** | `AuthController.java` |

**请求体** (`PhoneCodeSendRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | String | ✅ | 中国大陆手机号（格式 `^1[3-9]\d{9}$`） |

**实现链路**：
```
AuthController.sendPhoneLoginCode
  → SmsService.sendLoginCode(phone)
    → VerificationCodeService.issue(VerificationScene.PHONE_LOGIN, phone)
    → SmsSender.send(phone, code, "PHONE_LOGIN")  // SPI 接口，可切换实现
      LoggingSmsSender（开发桩，app.sms.provider=log 时生效，严禁生产）
      其他：阿里云/腾讯云短信等实现
    → 投递失败 → VerificationCodeService.invalidate 回滚作废（fail-closed）
```

---

### Task 6: 退出登录

| 项 | 值 |
|---|---|
| **接口** | `DELETE /api/v1/auth/portal/logout` |
| **鉴权** | `@RequireAuth` |
| **限流** | `@RateLimit` |
| **Controller 文件** | `AuthController.java` |

**实现**：写入空 Cookie（`maxAge=0`）覆盖 `access_token` 与 `user_id`，使浏览器删除。不做服务端 token 黑名单（无状态 JWT）。

---

### Task 7: 用户列表（分页）

| 项 | 值 |
|---|---|
| **接口** | `GET /api/v1/users?p={page}&n={size}` |
| **鉴权** | 需登录（`SecurityUtil.requireAuthentication()`） |
| **限流** | 无 |
| **Controller 文件** | `UserController.java` |

**Query 参数**：

| 字段 | 类型 | 默认 | 约束 |
|---|---|---|---|
| `p` | int | 0 | page ≥ 0 |
| `n` | int | 10 | 0 < size < 20 |

**响应**：`List<UserDTO>`，他人视角（`UserDTO.publicFromUser`）——邮箱和手机号均脱敏，**严禁返回 User 实体（含 passwordHash）**。

**实现链路**：
```
UserController.getUsers
  → UserService.getAllUsers(Pageable.ofSize(size).withPage(page))
    → UserRepository.findAll(page)  (JPA 分页)
  → .stream().map(UserDTO::publicFromUser).toList()
```

---

### Task 8: 当前用户资料（本人视角）

| 项 | 值 |
|---|---|
| **接口** | `GET /api/v1/users/me` |
| **鉴权** | `@RequireAuth` |
| **限流** | 无 |
| **Controller 文件** | `UserController.java` |

**响应**：`UserDTO.fromUser()`（本人视角，完整邮箱 + 脱敏手机号），字段：`id, email, name(=username), phone, nickname, avatarUrl, bio, createdAt`

**实现链路**：
```
UserController.me
  → requireCurrentUser
    → SecurityUtil.getCurrentUserId()           // JwtAuthenticationFilter 写入 request attribute
    → UserService.getUserById(userId)
      → UserCache.getCachedById(userId)         // Redis 优先
      → miss → UserRepository.findById          // DB 回退 + bestEffortCache 回填缓存
```

---

### Task 9: 修改当前用户资料（PATCH 语义）

| 项 | 值 |
|---|---|
| **接口** | `PATCH /api/v1/users/me` |
| **鉴权** | `@RequireAuth` |
| **限流** | `@RateLimit(maxRequests = 10)` |
| **Controller 文件** | `UserController.java` |

**请求体** (`UpdateProfileRequest` record)：

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `username` | String | ❌ | `^[A-Za-z0-9_\u4e00-\u9fa5]{2,32}$` |
| `nickname` | String | ❌ | ≤ 32 字符 |
| `avatarUrl` | String | ❌ | 仅允许 `http://` / `https://` / `/` 开头，≤ 512，无空格 |
| `bio` | String | ❌ | ≤ 500 字符 |

> PATCH 语义：null 字段不修改，仅提交的字段会被更新

**安全设计要点**：
- avatarUrl 白名单校验：阻止 `javascript:` / `data:` 等危险协议（防 XSS）
- username 全局唯一校验，冲突抛 `409 Conflict`
- 所有修改经 `saveUser → bestEffortCache` 同步刷新 Redis 缓存

---

### Task 10: 绑定/换绑邮箱（两步流程）

**Step 1 — 发送验证码**
| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/users/me/email/bind` |
| **鉴权** | `@RequireAuth` |
| **限流** | `@RateLimit(maxRequests = 3)` |

请求体：`{ "email": "..." }`

```
UserController.sendBindEmailCode
  → MailService.sendEmailBindingCode(email)     // VerificationScene.EMAIL_BIND
    → VerificationCodeService.issue(...)
    → 60s SETNX 冷却保护
```

**Step 2 — 提交验证完成绑定**

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/users/me/email/bind/confirm` |
| **鉴权** | `@RequireAuth` |
| **限流** | `@RateLimit(maxRequests = 10)` |

请求体：`EmailValidateRequest { email, code }`

```
UserController.confirmBindEmail
  → MailService.validateEmailCode(email, code)    // VerificationScene.EMAIL_BIND 常量时间比较 + 原子消费
  → UserService.bindEmail(userId, email)
    → EmailValidationService.normalize(email)     // 格式校验 + 规范化（小写 trim）
    → 幂等：新邮箱 == 当前邮箱 → 直接返回
    → 冲突检查：该邮箱已被其他账号绑定 → 409 Conflict
    → saveUser (DB + Redis)
    → ⚠️ bestEffortCache → UserCache.deleteUserEmailKey(oldEmail)   // 清旧缓存映射！
    → bestEffortCache → UserCache.setUserEmailKey(user)             // 写新映射
```

> **安全关键**：换绑后必须清除旧邮箱的 Redis 二级缓存映射（`deleteUserEmailKey`），否则旧邮箱仍可通过缓存命中该用户造成账号接管风险。

---

### Task 11: 绑定/换绑手机号（两步流程）

**Step 1 — 发送验证码**
| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/users/me/phone/bind` |
| **鉴权** | `@RequireAuth` |
| **限流** | `@RateLimit(maxRequests = 3)` |

请求体：`BindPhoneRequest { phone }`

**Step 2 — 提交验证完成绑定**

| 项 | 值 |
|---|---|
| **接口** | `POST /api/v1/users/me/phone/bind/confirm` |
| **鉴权** | `@RequireAuth` |
| **限流** | `@RateLimit(maxRequests = 10)` |

请求体：`PhoneValidateRequest { phone, code }`

```
UserService.bindPhone(userId, phone)
  → StringUtil.isValidChinaMobile(phone)          // ^1[3-9]\d{9}$
  → 幂等检查
  → 冲突检查（其他账号已绑定 → 409）
  → saveUser (DB + Redis)
```

> 绑定接口共用 `VerificationCodeService` 的 **PHONE_BIND** 场景，与登录场景（PHONE_LOGIN）相互隔离，防止跨场景验证码重放。

---

### Task 12: 修改密码

| 项 | 值 |
|---|---|
| **接口** | `PATCH /api/v1/users/password` |
| **鉴权** | `@RequireAuth` |
| **限流** | 无 |
| **Controller 文件** | `UserController.java` |

**请求体** (`ChangePasswordRequest` record)：`{ password, passwordConfirm }`

| 校验规则 | 失败返回 |
|---|---|
| password/passwordConfirm 非空 | 400 "密码不能为空" |
| password == passwordConfirm | 400 "确认密码不匹配" |
| 长度 8-32 | 400 "密码长度要求8到32位之间" |
| 仅 `[A-Za-z0-9]+` | 400 "密码只能包含大小写字母和数字" |

```
UserService.changePassword(password, userId)
  → getUserById → UserNotFoundException 404
  → passwordEncoder.encode(password) → user.setPasswordHash
  → saveUser → DB + Redis
```

---

## User 模块实体（Entity）

| 类 | 表名 | 主键 | 核心字段 | 说明 |
|---|---|---|---|---|
| `User` | `users` | `user_id`（Snowflake） | username, email(unique), phone(unique via uk_user_phone), nickname, avatarUrl, bio, passwordHash, createdAt | 核心用户实体 |
| `LearningRecord` | `learning_records` | `record_id`（Snowflake） | user(FK), zone(enum: LANG/CONTEXT/CAREER/INTEREST), taskName, score, durationMinutes, createdAt | 学习记录（懒加载关联 User） |
| `UserStat` | `user_stats` | `user_id`（= User.user_id） | totalExp, totalDays, currentStreak, badges(List\<Badge\>) | 用户统计（连签、总经验、徽章） |
| `Badge` | `badges` | `badge_id` | name, description, value | 成就徽章 |

## User 模块仓库（Repository）

| 类 | 父接口 | 自定义查询 |
|---|---|---|
| `UserRepository` | `JpaRepository<User, Long>` | `findUserById`, `findUserByUsername`, `findUserByEmail`, `findUserByPhone` |
| `LearningRecordRepository` | `JpaRepository<LearningRecord, Long>` | `findByUserId` |

## User 模块服务（Service）

| 类 | 职责 | 关键依赖 |
|---|---|---|
| `UserService`（实现 `IUserService`） | 注册/登录/资料/绑定/密码 修改 | `UserRepository`, `UserCache`, `PasswordEncoder`, `EmailValidationService`, `SecureRandom` |
| `UserInfoService` | 预留（当前空类） | — |

## User 模块 Redis Key 设计

| Key 模式 | 类型 | TTL | 用途 |
|---|---|---|---|
| `user:{userId}` | String（JSON） | — | 用户实体缓存 |
| `useremail{email}` | String（userId） | — | 邮箱 → userId 二级映射 |
| `verify:code:{scene}:{target}` | String（验证码） | 300s | 验证码（场景隔离） |
| `verify:fail:{scene}:{target}` | String（计数） | 300s | 验证码失败计数 |
| `verify:cooldown:{scene}:{target}` | String（NX） | 60s | 发送冷却 |
| `login:pending:{challengeId}` | String（userId\|fingerprint） | 300s | 登录二次验证挑战 |
| `login:pending:fail:{challengeId}` | String（计数） | 300s | 挑战失败计数 |
| `login:trust:{userId}` | List（指纹） | 30d 滚动 | 受信任设备指纹（有界 ≤5） |

## 登录指纹（`RequestFingerprintUtil`）

6 个标签按 `\n` join 后 SHA-256 hex 摘要：

1. **IP**（X-Forwarded-For 取最左 → X-Real-IP → remoteAddr）
2. **User-Agent**
3. **Accept-Language**
4. **Sec-CH-UA**
5. **Sec-CH-UA-Platform**
6. **客户端 deviceId**（来自 credential 可选参数，建议 localStorage 持久化）

---

# 二、Pet 模块（宠物域）

## Assignment 概述

宠物养成子域：每个用户绑定一只虚拟宠物，记录等级/经验/心情/进化阶段。宠物经验在主业务链路（如学习完成）中被**尽力**累加——不存在或出错均静默跳过，不影响主业务返回。

**跨模块依赖**：`component/ILoginStrategy`（无直接依赖，登录不涉及宠物）、`component/caching/PetCache`

---

## Task 清单

### Task 1: 查询当前用户宠物状态

| 项 | 值 |
|---|---|
| **接口** | `GET /api/v1/pets/status` |
| **鉴权** | 需登录（Controller 内调用 `SecurityUtil.getCurrentUserId()`） |
| **限流** | 无 |
| **Controller 文件** | `PetController.java` |

**响应** (`ApiResponse<PetStatusDTO>`)：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 宠物名称 |
| `level` | Long | 当前等级 |
| `exp` | Long | 当前经验值 |
| `expToNext` | Long | 距下一级还需经验（由 `PetUtil.calcExpToNextLevel` 计算，每 500 exp 升一级） |
| `evolution` | `Pet.Stage` | 进化阶段：`EGG` → `BABY` → `ADULT` → `LEGEND` |
| `mood` | `Pet.Mood` | 心情：`HAPPY` / `SAD` / `NEUTRAL` |

**实现链路**：
```
PetController.checkPetStatus
  → SecurityUtil.getCurrentUserId()               // 从 request attribute 取 JWT 中的 userId
  → PetService.getPetById(userId)                 (userId 即 pet_user_id，一对一)
    → PetCache.getCachedById(userId)               // Redis 优先
    → miss → PetRepository.findById(userId)         // DB 回退 + bestEffortCache 回填
  → PetStatusDTO.fromPet(pet)                      // 实体 → DTO
```

> 若用户尚无宠物（DB 无记录），`pet` 为 null，DTO 各字段可能为空（由上层判断 UI 展示"去创建"引导）。

---

### Task 2: 宠物经验累加（内部调用，无 HTTP 接口）

| 项 | 值 |
|---|---|
| **方法** | `PetService.grantExperience(Long userId, long exp)` |
| **调用方** | 学习任务完成、打卡等业务（当前尚未在其他 Controller 中集成） |
| **降级策略** | **全吞异常**：try-catch 包裹，不存在/出错均静默跳过，绝不影响主业务链路 |

**实现**：
```
grantExperience(userId, exp)
  → pet = getPetById(userId)    // null 直接 return
  → pet.setExperience(exp + pet.getExperience())
  → pet.setLevel(newExp / 500)  // 每 500 exp 升 1 级
  → PetCache.cache → PetRepository.save
  → 任意异常 → catch 后静默吞掉（降级）
```

---

## Pet 模块实体（Entity）

| 类 | 表名 | 主键 | 核心字段 | 说明 |
|---|---|---|---|---|
| `Pet` | `pets` | `pet_user_id`（= User.user_id，一对一） | name(unique), experience(exp), level, stage(Stage enum), mood(Mood enum, 默认 HAPPY), lastFedAt | 虚拟宠物（用户→宠物一对一） |

**枚举**：
- `Stage`：`EGG` → `BABY` → `ADULT` → `LEGEND`（进化阶段）
- `Mood`：`HAPPY` / `SAD` / `NEUTRAL`（心情）

## Pet 模块仓库（Repository）

| 类 | 父接口 | 自定义查询 |
|---|---|---|
| `PetRepository` | `JpaRepository<Pet, Long>` | `findPetByName(String name)` |

## Pet 模块服务（Service）

| 类 | 实现接口 | 方法 | 降级策略 |
|---|---|---|---|
| `PetService` | `IPetService` | `savePet(Pet)`：缓存优先、Redis 异常降级落 DB | catch CacheException 后直接 save |
| | | `getPetById(Long)`：缓存优先、miss 回查 DB + 回填缓存 | catch CacheMissedException 后查 DB |
| | | `grantExperience(Long userId, long exp)`：尽力累加经验并按 500/级升档 | **全吞异常**（catch(Exception)），不影响主链路 |

**持久化顺序约定**：所有 Pet 写操作遵循"缓存优先 + DB 兜底"——先写 Redis，Redis 不可用时直接落 DB。读操作"缓存优先 + DB 回退 + bestEffortCache 回填"。

---

# 三、统一验证码模块（User 模块跨域依赖）

> 非独立模块，但被 User 模块 5 个 Task 共用，列为参考。

**Service**：`component/services/VerificationCodeService`

| 场景枚举 | 用途 | 消费接口 |
|---|---|---|
| `EMAIL_LOGIN_STEP_UP` | 登录二次验证 | `LoginSecurityService` |
| `EMAIL_BIND` | 邮箱绑定/换绑 | `UserService.bindEmail` |
| `PHONE_LOGIN` | 手机号验证码登录 | `PhoneCodeStrategy` |
| `PHONE_BIND` | 手机号绑定/换绑 | `UserService.bindPhone` |

**安全设计**：
- 场景隔离：不同场景的验证码 key 互不可用（Redis key 包含 `scene.name()`）
- 常量时间比较：`MessageDigest.isEqual` 防时序侧信道
- Lua 原子一次性消费：`deleteIfValueMatches` 防并发重放
- 发送冷却：SETNX key 60s，冷却内拒绝签发
- 防爆破：同目标同场景错误 ≥ 5 次锁定 + 清理
- 投递失败回滚：`invalidate` 删除已签发验证码（fail-closed）

---

# 四、应用配置参考

```yaml
app:
  security:
    login:
      challenge-ttl-seconds: 300     # 登录二次验证 challenge TTL
      max-verify-attempts: 5        # 单个 challenge 最大验证次数
      trust-ttl-days: 30             # 受信任设备滚动 TTL
      trust-max-devices: 5           # 受信任设备列表上限
    verify-code:
      ttl-seconds: 300              # 验证码有效期
      length: 6                      # 验证码长度（4-8）
      resend-cooldown-seconds: 60    # 发送冷却
      max-verify-attempts: 5        # 验证码最大尝试次数
  sms:
    provider: log                    # log=开发桩（LoggingSmsSender），严禁生产
  mail:
    from: ${MAIL_FROM:}
    from-name: ${MAIL_FROM_NAME:Amilingo}
```

---

# 五、Task → 接口速查表

| # | Task | Method | URL | 需要登录 |
|---|---|---|---|---|
| 1 | 邮箱密码注册 | POST | `/api/v1/auth/portal/register` | ❌ |
| 2 | 统一登录入口 | POST | `/api/v1/auth/portal/login` | ❌ |
| 3 | 登录二次验证 | POST | `/api/v1/auth/portal/login/verify` | ❌（需 challengeId） |
| 4 | 重发二次验证验证码 | POST | `/api/v1/auth/portal/login/resend` | ❌（需 challengeId） |
| 5 | 发送手机号登录验证码 | POST | `/api/v1/auth/portal/phone/send` | ❌ |
| 6 | 退出登录 | DELETE | `/api/v1/auth/portal/logout` | ✅ |
| 7 | 用户列表（分页） | GET | `/api/v1/users?p={page}&n={size}` | ✅ |
| 8 | 当前用户资料 | GET | `/api/v1/users/me` | ✅ |
| 9 | 修改当前用户资料 | PATCH | `/api/v1/users/me` | ✅ |
| 10a | 发送邮箱绑定验证码 | POST | `/api/v1/users/me/email/bind` | ✅ |
| 10b | 确认邮箱绑定 | POST | `/api/v1/users/me/email/bind/confirm` | ✅ |
| 11a | 发送手机号绑定验证码 | POST | `/api/v1/users/me/phone/bind` | ✅ |
| 11b | 确认手机号绑定 | POST | `/api/v1/users/me/phone/bind/confirm` | ✅ |
| 12 | 修改密码 | PATCH | `/api/v1/users/password` | ✅ |
| — | 校验邮箱验证码（工具） | POST | `/api/v1/auth/portal/validate` | ❌ |
| P1 | 查询当前用户宠物 | GET | `/api/v1/pets/status` | ✅ |
