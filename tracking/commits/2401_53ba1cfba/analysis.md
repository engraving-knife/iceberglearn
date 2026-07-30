# 提交 2401：Flink: backport PR #13646 to 1.19 for fail compaction on V3 tables

## 提交信息

- **序号**：2401 / 4088
- **哈希**：53ba1cfbade47de21fda777bffc5c0e7360919d7
- **短哈希**：53ba1cfba
- **日期**：2025-07-24 09:28:46 +0200
- **作者**：Steven Wu
- **提交说明**：Flink: backport PR #13646 to 1.19 for fail compaction on V3 tables
- **PR/Issue**：backport #13646

## 总体目的

此提交将 PR #13646（提交 2393）backport 到 Flink 1.19 分支。核心目标是**在 Flink 1.19 的数据文件重写（compaction）流程中对 V3 表（支持行级血统的表）添加显式失败检查**。

Iceberg V3 表引入了行级血统（row lineage）特性，而 Flink 当前的 compaction 实现不支持在压缩过程中保留行级血统信息。为避免静默产生丢失行级血统的错误数据，在 compaction 入口添加前置检查，如果表支持行级血统（V3+），则抛出 `IllegalArgumentException` 阻止操作。

此提交的代码变更与 2393（flink/v2.0）和 2399（flink/v1.20）完全一致，仅目标分支不同（flink/v1.19）。这确保了所有 Flink 版本在 compaction 不支持行级血统时统一快速失败。

## 如何达成设计目的

与 2393 完全一致的设计：

1. **双入口检查**：在 `RewriteDataFilesAction` 和 `DataFileRewritePlanner` 两个入口添加行级血统检查。
2. **使用 TableUtil.supportsRowLineage**：统一工具方法判断表是否支持行级血统。
3. **测试适配**：现有 compaction 测试跳过 V3 表，新增 V3 失败测试。
4. **OperatorTestBase 扩展**：支持创建指定格式版本的表。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/actions/RewriteDataFilesAction.java` (+4/-0 lines)

**修改目的**：批处理 compaction 入口添加 V3 表检查。

**工作逻辑**：构造函数中添加 `Preconditions.checkArgument(!TableUtil.supportsRowLineage(table), "Flink does not support compaction on row lineage enabled tables (V3+)")`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+6/-0 lines)

**修改目的**：流式维护 compaction 入口添加 V3 表检查。

**工作逻辑**：在 `open` 方法中加载表后添加行级血统检查。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/actions/TestRewriteDataFilesAction.java` (+34/-0 lines)

**修改目的**：新增 V3 表 compaction 失败测试并适配现有测试。

**工作逻辑**：新增 `testFailureOnV3Table` 测试验证 V3 表 compaction 抛出异常。现有测试添加 `Assumptions.assumeThat(formatVersion).isLessThan(3)` 跳过 V3。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+14/-2 lines)

**修改目的**：支持创建指定格式版本的表。

**工作逻辑**：新增 `createTable(String formatVersion)` 重载方法，通过 `TableProperties.FORMAT_VERSION` 指定版本。默认 `createTable()` 创建 V2 表。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+14/-0 lines)

**修改目的**：新增 DataFileRewritePlanner 的 V3 表失败测试。

**工作逻辑**：`testFailsOnV3Table` 创建 V3 表并验证 `planDataFileRewrite` 抛出 IllegalArgumentException。

## 总结

此提交是 2393 在 Flink 1.19 分支上的对应 backport，代码变更完全一致。与 2399（Flink 1.20）一起，确保了所有维护中的 Flink 版本（1.19、1.20、2.0）在 compaction 不支持行级血统时统一快速失败，保持跨版本行为一致性。
