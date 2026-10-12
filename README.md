# EduExam

在线题库与考试管理系统，包含 Vue 3 + Vite 前端和 Spring Boot + MyBatis-Plus 后端。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts
- 后端：Java 17、Spring Boot 3.2、Spring Security、MyBatis-Plus、JWT
- 数据库：MySQL 8.0+

## 本地启动

环境要求：Java 17、Maven 3.9+、Node.js 20+、MySQL 8.0+。

1. 确认 MySQL 服务正在运行，并配置 `.eduexam.local.bat` 中的数据库用户名、密码和 JWT 密钥。
2. 双击 `start_all.bat`。
3. 浏览器打开 http://localhost:5173。

首次启动前，复制 `eduexam.local.example.bat` 为 `.eduexam.local.bat`，填入本机 MySQL 密码和一个随机 JWT 密钥。示例文件中的 `APP_JWT_SECRET=CHANGE_ME` 必须替换；可以用下面的 PowerShell 命令生成 32 字节随机密钥：`[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`。`.eduexam.local.bat` 已被 Git 忽略，不会上传到远程仓库；启动脚本会自动读取它。新注册账号默认为学生，班级由管理员在用户管理中分配，不能通过注册或个人资料自报班级。

启动脚本会从项目内的 `sql/schema.sql` 检查数据库结构，并迁移旧的考试记录状态、清理历史重复答题后建立 `(record_id, question_id)` 唯一约束；默认不会自动导入演示账号，只有在本地配置中明确设置 `EDUEXAM_SEED_DEMO=true` 且数据库为空时才导入 `sql/data.sql`。网页操作保存到 MySQL，不会改写 SQL 初始化文件。

学生答题时，草稿会同时保存在浏览器会话和 MySQL 中；网络短暂中断或页面刷新后可恢复。到达考试截止时间时，后端以截止前最后一次成功保存的服务端草稿结算，不接受截止后的新答案。

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
npm run build
```

项目的浏览器检查脚本位于 `frontend/scripts/visual_check.py`，测试截图默认写入被 Git 忽略的 `frontend/artifacts/` 目录。

## Git 协作记录

本项目的克隆、功能提交、回滚和远程同步过程记录在 [GIT_WORKFLOW.md](GIT_WORKFLOW.md) 中。

完整的实践命令、提交哈希和验收结果见 [GIT_PRACTICE_REPORT.md](GIT_PRACTICE_REPORT.md)。
