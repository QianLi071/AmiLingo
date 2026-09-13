# AmiLingo

### 如何运行？
```bash
mvn clean package -DskipTests
```
确保安装和运行了：
* Redis 8.0.x
* MySQL
* Java 21+  

```bash
java -DDATABASE_PASSWORD=你的数据库密码 -DDATABASE_HOST=你的数据库主机URL -DDATABASE_USERNAME=你的数据库用户名 -DREDIS_HOST=你的Redis主机 -DREDIS_PASSWORD=你的Redis密码 -DJWT_SECRET=不少于32字符的jwt密钥 -jar target/build-0.0.1-SNAPSHOT.jar
```
### 贡献

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
| ...            |        |     |     |     |                                                                           |                                                 |
