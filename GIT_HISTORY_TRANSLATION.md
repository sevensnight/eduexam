# 提交说明中文化记录

## 范围与校验

2026 年 10 月 14 日，经项目所有者明确授权，将 `main` 上已有的 15 条项目提交说明改为中文，回滚提交的正文也改为中文，并指向重写后的错误提交。

- 每条提交的文件树（tree）逐一核对，均与原版本完全相同。
- 原作者、提交者、作者时间、提交时间与父子顺序均保留。
- 初始提交、功能开发、故意引入错误以及回滚恢复记录完整保留，没有压缩或删除提交。
- 重写前后的末端文件树相同，中文化没有改变本地未提交的 UI 和悬停修复。
- 旧提交编号会变化；旧截图和旧版本文档中的编号可通过下表对应。
- 仅更新项目主分支，不改写或合并 Dependabot 的 5 个依赖更新分支。旧 PR 基于旧历史，需要后续重新生成后再评审。

## 编号对照

| 原提交 | 中文化后的提交 | 中文说明 |
| --- | --- | --- |
| `55a0d7d` | [`155e001`](https://github.com/sevensnight/eduexam/commit/155e001c24bedf0cee4d34b246d8c13b5df98eef) | 初始化：创建 EduExam 在线题库与考试系统 |
| `8a4b088` | [`64e53b5`](https://github.com/sevensnight/eduexam/commit/64e53b54aa1a0999893daf7770a3705beb0bbe5e) | 文档：补充 Git 与远程仓库协作流程 |
| `5ce81ba` | [`717d464`](https://github.com/sevensnight/eduexam/commit/717d46475509b5f82a5eea9a921e6c01e624d34f) | 功能：增加仪表盘刷新与更新时间显示 |
| `a58cde6` | [`d6ab428`](https://github.com/sevensnight/eduexam/commit/d6ab428f037c3ed5bed2de07172f75baa40b3b16) | 配置：补充前端代理环境变量示例与说明 |
| `c76cd4a` | [`cbb4427`](https://github.com/sevensnight/eduexam/commit/cbb4427865891752c7758cd61154047574cb27fb) | 测试：引入仪表盘语法错误以练习版本回滚 |
| `426aeb3` | [`c3dc5fa`](https://github.com/sevensnight/eduexam/commit/c3dc5fad5160e87439fc17ce586a1bf5049ca0a8) | 回滚：撤销练习用语法错误并恢复仪表盘 |
| `aaaef3b` | [`7252e86`](https://github.com/sevensnight/eduexam/commit/7252e86d389d35af52efc76575f94be6f44b4fd6) | 文档：新增 Git 实践验收报告 |
| `4b0e339` | [`463ec68`](https://github.com/sevensnight/eduexam/commit/463ec68107dd691062d13f89daae8fe3aa9bc26f) | 修复：限制公开注册仅能创建学生账号 |
| `6642f12` | [`0910f4e`](https://github.com/sevensnight/eduexam/commit/0910f4e9a34a39502c0d6879f2ffb2893805a53f) | 修复：加强考试访问控制与事务化交卷 |
| `ca6a131` | [`0cb8c6a`](https://github.com/sevensnight/eduexam/commit/0cb8c6a9caf222880c17f8744b59140bceba0d8b) | 修复：加强考试完整性并保存学生答题草稿 |
| `67ffe8b` | [`2317130`](https://github.com/sevensnight/eduexam/commit/23171306cd365082094d160429ef381c23ecdd8b) | 修复：完善一键启动与数据库安全初始化 |
| `a26e1ad` | [`bd935d1`](https://github.com/sevensnight/eduexam/commit/bd935d11b2097c48c27ea6a43a7a92e20e9e549b) | 修复：完善考试流程、并发处理与大数据量支持 |
| `b80847b` | [`5718909`](https://github.com/sevensnight/eduexam/commit/57189090422a616c57d8adfc364cbd11d8f2c05a) | 构建：配置自动化验证与依赖更新检查 |
| `0406997` | [`e1cc982`](https://github.com/sevensnight/eduexam/commit/e1cc9821cc00515b30e4a97abe583e9137d81fb0) | 修复：统一前端锁文件与持续集成的 npm 版本 |
| `2502602` | [`06dfa2a`](https://github.com/sevensnight/eduexam/commit/06dfa2a4d396593100397c284586ffabf7421d15) | 文档：更新 Git 实践验证结果 |

## 备份与恢复

操作前建立了完整 Git bundle 备份，校验通过；当前未提交文件另有文件级备份。备份位于项目目录之外，不会上传到公开仓库。

- 外部历史备份：`D:\\软件工程与UML\\eduexam-backup-20261014-1657\\before-chinese-history.bundle`。
- 本地备用引用：`refs/backup/main-before-chinese-20261014`，未推送到 GitHub。
- 一次性中文化脚本及具体操作命令保存在同一个外部备份目录中。

需要查阅旧版本时，可从 bundle 克隆到一个新的目录；不要在当前工作区执行破坏性重置。不要将旧副本的英文历史再次强制推送覆盖主分支。

## 后续约定

人工提交的标题和正文统一使用中文，技术名词可保留原文。约定记录在 `AGENTS.md`、`.cursor/rules/commit-messages.mdc` 与 `GIT_WORKFLOW.md` 中。

本次采用保留原文件树的提交重建，并使用带旧值校验的引用更新；这与仅改说明的历史重写目的相同。相关 Git 机制见 [commit-tree](https://git-scm.com/docs/git-commit-tree)、[update-ref](https://git-scm.com/docs/git-update-ref) 和 [force-with-lease](https://git-scm.com/docs/git-push#Documentation/git-push.txt---force-with-leaseltrefnamegtltexpectgt)。
