# EduExam

[![CI](https://github.com/sevensnight/eduexam/actions/workflows/ci.yml/badge.svg)](https://github.com/sevensnight/eduexam/actions/workflows/ci.yml)

在线题库与考试管理系统，包含 Vue 3 + Vite 前端和 Spring Boot + MyBatis-Plus 后端。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts
- 后端：Java 17、Spring Boot 3.5.16、Spring Security、MyBatis-Plus、JWT
- 数据库：MySQL 8.0+

## 本地启动

环境要求：Java 17、Maven 3.9+、Node.js 20.19+（或 22.12+）、MySQL 8.0+。

1. 确认 MySQL 服务正在运行，并配置 `.eduexam.local.bat` 中的数据库用户名、密码和 JWT 密钥。
2. 双击 `start_all.bat`。
3. 浏览器打开 http://localhost:5173。

首次启动前，复制 `eduexam.local.example.bat` 为 `.eduexam.local.bat`，填入本机 MySQL 密码和一个随机 JWT 密钥。示例文件中的 `APP_JWT_SECRET=CHANGE_ME` 必须替换；可以用下面的 PowerShell 命令生成 32 字节随机密钥：`[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`。`.eduexam.local.bat` 已被 Git 忽略，不会上传到远程仓库；启动脚本会自动读取它。新注册账号默认为学生，班级由管理员在用户管理中分配，不能通过注册或个人资料自报班级。

启动脚本会从项目内的 `sql/schema.sql` 检查 13 张核心表；新库或结构不完整时执行幂等初始化，已有数据库则由后端启动时的 Flyway 版本迁移自动升级。迁移会清理旧状态、孤儿关联和历史重复答题，建立外键与 `(record_id, question_id)` 唯一约束，校准题目引用次数，并为旧库补齐防止多标签页覆盖的草稿版本列。默认不会自动导入演示账号，只有在本地配置中明确设置 `EDUEXAM_SEED_DEMO=true` 且数据库为空时才导入 `sql/data.sql`。网页操作保存到 MySQL，不会改写 SQL 初始化文件。

若旧版演示题目的选项显示为 `u9009u62e9` 等文本，更新代码并重启后端后，Flyway V5 会定向修复已知的 18 道题。迁移同时精确匹配题号、题干和损坏选项，仅更新选项；不会覆盖后来修改过的题目，也不会改动学生作答、成绩或考试记录。无需清库或重新导入演示数据。两份 `data.sql` 中的选项统一保存为 UTF-8 原文，避免 SQL 字符串中的单反斜杠 Unicode 转义被 MySQL 吞掉。

密码采用 BCrypt 保存，长度为 8–72 个字符且 UTF-8 编码不超过 72 字节。若旧版本曾创建超过该上限的密码，请由管理员通过学生导入重设为符合规则的新密码。

学生答题时，草稿会同时保存在浏览器会话和 MySQL 中；网络短暂中断或页面刷新后可恢复。到达考试截止时间时，后端以截止前最后一次成功保存的服务端草稿结算，不接受截止后的新答案。

学生成功提交答卷后，系统会在同一事务中向考试创建者写入通知：含简答题的答卷标记为待批改，纯客观题答卷标记为已自动评分；截止时间自动收卷时还会向教师汇总待处理答卷。通知中心每 10 秒及窗口重新获得焦点时刷新，支持逐条已读、全部已读和业务直达：教师可从交卷通知进入批改页，学生可从考试发布通知进入答题页、从成绩通知进入结果页。

成绩批量导入应使用系统当前导出的模板。新版模板通过 `record_id` 精确定位某次考试记录，并包含每道题的得分列；旧的“只有学生编号和总分”模板不会再直接覆盖成绩，以避免重考场景下改错记录或造成总分与逐题得分不一致。

如果不想使用本地配置文件，也可以在 PowerShell 中设置环境变量后再启动：

```powershell
$env:EDUEXAM_DB_USER = 'root'
$env:EDUEXAM_DB_PASSWORD = '你的密码'
$env:APP_JWT_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:EDUEXAM_SEED_DEMO = 'true' # 仅本地空库演示时需要
.\start_all.bat
```

也可以分别运行 `start_backend.bat` 和 `start_frontend.bat`。

如果后端不在本机的 8080 端口运行，可以复制 `frontend/.env.example` 为 `frontend/.env.local`，再修改 `VITE_API_PROXY_TARGET`。`.env.local` 被 Git 忽略，不会进入提交。

## Docker 服务器部署

Linux 服务器的 Docker Compose 目录结构、启动、更新、备份和停止方法见 [deploy/README.md](deploy/README.md)。部署密钥、数据库密码、JWT 密钥和组装后的前后端产物均不会提交到 Git。

## 演示账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 教师 | `teacher1` | `teacher123` |
| 学生 | `student1` | `student123` |

这些账号只会在空库且显式启用 `EDUEXAM_SEED_DEMO=true` 时导入，不能用于生产环境。

## 配置覆盖

后端配置支持环境变量覆盖：

- `DB_USERNAME`、`DB_PASSWORD`
- `APP_JWT_SECRET`
- `DB_HOST`、`DB_PORT`、`DB_NAME`（启动脚本的内置 SQL 引导使用默认数据库名 `eduexam`）
- `DB_USE_SSL`、`DB_ALLOW_PUBLIC_KEY_RETRIEVAL`

生产环境请使用独立数据库账号、TLS 连接和随机 JWT 密钥，不要使用演示密码；生产部署不应依赖 `.eduexam.local.bat`，并应关闭 `DB_ALLOW_PUBLIC_KEY_RETRIEVAL`、启用 `DB_USE_SSL`。

考试时间统一按 `Asia/Shanghai` 的本地时间保存和解释；前端不会再把本地时间转换成带 `Z` 的 UTC 字符串。

## 前端验证

```powershell
cd frontend
npm install
npm test
npm run build
```

项目的浏览器检查脚本位于 `frontend/scripts/visual_check.py`，测试截图默认写入被 Git 忽略的 `frontend/artifacts/` 目录。

## 自动化验证

每次推送到 `main` 或向 `main` 提交 Pull Request 时，GitHub Actions 会自动完成以下检查：

- 使用 Node.js 22 安装前端锁定依赖，检查高危依赖漏洞，运行单元测试并构建生产包。
- 使用 Java 17 运行后端完整的 Maven `clean verify`，包含题目选项的真实 MySQL 导入与修复回归测试（默认转义模式及 `NO_BACKSLASH_ESCAPES` 模式）。测试只写连接内的临时表，并检查重复执行、正常数据与手工修改保护；随后用全新的 MySQL 8.4 临时数据库启动可执行 JAR，检查 `/api/health`。

CI 只连接工作流内随用随毁的 MySQL 容器，不连接本机数据库，也不读取 `.eduexam.local.bat`、本地数据库密码或本地 JWT 密钥。Dependabot 每周检查 npm、Maven 和 GitHub Actions 依赖更新；补丁和次版本更新会合并为较少的 Pull Request，主版本更新单独提交以便审查。

## Git 协作记录

本项目的克隆、功能提交、回滚和远程同步过程记录在 [GIT_WORKFLOW.md](GIT_WORKFLOW.md) 中。

完整的实践命令、提交哈希和验收结果见 [GIT_PRACTICE_REPORT.md](GIT_PRACTICE_REPORT.md)。
