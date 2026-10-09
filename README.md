# EduExam

在线题库与考试管理系统，包含 Vue 3 + Vite 前端和 Spring Boot + MyBatis-Plus 后端。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts
- 后端：Java 17、Spring Boot 3.2、Spring Security、MyBatis-Plus、JWT
- 数据库：MySQL 8.0+

## 本地启动

环境要求：Java 17、Maven 3.9+、Node.js 20+、MySQL 8.0+。

1. 确认 MySQL 服务正在运行，并检查 `backend/src/main/resources/application.yml` 中的数据库用户名和密码。
2. 双击 `start_all.bat`。
3. 浏览器打开 http://localhost:5173。

启动脚本会从项目内的 `sql/schema.sql` 检查数据库结构；仅当 `eduexam.users` 为空时才导入 `sql/data.sql`。网页操作保存到 MySQL，不会改写 SQL 初始化文件。

如果 MySQL 用户名或密码不是默认值，可以在 PowerShell 中设置环境变量后再启动：

```powershell
$env:EDUEXAM_DB_USER = 'root'
$env:EDUEXAM_DB_PASSWORD = '你的密码'
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

## 配置覆盖

后端配置支持环境变量覆盖：

- `DB_HOST`、`DB_PORT`、`DB_NAME`
- `DB_USERNAME`、`DB_PASSWORD`
- `APP_JWT_SECRET`

生产环境请使用独立数据库账号和随机 JWT 密钥，不要使用演示密码。

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
