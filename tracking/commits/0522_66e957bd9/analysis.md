# 提交 0522：Infra: Fix issue template labels

## 提交信息

- **序号**：0522 / 4088
- **哈希**：66e957bd99ff8d11344efda4cb177c2d243eedaa
- **短哈希**：66e957bd9
- **日期**：2024-02-20 10:51:05 +0100
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>（与 Zhang, Manu <tianlzhang@ebay.com> 共作）
- **提交说明**：Infra: Fix issue template labels (#9759)
- **PR/Issue**：#9759

## 总体目的

修复 GitHub Issue 模板中预设的 label 名称与仓库实际配置的 label 不一致的问题。三个 Issue 模板（bug 报告、改进/特性请求、提问）在 `labels:` 字段中填写的标签名（`kind:bug`、`kind:feature request`、`kind:question`）在仓库的 label 集合中已不存在或已被重命名，导致用户通过模板提交 Issue 时 GitHub 报错或无法自动打标。本提交将三个模板的 label 改为仓库实际存在的标签名：`bug`、`improvement`、`question`。

## 如何达成设计目的

通过直接修改 `.github/ISSUE_TEMPLATE/` 目录下三个 YAML 表单模板文件中 `labels:` 字段的值，使其与 apache/iceberg 仓库实际配置的 label 名称对齐。改动极小，每个文件仅一行：

- bug 报告模板：`["kind:bug"]` → `["bug"]`
- 改进/特性请求模板：`["kind:feature request"]` → `["improvement"]`
- 提问模板：`["kind:question"]` → `["question"]`

值得注意的是 `kind:feature request` 不仅去掉了 `kind:` 前缀，还将"feature request"改为"improvement"——这与 Apache Iceberg 仓库的 label 治理一致：仓库用 `improvement` 而非 `feature request` 作为新功能建议的标签名。GitHub Issue 表单的 `labels` 字段要求填写的标签必须事先在仓库 Labels 页面定义，否则提交时该标签会被静默丢弃，因此保持模板与实际 label 同步是必要维护。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：使 bug 报告模板的自动标签匹配仓库实际 label。

**工作逻辑**：`labels: ["kind:bug"]` 改为 `labels: ["bug"]`。其余表单结构（dropdown、textarea 等）不变。

### `.github/ISSUE_TEMPLATE/iceberg_improvement.yml`

**修改目的**：使改进/特性请求模板的自动标签匹配仓库实际 label。

**工作逻辑**：`labels: ["kind:feature request"]` 改为 `labels: ["improvement"]`。同时统一了命名（去掉 `kind:` 前缀，并用 `improvement` 替代 `feature request`）。

### `.github/ISSUE_TEMPLATE/iceberg_question.yml`

**修改目的**：使提问模板的自动标签匹配仓库实际 label。

**工作逻辑**：`labels: ["kind:question"]` 改为 `labels: ["question"]`。

## 小结

**成效**：修复了 Issue 模板的 label 失效问题，确保用户通过模板提交的 Issue 能正确自动打上 `bug`/`improvement`/`question` 标签，便于维护者分流。

**影响范围**：仅 GitHub 仓库元数据（Issue 模板），不影响任何代码、构建或运行时行为。

**回迁到 1.4.x 的注意事项**：纯仓库治理类改动，cherry-pick 无技术风险。但 1.4.x 作为已发布维护分支，是否回迁取决于维护者是否仍接受 1.4.x 的 Issue（通常维护分支的 Issue 也走主仓库模板，故回迁非必需）。回迁时需确认 1.4.x 分支的 `.github/ISSUE_TEMPLATE/` 路径与文件名一致。
