# 提交 3674：CI: Add PR title check workflow (#16101)

## 提交信息

- **序号**：3674 / 4088
- **哈希**：fce25041755c53e85ecaab982556b18d7001818c
- **短哈希**：fce250417
- **日期**：2026-05-09 10:15:11 -0700
- **作者**：Manu Zhang
- **提交说明**：CI: Add PR title check workflow (#16101)
- **PR/Issue**：#16101

## 总体目的

这个提交为 Iceberg 项目新增了一个 GitHub Actions 工作流，用于检查 PR 标题是否符合规范格式。

Iceberg 项目要求 PR 标题遵循 `Module: Description`（模块: 描述）的格式，例如 `Core: Fix ...`、`Spark: Add ...`、`API: Remove ...`、`Docs: Update ...`。这种格式便于从 PR 标题识别改动涉及的模块，也用于自动生成 changelog。此前这一规范依赖人工审查，容易被忽视。本提交新增自动化检查工作流，在 PR 创建、编辑、重新打开时自动验证标题格式，不符合则报错。

## 如何达成设计目的

新建 `.github/workflows/pr-title-check.yml` 工作流，在 PR 的 `opened`、`edited`、`reopened` 事件触发时，使用 grep 正则表达式验证 PR 标题是否符合 `^[A-Za-z][A-Za-z0-9._+/&-]*: .+` 模式（即模块名 + 冒号 + 空格 + 描述）。

## 修改详情

### `.github/workflows/pr-title-check.yml` (+44 lines, new)

**修改目的**：新增 PR 标题格式检查工作流。

**工作逻辑**：
1. 触发条件：PR 的 `opened`、`edited`、`reopened` 事件。
2. 并发控制：按 PR 编号分组，取消旧的进行中检查。
3. 权限：`permissions: {}`（最小权限）。
4. 运行环境：`ubuntu-slim`。
5. 检查逻辑：
```bash
PATTERN='^[A-Za-z][A-Za-z0-9._+/&-]*: .+'
if ! echo "$PR_TITLE" | grep -Eq "$PATTERN"; then
  echo "::error::PR title must follow 'Module: Description' format. Got: '$PR_TITLE'"
  echo "Examples: 'Core: Fix ...', 'Spark: Add ...', 'API: Remove ...', 'Docs: Update ...'"
  exit 1
fi
```
正则要求：以字母开头，后跟字母/数字/特定符号组成模块名，然后冒号+空格，最后是描述文本。不符合时输出 GitHub error 注解并退出失败。

## 总结

这个提交新增了 PR 标题格式检查的 GitHub Actions 工作流，自动验证 PR 标题遵循 `Module: Description` 格式。这是一个 CI 流程改进，通过自动化检查替代人工审查，确保 PR 标题规范的一致性，便于模块识别和 changelog 生成。工作流设计简洁，使用正则表达式验证，并提供清晰的错误提示和示例。
