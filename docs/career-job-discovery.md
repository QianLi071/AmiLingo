# 就业岗位发现模块

## 1. 模块目标

该模块面向寻找中国内地、香港及澳门就业或实习机会的学生。系统只整理公开招聘信息并跳转企业官方页面，不代替企业发布岗位、不代收简历，也不自动提交申请。

## 2. 用户链路

```text
进入 /career/
  → 输入关键词或选择地区、岗位类型
  → GET /api/v1/career/jobs
  → 浏览岗位卡片与详情
  → 点击“前往企业官方页面”完成申请
```

## 3. 核心文件

- `scripts/career_job_crawler.py`：低频读取配置好的公开招聘页面，统一输出 JSON。
- `scripts/update_career_jobs.ps1`：安全更新岗位数据；采集失败或结果为空时保留旧文件。
- `src/main/resources/career/job-postings.crawled.json`：采集结果及采集时间。
- `JobPostingSeedLoader`：应用启动时按 `officialUrl` 同步数据库；同一链接更新而不重复新增。
- `JobPostingController`：岗位列表、筛选和详情接口。
- `src/main/resources/static/career/`：无需构建工具的岗位发现演示页。

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
- `POST /api/v1/career/risk-assessments`：根据用户已知的客观信息生成风险信号与核实清单。

统一返回 `ApiResponse<T>`。无明确截止日期时 `applicationDeadline` 为 `null`，前端展示“以官网为准”。

风险检查器不会联网查询企业征信，也不会输出“骗子公司”等事实性定性。高权重信号集中在向求职者收费、正式录用前索取敏感资料等招聘行为；公司成立时间、人员规模和登记资本只作为低权重背景信息。

## 6. 数据与合规边界

- 只访问无需登录的企业公开招聘页，并保持低频、串行请求。
- 不绕过验证码、访问控制或反自动化措施。
- 保存岗位摘要、来源和官方链接，不复制整站内容。
- 不推测截止日期、薪资或资格条件。
- 对外展示时提示用户回到企业官网核实最新信息。
