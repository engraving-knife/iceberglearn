# 提交 0527：Infra: Add Kafka Connect as a label

## 提交信息

- **序号**：0527 / 4088
- **哈希**：811c92075ea4fb1b0734e43e08d7bb1bffd81350
- **短哈希**：811c92075
- **日期**：2024-02-21 13:53:01 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Infra: Add Kafka Connect as a label
- **PR/Issue**：#9769

## 总体目的

本提交是一个**纯基础设施类的小改动**：在 GitHub Issue 模板的“Query engine”下拉选项中新增 `Kafka Connect` 一项，使提交 Bug 报告和改进建议的用户能够选择 Kafka Connect 作为他们使用的查询/集成引擎。

### 背景

Apache Iceberg 不仅有 SQL 查询引擎（Spark、Trino、Presto、Flink 等）的集成，还提供了 **Kafka Connect connector**（位于 `kafka-connect` 子模块中），用于将 Iceberg 表与 Kafka 生态对接（典型场景如 sink connector 将 Kafka 数据落盘到 Iceberg 表）。在用户提交 issue 时，模板里的“Query engine”字段用于归类用户使用的引擎，但此前该下拉列表只列出了 Spark、Trino、Starburst、Snowflake、Dremio、Starrocks、Doris、EMR、Athena、PrestoDB、Flink、Impala、Hive、Other，缺少 Kafka Connect 选项。这导致 Kafka Connect 用户只能选 “Other”，无法在 issue 元数据上明确标识，不利于维护者统计与分流 Kafka Connect 相关的问题。

## 如何达成设计目的

设计非常直接：在两个 Issue 模板文件中各加一行 `- Kafka Connect`，将其插入在 `- Flink` 与 `- Impala` 之间（保持与引擎列表的相邻关系——Kafka Connect 与 Flink/Streaming 生态相近）。

### 文件作用说明

- `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`：Bug 报告模板。该模板定义了用户在 GitHub 上提交 Bug 时填写的表单字段，包括 Iceberg 版本、Query engine、Bug 描述等。`Query engine` 字段是一个 `dropdown`，`options` 数组列出所有可选引擎。
- `.github/ISSUE_TEMPLATE/iceberg_improvement.yml`：改进建议模板，结构与 bug 报告模板类似，同样有 `Query engine` 下拉字段。

这两个 YAML 模板被 GitHub 自动识别为 Issue 模板，用户在仓库的 “New issue” 页面会看到对应选项。新增 `Kafka Connect` 选项后，所有新提交的 issue 都能直接选 Kafka Connect，无需写 “Other”。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：在 Bug 报告模板的“Query engine”下拉选项中新增 Kafka Connect。

**工作逻辑**：在 `Query engine` 下拉 `options` 列表中，于 `- Flink` 之后、`- Impala` 之前插入一行 `- Kafka Connect`。修改后该字段的可选值为：Spark、Trino、Starburst、Snowflake、Dremio、Starrocks、Doris、EMR、Athena、PrestoDB、Flink、**Kafka Connect**、Impala、Hive、Other。GitHub 在渲染 Issue 表单时会把这条新选项展示给用户选择。

### `.github/ISSUE_TEMPLATE/iceberg_improvement.yml`

**修改目的**：在改进建议模板的“Query engine”下拉选项中新增 Kafka Connect，与 bug 报告模板保持一致。

**工作逻辑**：与 bug_report 完全相同的修改——在 `- Flink` 之后插入 `- Kafka Connect`。确保无论用户提交 bug 还是改进建议，都能正确选择 Kafka Connect 作为引擎。

## 小结

- **成效**：本次改动让 Kafka Connect 用户在提交 issue 时能明确标识所使用的引擎，提升 issue 分类与维护者分流的效率，并体现出 Iceberg 对 Kafka Connect 子模块的官方认可（与 Spark、Flink 等并列为一级引擎选项）。
- **影响范围**：仅影响 GitHub Issue 模板渲染，不涉及任何代码、构建或运行时行为。两个模板文件各加 1 行，共 2 行新增。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个纯粹的仓库基础设施改动，不依赖任何代码逻辑，回迁完全无风险。
  2. 1.4.x 分支的 issue 模板如果与 main 分支同步落后，可直接 cherry-pick 本提交。
  3. 注意 1.4.x 模板中的 Iceberg 版本列表可能滞后（如缺少 1.4.3 之后的版本），但本提交只动 Query engine 字段，与之无关，不需要协同更新版本列表。
