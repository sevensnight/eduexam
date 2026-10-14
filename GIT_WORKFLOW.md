# Git 协作实践记录

本项目使用 `main` 作为稳定分支，功能完成后再推送到 GitHub 远程仓库。

## 日常流程

```powershell
git pull --ff-only origin main
git status
git add .
git commit -m "功能：描述本次功能"
git push origin main
```

每次提交只包含一项完整、可说明的功能或修复，标题和正文统一使用简体中文。标题采用“类型：具体改动”，例如“修复：解决统计图悬停遮挡”；Git、Vue、ECharts、npm 等技术名词可以保留原文。有必要时在正文说明原因和验证结果。

项目约定同时记录在 `AGENTS.md` 和 `.cursor/rules/commit-messages.mdc`。未经明确授权，不为调整提交说明而改写已推送的历史。Dependabot 自动管理的依赖分支不属于人工提交规范的改写范围。

日常开发只使用 `eduexam` 目录。原 `eduexam-git-practice` 是作业中的独立克隆副本，已完成克隆和回滚演示，不需要再维护第二份开发目录。

## 回滚练习

如果某次提交引入了问题，可以先确认问题提交，再用 `git revert` 创建一个反向提交：

```powershell
git log --oneline --decorate -5
git revert <错误提交哈希>
git push origin main
```

`git revert` 会保留原提交和恢复提交，适合已经推送到远程仓库的协作分支；不要对共享分支随意使用会改写历史的 `git reset --hard`。

## 本次实践检查项

- 远程仓库：GitHub `sevensnight/eduexam`
- 稳定分支：`main`
- 必须保留：初始提交、功能提交、文档或配置提交、回滚提交
- 推送前检查：`git status`、`git log --oneline --decorate`、`git diff origin/main`
