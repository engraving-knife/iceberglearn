# 提交 2393：Flink: fail file rewrite for V3 tables as row lineage not supported (#13646)

## 提交信息

- **序号**：2393 / 4088
- **哈希**：a40ab7ad475b7a1df590109d58cd6c73f0b5d10e
- **短哈希**：a40ab7ad4
- **日期**：2025-07-23 13:58:31 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: fail file rewrite for V3 tables as row lineage not supported (#13646)
- **PR/Issue**：#13646

## 总体目的

此提交在 Flink 的数据文件重写（compaction）流程中添加了对 V3 表的显式失败检查。Iceberg V3 表引入了行级血统（row lineage）特性，而 Flink 当前的 compaction 实现不支持在压缩过程中保留行级血统信息。与其让操作静默执行并产生丢失行级血统的错误数据，不如在操作开始时就快速失败，给出明确的错误信息。

与 Spark 不同（Spark 在 2390/2392 中实现了 compaction 时保留行级血统的功能），Flink 尚未实现这一能力。因此，本提交采取了防御性策略：在 `RewriteDataFilesAction` 和 `DataFileRewritePlanner` 两个入口点添加前置检查，如果表支持行级血统（V3+），则抛出 `IllegalArgumentException`，阻止 compaction 操作执行。

## 如何达成设计目的

关键设计点：

1. **双入口检查**：在 Flink compaction 的两个入口——`RewriteDataFilesAction`（批处理 API）和 `DataFileRewritePlanner`（流式维护 API）——都添加了行级血统检查，确保无论通过哪种方式触发 compaction 都会被拦截。
2. **使用 TableUtil.supportsRowLineage**：通过统一的工具方法判断表是否支持行级血统，避免硬编码版本号检查。
3. **测试适配**：现有 compaction 测试通过 `Assumptions.assumeThat(formatVersion).isLessThan(3)` 跳过 V3 表，新增专门的 V3 失败测试用例。
4. **OperatorTestBase 扩展**：支持创建指定格式版本的表，便于测试 V3 场景。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/actions/RewriteDataFilesAction.java` (+4/-0 lines)

**修改目的**：在批处理 compaction 入口添加 V3 表检查。

**工作逻辑**：在构造函数中添加 `Preconditions.checkArgument(!TableUtil.supportsRowLineage(table), "Flink does not support compaction on row lineage enabled tables (V3+)")`，在创建 RewriteDataFilesAction 实例时即进行校验，快速失败。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+6/-0 lines)

**修改目的**：在流式维护 compaction 入口添加 V3 表检查。

**工作逻辑**：在 `open(OpenContext context)` 方法中，通过 `tableLoader.loadTable()` 加载表后，添加相同的行级血统检查。这确保了通过 Flink 维护算子触发的 compaction 也会被拦截。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/actions/TestRewriteDataFilesAction.java` (+34/-0 lines)

**修改目的**：新增 V3 表 compaction 失败测试并适配现有测试。

**工作逻辑**：新增 `testFailureOnV3Table` 测试，验证 V3 表执行 compaction 时抛出包含 "Flink does not support compaction on row lineage enabled tables (V3+)" 的 IllegalArgumentException。所有现有 compaction 测试（testRewriteDataFilesEmptyTable、testRewriteDataFilesUnpartitionedTable 等）添加 `Assumptions.assumeThat(formatVersion).isLessThan(3)`，使其仅在 V2 及以下版本运行。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+14/-2 lines)

**修改目的**：支持创建指定格式版本的表。

**工作逻辑**：`createTable()` 方法默认创建 V2 表（因 compaction 不支持 V3）。新增 `createTable(String formatVersion)` 重载方法，通过 `TableProperties.FORMAT_VERSION` 属性指定格式版本。`createTableWithDelete` 也显式设置 `format-version` 为 "2"。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+14/-0 lines)

**修改目的**：新增 DataFileRewritePlanner 的 V3 表失败测试。

**工作逻辑**：新增 `testFailsOnV3Table` 测试，创建 V3 表并插入数据后，验证 `planDataFileRewrite` 抛出 IllegalArgumentException 并包含预期的错误消息。

## 总结

此提交采取了防御性设计，在 Flink compaction 不支持行级血统的情况下，通过快速失败机制避免数据损坏。与 Spark 积极实现行级血统保留不同，Flink 选择先阻止操作再后续实现。双入口检查确保了全面的拦截覆盖，测试也相应调整以适配 V3 场景。
