# 提交 0086：Build: Avoid Running engine and core CI on template update (#8890)

## 提交信息

- **序号**：0086 / 4088
- **哈希**：b1f7008517bf9da0fe4eea6755878a87cf64341d
- **短哈希**：b1f700851
- **日期**：2023-10-21 09:27:01 +0200
- **作者**：Prashant Singh
- **提交说明**：Build: Avoid Running engine and core CI on template update (#8890)
- **PR/Issue**：#8890

## 总体目的

这个提交要解决的是 CI 资源浪费问题。Iceberg 仓库下维护了多个 GitHub Actions 工作流（`delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`spark-ci.yml`），每个工作流在 `pull_request` 触发器里都配置了 `paths-ignore`，用来在 PR 仅改动某些与代码无关的文件时跳过 CI 运行，以节省 CI 算力与执行时间。

此前这五个工作流的 `paths-ignore` 列表里，针对 issue 模板目录的忽略规则只写了单个文件 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`。也就是说，只有当 PR 恰好只改动了 `iceberg_bug_report.yml` 这一个文件时，CI 才会跳过；而一旦 PR 改动了 `.github/ISSUE_TEMPLATE/` 目录下的任何其它模板（例如新增或修改其它 issue 模板、config 文件等），所有 engine 和 core 的 CI 仍会被触发并跑一遍，即便这些改动与引擎代码、core 代码完全无关。

本提交将忽略规则从精确匹配单个 bug 报告模板扩展为通配 `.github/ISSUE_TEMPLATE/**`，使整个 issue 模板目录下的任何文件变更都不会再触发这些重型 CI。这是一个典型的"基础设施维护"类改动，对 Iceberg 演进的意义在于持续优化 CI 成本和反馈速度，避免贡献者因无关的模板改动等待无意义的 CI 运行。

## 如何达成设计目的

设计思路非常直接：把五个 CI 工作流 YAML 文件中 `paths-ignore` 列表里那一行 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 统一替换为 `.github/ISSUE_TEMPLATE/**`，从而把忽略范围从一个具体文件扩展到整个模板目录。改动结构高度一致，五个文件采用相同的替换模式，没有引入新的逻辑或新文件。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml`

**修改目的**：扩大 `paths-ignore` 对 issue 模板的忽略范围，避免 delta-conversion CI 在模板目录任意文件变更时被触发。

**工作逻辑**：在 `pull_request` 触发器的 `paths-ignore` 列表中，将 `'.github/ISSUE_TEMPLATE/iceberg_bug_report.yml'` 改为 `'.github/ISSUE_TEMPLATE/**'`，使通配整个目录。

### `.github/workflows/flink-ci.yml`

**修改目的**：与上一节相同，避免 flink CI 在 issue 模板更新时被触发。

**工作逻辑**：同样将 `paths-ignore` 中针对 bug 报告模板的单文件条目改为 `.github/ISSUE_TEMPLATE/**`。注意此文件的缩进风格与其它文件略有不同（列表项与 `-` 之间无额外空格），但替换内容一致。

### `.github/workflows/hive-ci.yml`

**修改目的**：避免 hive CI 在 issue 模板目录任意文件更新时被触发。

**工作逻辑**：将 `paths-ignore` 中的 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 替换为 `.github/ISSUE_TEMPLATE/**`。

### `.github/workflows/java-ci.yml`

**修改目的**：避免 core Java CI 在 issue 模板更新时被触发。

**工作逻辑**：将 `paths-ignore` 中的 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 替换为 `.github/ISSUE_TEMPLATE/**`。`java-ci.yml` 是 core CI 工作流，这一处是本提交标题中"core CI"指代的目标。

### `.github/workflows/spark-ci.yml`

**修改目的**：避免 spark CI 在 issue 模板更新时被触发。

**工作逻辑**：将 `paths-ignore` 中的 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 替换为 `.github/ISSUE_TEMPLATE/**`。

## 小结

通过把五个 CI 工作流中对 issue 模板的 `paths-ignore` 由单文件升级为整目录通配，本提交让模板类改动不再无谓触发 engine 与 core CI，是 Iceberg 持续优化 CI 成本与反馈延迟的一次基础设施维护。
