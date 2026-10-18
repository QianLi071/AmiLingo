# 就业岗位发现模块

## 1. 模块目标

该模块面向寻找中国内地、香港及澳门就业或实习机会的学生。系统只整理公开招聘信息并跳转企业官方页面，不代替企业发布岗位、不代收简历，也不自动提交申请。

## 2. 用户链路

```text
进入 /career/
  → 输入关键词或选择地区、岗位类型
  → GET /api/v1/career/jobs
  → 浏览岗位卡片与详情
  → 可选：填写个人专业、技能与经历，生成可解释匹配分析
  → 点击“前往企业官方页面”完成申请
```

## 3. 核心文件

- `scripts/career_job_crawler.py`：低频读取配置好的公开招聘页面，统一输出 JSON。
- `scripts/update_career_jobs.ps1`：安全更新岗位数据；采集失败或结果为空时保留旧文件。
- `src/main/resources/career/job-postings.crawled.json`：采集结果及采集时间。
- `JobPostingSeedLoader`：应用启动时按 `officialUrl` 同步数据库；同一链接更新而不重复新增。
- `JobPostingController`：岗位列表、筛选和详情接口。
- `src/main/resources/static/career/`：无需构建工具的岗位发现演示页。
- `profile.html`、`profile-store.js`：浏览器本地求职资料和字段复制板；不调用后端保存个人资料。
- `applications.html`、`application-store.js`：浏览器本地投递记录；支持从岗位列表加入、手动添加、筛选和更新进度。

## 4. 更新岗位数据

在仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_career_jobs.ps1
```

可限制每个来源保留的岗位数量：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_career_jobs.ps1 -LimitPerSource 5
```

检查生成的 JSON 后重启 Java 应用，导入器会新增新链接并刷新已有链接对应的岗位。数据源网页结构发生变化时，应修改对应解析函数并先运行 `python .\scripts\test_career_job_crawler.py`。

## 5. 接口

- `GET /api/v1/career/jobs`：列出有效岗位。
- `GET /api/v1/career/jobs?region=HONG_KONG`：按地区筛选。
- `GET /api/v1/career/jobs?jobType=INTERNSHIP`：按岗位类型筛选。
- `GET /api/v1/career/jobs?keyword=engineer`：按公司、岗位或行业搜索。
- `GET /api/v1/career/jobs/{id}`：读取岗位详情。
- `POST /api/v1/career/jobs/{id}/fit-assessments`：比较个人信息与岗位文本，返回匹配依据、待核对项和准备建议。
- `POST /api/v1/career/risk-assessments`：根据用户已知的客观信息生成风险信号与核实清单。

统一返回 `ApiResponse<T>`。无明确截止日期时 `applicationDeadline` 为 `null`，前端展示“以官网为准”。

风险检查器不会联网查询企业征信，也不会输出“骗子公司”等事实性定性。高权重信号集中在向求职者收费、正式录用前索取敏感资料等招聘行为；公司成立时间、人员规模和登记资本只作为低权重背景信息。

匹配分析不会预测录用概率。参考分只汇总地区、岗位类型、专业方向、技能关键词和经历佐证等可解释维度；公开摘要不足时会降低信息质量等级，并提示用户粘贴官网完整职位说明。岗位 ID 在 API 中以字符串返回，避免雪花 ID 在 JavaScript 中丢失精度。

求职资料助手将常用网申字段保存在当前浏览器的 `localStorage` 中，保存键为 `amilingo.career.profile.v1`。用户必须主动点击复制并在企业页面粘贴、核对；系统不读取外部招聘网页，也不自动填写或提交。页面明确禁止保存证件号码、银行卡、验证码、密码及证件扫描件等高敏感信息，并提供一键清除本地资料。

轻量投递管理使用本地键 `amilingo.career.applications.v1`，允许用户手动记录 `待投递 → 已投递 → 笔试 → 面试 → Offer` 等状态，也保留已入职、已拒绝和已放弃结果。岗位列表按 `sourceJobId` 去重并保留用户已填写的进度和备注；外部岗位可手动添加。该功能不监听企业网站状态，也不自动投递。

## 6. 数据与合规边界

- 只访问无需登录的企业公开招聘页，并保持低频、串行请求。
- 不绕过验证码、访问控制或反自动化措施。
- 保存岗位摘要、来源和官方链接，不复制整站内容。
- 不推测截止日期、薪资或资格条件。
- 对外展示时提示用户回到企业官网核实最新信息。
