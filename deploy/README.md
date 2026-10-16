# EduExam 生产部署

本目录用于在 Linux 服务器上通过 Docker Compose 运行 EduExam。部署由三个相互隔离的服务组成：

- `web`：Nginx，公开 80 端口，托管 Vue 生产包并转发 `/api/`。
- `backend`：Spring Boot，仅将 8080 绑定到服务器回环地址。
- `database`：MySQL 8，不发布宿主机端口，数据保存在独立 Docker 卷中。

数据库和后端使用无外网出口的 `data` 内部网络；后端与 Nginx 通过 `edge` 网络通信。这样数据库不会暴露到公网，同时 Nginx 的 80 端口可以正常发布。

Java 与 Nginx 基础镜像通过 AWS Public ECR 的 Docker Official Images 镜像地址获取，避免部署服务器的 Docker Hub 镜像代理异常影响首次启动。

## 目录结构

部署服务器上的 `/opt/eduexam` 应包含：

```text
docker-compose.yml
.env
backend/Dockerfile
backend/app.jar
frontend/index.html
frontend/assets/...
nginx/default.conf
database/01-schema.sql
database/02-data.sql
```

`.env` 必须使用随机数据库密码和至少 64 个十六进制字符的 JWT 密钥，并保持 `600` 权限。该文件已被 Git 忽略，禁止提交到远程仓库。

## 启动与检查

```bash
cd /opt/eduexam
docker compose config --quiet
docker compose up -d --build
docker compose ps
curl --fail http://127.0.0.1/api/health
```

第一次启动时，MySQL 会导入演示结构和数据；Spring Boot 随后使用 Flyway 校验并升级结构。再次启动不会重复导入。

## 更新

先在可信环境完成后端测试与前端生产构建，再替换 `backend/app.jar` 和 `frontend/`。随后执行：

```bash
cd /opt/eduexam
docker compose up -d --build backend web
curl --fail http://127.0.0.1/api/health
```

## 备份

```bash
cd /opt/eduexam
set -a
. ./.env
set +a
docker exec eduexam-db mysqldump -uroot -p"$DB_ROOT_PASSWORD" --single-transaction --routines --triggers eduexam > "eduexam-$(date +%F-%H%M%S).sql"
```

备份文件可能包含账号、答卷和成绩，不应提交到 Git。

## 停止与恢复

```bash
cd /opt/eduexam
docker compose stop
docker compose start
```

不要使用 `docker compose down -v`，该命令会删除 MySQL 数据卷。

## HTTPS

只有 IP 地址时可以通过 HTTP 完成跨网络验收。正式长期运行时应绑定域名，在外层配置 HTTPS，并将 80 端口重定向到 443。
