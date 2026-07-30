# 提交 3546：Build: Ignore `.githooks` (#15909)

## 提交信息

- **序号**：3546 / 4088
- **哈希**：1a6a4881012bbf367296f138685b9f8d80d800ee
- **短哈希**：1a6a48810
- **日期**：2026-04-15 20:19:56 -0700
- **作者**：Manu Zhang
- **提交说明**：Build: Ignore `.githooks` (#15909)
- **PR/Issue**：#15909

## 总体目的

开发者本地可能会配置 git hooks（如 pre-commit 钩子）用于代码格式检查、提交规范验证等，这些钩子通常存放在 `.githooks/` 目录下。这是开发者个人的本地工具配置，不应提交到共享仓库，否则会污染项目历史并可能与其他开发者的配置冲突。

此前 `.gitignore` 没有忽略 `.githooks/` 目录，导致开发者本地运行的 `git status` 会显示该目录下的文件为未跟踪，可能被误提交。本提交在 `.gitignore` 中添加 `.githooks/` 条目，避免该目录被跟踪。

## 如何达成设计目的

在 `.gitignore` 末尾新增一段注释和 `.githooks/` 条目，遵循现有 `.gitignore` 的分段注释风格。

## 修改详情

### `.gitignore` (+3/-0 lines)

**修改目的**：忽略 `.githooks/` 目录。

**工作逻辑**：
```
+
+# git hooks like pre-commit
+.githooks/
```
在文件末尾新增注释 `# git hooks like pre-commit` 和忽略规则 `.githooks/`，与前面 `# sdkman` 等分段的注释风格一致。

## 总结

本提交在 `.gitignore` 中添加 `.githooks/` 目录的忽略规则，避免开发者本地的 git hooks 配置被误提交到仓库。属于仓库卫生维护的小改进。
