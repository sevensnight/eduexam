# Git 与远程仓库基础协作实践报告

## 一、仓库信息

- 项目名称：EduExam
- 远程仓库：<https://github.com/sevensnight/eduexam>
- 仓库可见性：Public
- 默认分支：`main`
- 实践日期：2026-10-09

远程仓库创建时已经包含项目根目录的 `README.md`，随后使用标准 `git clone` 命令创建独立的本地开发副本：

```powershell
git clone https://github.com/sevensnight/eduexam.git "D:\软件工程与UML\eduexam-git-practice"
```

## 二、功能开发与提交历史

本次实践按照“一次提交完成一项明确工作”的原则形成了以下版本历史：

| 提交 | 类型 | 内容 |
| --- | --- | --- |
| `55a0d7d` | 初始提交 | 上传完整 EduExam 前后端项目、SQL 和启动脚本 |
| `8a4b088` | 文档提交 | 新增 Git 协作流程说明 |
| `5ce81ba` | 功能提交 | 增加仪表盘手动刷新和最后更新时间显示 |
| `a58cde6` | 配置提交 | 新增前端代理环境变量示例并补充使用说明 |
| `c76cd4a` | 错误提交 | 故意引入 Dashboard 语法错误，用于回滚练习 |
| `426aeb3` | 回滚提交 | 使用 `git revert` 撤销错误提交，恢复正确版本 |

前三项开发提交的命令示例：

```powershell
git add README.md GIT_WORKFLOW.md
git commit -m "docs: add Git collaboration workflow"

git add frontend/src/views/common/Dashboard.vue
git commit -m "feat: add dashboard refresh status"

git add README.md frontend/.env.example
git commit -m "chore: document frontend proxy configuration"
```

## 三、错误提交与版本回滚

为了验证回滚流程，在 `Dashboard.vue` 中故意删除了一个右括号并提交：

```powershell
git add frontend/src/views/common/Dashboard.vue
git commit -m "test: introduce dashboard syntax error for rollback practice"
```

运行构建后，Vite 返回 `Unexpected token, expected ","`，证明该提交确实破坏了前端构建。随后执行：

```powershell
git revert --no-edit HEAD
```

Git 生成了新的恢复提交 `426aeb3`。回滚后重新执行 `npm run build`，构建成功。这里选用 `git revert` 而不是改写历史的 `git reset --hard`，因此错误提交和恢复过程都能在远程仓库中完整查看。

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

验证结果：

- `HEAD`、`origin/main` 和 GitHub 的 `refs/heads/main` 指向同一提交。
- `HEAD...origin/main` 的领先和落后数量均为 `0`。
- 工作区无未提交修改。
- 本地与远程都使用 `main` 作为默认分支。
- `node_modules`、`dist`、`target`、测试截图和本地 `.env` 文件均被 `.gitignore` 排除。

## 五、最终质量检查

```powershell
cd frontend
npm run build

cd ..\backend
mvn test
```

- 前端生产构建：通过。
- 后端 Maven 测试阶段：通过；当前项目没有单元测试源文件。
- 回滚后的最终代码处于可构建状态。

至此，实践覆盖了公开远程仓库、README、远程克隆、本地功能开发、多次有意义提交、错误提交、版本回滚、推送以及本地与远程一致性验证。
