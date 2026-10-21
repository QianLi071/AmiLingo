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
- `scripts/career_job_audit.py`：替换数据前检查必填字段、HTTPS 官方链接、重复链接、采集时间和截止日期。
- `scripts/career_job_link_checker.py`：按需低频检查官方链接并生成报告；单次失败只进入人工复核，不自动下架。
- `src/main/resources/career/job-postings.crawled.json`：采集结果及采集时间。
- `JobPostingSeedLoader`：应用启动时按 `officialUrl` 同步数据库；同一链接更新而不重复新增。
- `JobPostingController`：岗位列表、筛选和详情接口。
- `src/main/resources/static/career/`：无需构建工具的岗位发现演示页。
- `profile.html`、`profile-store.js`：浏览器本地求职资料和字段复制板；不调用后端保存个人资料。
- `applications.html`、`application-store.js`：浏览器本地投递记录；支持从岗位列表加入、手动添加、筛选和更新进度。
- `offers.html`、`offer-store.js`：浏览器本地 Offer 多维对比；由用户设置权重并为每个维度填写评分。
- `deadlines.html`、`deadline-utils.js`：汇总本地申请截止和 Offer 签约截止，计算站内倒计时。
- `data-center.html`、`local-data-backup.js`：在浏览器内选择性导出、恢复或清除求职与校园探索本地数据。

## 4. 更新岗位数据

在仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_career_jobs.ps1
```

可限制每个来源保留的岗位数量：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_career_jobs.ps1 -LimitPerSource 5
```

可以先查看已经注册的官方来源，并只更新其中一部分：

```powershell
python .\scripts\career_job_crawler.py --list-sources
powershell -ExecutionPolicy Bypass -File .\scripts\update_career_jobs.ps1 -Sources hkjc,mtr,wynn-macau
```

采集器通过 `SOURCE_REGISTRY` 管理来源。每个来源声明稳定键、显示名称、地区、公开网址、解析函数和页面类型；新增来源时需同时增加解析器离线样例测试。`--source` 可重复使用，单个来源失败只写入警告，不会中断其他来源。

更新脚本会先写入临时文件并执行审计；存在不安全链接、重复链接或结构错误时保留原数据。明确截止日期已经过去的岗位会自动设为 `active: false`。检查生成的 JSON 后重启 Java 应用，导入器会新增新链接并刷新已有链接对应的岗位。数据源网页结构发生变化时，应修改对应解析函数，并运行 `python .\scripts\test_career_job_crawler.py` 与 `python .\scripts\test_career_job_audit.py`。

需要检查当前链接时，可单独生成报告：

```powershell
python .\scripts\career_job_link_checker.py --input .\src\main\resources\career\job-postings.crawled.json --output .\career-link-report.json
```

`403`、`429`、超时和服务器错误不等于岗位失效，只提示人工回到企业网站复核；只有明确的 `404` 或 `410` 才归类为未找到，也不会自动修改岗位状态。

## 5. 接口

- `GET /api/v1/career/jobs`：列出有效岗位。
- `GET /api/v1/career/jobs?region=HONG_KONG`：按地区筛选。
- `GET /api/v1/career/jobs?jobType=INTERNSHIP`：按岗位类型筛选。
- `GET /api/v1/career/jobs?keyword=engineer`：按公司、岗位或行业搜索。
- `GET /api/v1/career/jobs/{id}`：读取岗位详情。
- `GET /api/v1/career/jobs/metadata`：读取采集时间、审计时间、来源岗位数和采集警告。
- `POST /api/v1/career/jobs/{id}/fit-assessments`：比较个人信息与岗位文本，返回匹配依据、待核对项和准备建议。
- `POST /api/v1/career/risk-assessments`：根据用户已知的客观信息生成风险信号与核实清单。

统一返回 `ApiResponse<T>`。无明确截止日期时 `applicationDeadline` 为 `null`，前端展示“以官网为准”。

风险检查器不会联网查询企业征信，也不会输出“骗子公司”等事实性定性。高权重信号集中在向求职者收费、正式录用前索取敏感资料等招聘行为；公司成立时间、人员规模和登记资本只作为低权重背景信息。

匹配分析不会预测录用概率。参考分只汇总地区、岗位类型、专业方向、技能关键词和经历佐证等可解释维度；公开摘要不足时会降低信息质量等级，并提示用户粘贴官网完整职位说明。岗位 ID 在 API 中以字符串返回，避免雪花 ID 在 JavaScript 中丢失精度。

求职资料助手将常用网申字段保存在当前浏览器的 `localStorage` 中，保存键为 `amilingo.career.profile.v1`。用户必须主动点击复制并在企业页面粘贴、核对；系统不读取外部招聘网页，也不自动填写或提交。页面明确禁止保存证件号码、银行卡、验证码、密码及证件扫描件等高敏感信息，并提供一键清除本地资料。

轻量投递管理使用本地键 `amilingo.career.applications.v1`，允许用户手动记录 `待投递 → 已投递 → 笔试 → 面试 → Offer` 等状态，也保留已入职、已拒绝和已放弃结果。岗位列表按 `sourceJobId` 去重并保留用户已填写的进度和备注；外部岗位可手动添加。该功能不监听企业网站状态，也不自动投递。

Offer 对比使用本地键 `amilingo.career.offers.v1`。用户可分别设置岗位契合、成长空间、薪酬福利、地点与通勤、工作生活和稳定性的 0–5 权重，并对每个 Offer 填写 1–5 分。系统只对已经评分且权重大于 0 的维度计算加权百分制参考分，同时保留事实依据和待核对备注；排名不等于最终选择建议。

截止日期中心不新增存储，只读取本地投递记录和 Offer 中已填写的日期，按逾期、今天、7 天内、30 天内和稍后分类。倒计时按本地日历日期计算，避免时区造成的日期偏移；网页关闭后不会发送系统通知，日期仍需以企业官网或书面 Offer 为准。

本地数据中心只处理固定白名单中的 8 类 `localStorage` 数据。备份在浏览器内生成，不发送到服务器；导入时校验备份标识、版本、总大小、单类大小和内部版本，忽略未知键，并在覆盖前要求用户确认。备份文件可能包含个人信息，不应公开分享或提交到代码仓库。

## 6. 数据与合规边界

- 只访问无需登录的企业公开招聘页，并保持低频、串行请求。
- 不绕过验证码、访问控制或反自动化措施。
- 保存岗位摘要、来源和官方链接，不复制整站内容。
- 不推测截止日期、薪资或资格条件。
- 对外展示时提示用户回到企业官网核实最新信息。
