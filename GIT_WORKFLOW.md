# Git 协作实践记录

本项目使用 `main` 作为稳定分支，功能完成后再推送到 GitHub 远程仓库。

## 日常流程

```powershell
git pull --ff-only origin main
git status
git add .
git commit -m "feat: 描述本次功能"
git push origin main
```

每次提交只包含一个完整、可说明的功能或修复，并使用动词开头的提交信息，便于回顾版本历史。

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
