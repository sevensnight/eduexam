# Git 与远程仓库基础协作实践报告

## 一、仓库信息

- 项目名称：EduExam
- 远程仓库：<https://github.com/sevensnight/eduexam>
- 仓库可见性：Public
- 默认分支：`main`
- 实践日期：2026-10-09
- 最新维护日期：2026-10-14

远程仓库创建时已经包含项目根目录的 `README.md`，随后使用标准 `git clone` 命令创建独立的本地开发副本：

```powershell
git clone https://github.com/sevensnight/eduexam.git "D:\软件工程与UML\eduexam-git-practice"
```

上述克隆与回滚练习已经完成。2026-10-14 经所有者授权，在确认没有独有源码或本地配置后，将该练习副本移入 Windows 回收站；后续开发统一使用 `D:\软件工程与UML\eduexam`。删除重复副本不影响已保存的提交历史。

## 二、功能开发与提交历史

本次实践按照“一次提交完成一项明确工作”的原则形成了以下版本历史：

| 提交 | 类型 | 内容 |
| --- | --- | --- |
| `155e001` | 初始提交 | 上传完整 EduExam 前后端项目、SQL 和启动脚本 |
| `64e53b5` | 文档提交 | 新增 Git 协作流程说明 |
| `717d464` | 功能提交 | 增加仪表盘手动刷新和最后更新时间显示 |
| `d6ab428` | 配置提交 | 新增前端代理环境变量示例并补充使用说明 |
| `cbb4427` | 错误提交 | 故意引入 Dashboard 语法错误，用于回滚练习 |
| `c3dc5fa` | 回滚提交 | 使用 `git revert` 撤销错误提交，恢复正确版本 |
| `7252e86` | 文档提交 | 新增 Git 实践验收报告 |
| `463ec68` | 安全修复 | 限制公开注册只能创建学生账号 |
| `0910f4e` | 业务修复 | 强化考试访问控制和事务化提交 |
| `0cb8c6a` | 业务修复 | 强化考试完整性并保存学生草稿 |
| `2317130` | 启动修复 | 安全化一键启动和数据库初始化流程 |
| `bd935d1` | 综合修复 | 修复考试流程、并发和大数据量扩展问题 |
| `5718909` | CI 配置 | 新增 GitHub Actions、Dependabot 和真实数据库启动检查 |
| `e1cc982` | CI 修复 | 统一 npm 版本生成的锁文件，保证云端冷安装通过 |
| `06dfa2a` | 文档提交 | 更新 Git 实践验证结果 |
| `f3e964e` | 界面优化 | 重构统计分析界面并修复图表悬停交互 |

2026-10-14 经所有者明确授权，原 15 条项目提交的标题和正文统一改为中文。表中使用新编号，旧编号与新编号的对照见 [提交说明中文化记录](GIT_HISTORY_TRANSLATION.md)。每条旧提交的文件树、作者、原时间和父子顺序均保持不变。

前三项开发提交的命令示例（使用中文化后的说明）：

```powershell
git add README.md GIT_WORKFLOW.md
git commit -m "文档：补充 Git 与远程仓库协作流程"

git add frontend/src/views/common/Dashboard.vue
git commit -m "功能：增加仪表盘刷新与更新时间显示"

git add README.md frontend/.env.example
git commit -m "配置：补充前端代理环境变量示例与说明"
```

## 三、错误提交与版本回滚

为了验证回滚流程，在 `Dashboard.vue` 中故意删除了一个右括号并提交：

```powershell
git add frontend/src/views/common/Dashboard.vue
git commit -m "测试：引入仪表盘语法错误以练习版本回滚"
```

运行构建后，Vite 返回 `Unexpected token, expected ","`，证明该提交确实破坏了前端构建。随后执行：

```powershell
git revert --no-edit HEAD
```

Git 当时生成了恢复提交 `426aeb3`，中文化后的编号为 `c3dc5fa`。回滚后重新执行 `npm run build`，构建成功。这里选用 `git revert` 而不是破坏历史的 `git reset --hard`，因此错误提交和恢复过程完整保留；后续仅修改提交说明，没有删除这些记录。

## 四、推送与同步验证

完整历史使用以下命令推送：

```powershell
git push origin main
```

推送后使用以下命令验证本地分支、远程跟踪分支和 GitHub 远端引用：

```powershell
git fetch origin
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count HEAD...origin/main
git ls-remote origin refs/heads/main
git status --short
git branch -avv
git log --oneline --decorate --graph
```

2026-10-13 的原始同步验证结果：

- `HEAD`、`origin/main` 和 GitHub 的 `refs/heads/main` 指向同一提交。
- `HEAD...origin/main` 的领先和落后数量均为 `0`。
- 工作区无未提交修改。
- 本地与远程都使用 `main` 作为默认分支。
- `node_modules`、`dist`、`target`、测试截图、本地 `.env` 文件和 `.eduexam.local.bat` 均被 `.gitignore` 排除。

## 五、原始实践质量检查（2026-10-13）

```powershell
cd frontend
npm ci --no-audit --no-fund
npm audit --audit-level=high --registry=https://registry.npmjs.org/
npm test
npm run build

cd ..\backend
mvn --batch-mode --no-transfer-progress clean verify
```

- 前端锁定依赖冷安装：通过；官方 npm 漏洞审计结果为 `0 vulnerabilities`。
- 前端单元测试：4 个测试文件、16 个测试全部通过；生产构建通过。
- 后端 Maven 验证：40 个测试全部通过；可执行 JAR 打包成功。
- GitHub Actions 的前端任务和后端任务均通过；后端任务还会在随用随毁的 MySQL 8.4 容器上执行 Flyway 全新库迁移、启动应用并验证 `/api/health` 返回数据库 `UP`。
- GitHub 远程树已复核，共 155 个受版本控制的文件；未上传 `node_modules`、`dist`、`target`、本地环境文件或本地启动密钥。
- 回滚后的最终代码处于可构建、可测试、可启动状态。

至此，实践覆盖了公开远程仓库、README、远程克隆、本地功能开发、多次有意义提交、错误提交、版本回滚、推送以及本地与远程一致性验证。

## 六、统计界面与中文提交规范维护（2026-10-14）

- 统计页增加题库概览、考试分析、分类明细及异常状态展示，并修复柱条被高亮遮挡、环形图提示框叠字及连续悬停问题。
- 当前前端验证为 5 个测试文件、22 项单元测试全部通过；生产构建通过。构建仍有原有第三方注释和分块体积警告。
- 浏览器回归覆盖桌面及窄屏悬停、真实角色权限、明细搜索、错误/空数据状态和乱序请求，详见 `frontend/STATS_REDESIGN.md`。
- 中文提交规范记录在 `AGENTS.md`、`.cursor/rules/commit-messages.mdc` 和 `GIT_WORKFLOW.md`，后续人工提交均使用中文。
- 15 条原项目提交中文化前后逐一比较 tree，文件快照完全相同；本地待提交改动也未因重写丢失。
- 旧历史有项目外 Git bundle 备份，练习副本可从 Windows 回收站恢复。
- 本轮只更新 `main`。Dependabot 自动依赖分支未改写或合并，旧 PR 需要后续基于新历史重新生成。

中文化属于所有者明确授权的历史重写；推送时使用带明确远程旧提交值的 `--force-with-lease`，防止覆盖检查之后出现的新提交。普通后续开发仍使用 `git push origin main`，无需再次强制推送。
