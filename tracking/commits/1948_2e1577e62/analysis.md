# 提交 1948：Flink: Backport support source watermark for flink sql windows (#12697)

## 提交信息

- **序号**：1948 / 4088
- **哈希**：2e1577e62bf023daddb0ad7aec5ce6f094fb98dc
- **短哈希**：2e1577e62
- **日期**：2025-04-02 00:27:43 +0200
- **作者**：Swapna Marru
- **提交说明**：Flink: Backport support source watermark for flink sql windows（backports #12191）
- **PR/Issue**：#12697（回溯提交）/ #12191（被回溯的原始 PR）

## 总体目的

此提交将此前已合入主分支的 PR #12191 回溯到 Iceberg 的 Flink v1.18 与 v1.19 两个模块，使这两个版本的 Iceberg Flink source 支持 Flink SQL 的 source watermark 机制，从而能在 Flink SQL 中使用基于 `SOURCE_WATERMARK()` 的窗口操作（如 `TUMBLE`）。

Flink 的窗口聚合（如 `TUMBLE(TABLE t, DESCRIPTOR(eventTS), INTERVAL '1' SECOND)`）要求源表提供一个 watermark 定义。Flink SQL 允许通过 `WATERMARK FOR eventTS AS SOURCE_WATERMARK()` 声明直接使用 source 自身产生的水印，但这要求 `ScanTableSource` 实现 `SupportsSourceWatermark` 接口，由 Flink 在优化时调用 `applySourceWatermark()` 来激活 source 端 watermark 生成。

此前 Iceberg 的 `IcebergTableSource` 未实现 `SupportsSourceWatermark`，因此无法在 Flink SQL 中用 `SOURCE_WATERMARK()` 做窗口聚合。本提交让 `IcebergTableSource` 实现 `SupportsSourceWatermark` 接口，并在 `applySourceWatermark()` 中校验前置条件：必须启用 FLIP-27 source（`TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE=true`）且配置了 `watermark-column` 读选项（指定用哪一列作为 watermark 来源）。这两个校验通过后，Iceberg 的 FLIP-27 source 会在运行时基于指定列生成 watermark，使 Flink SQL 窗口查询可用。

## 如何达成设计目的

设计思路是让 `IcebergTableSource` 声明支持 `SupportsSourceWatermark` 能力，并在 `applySourceWatermark()` 回调中做必要的前置校验，将实际 watermark 生成委托给已存在的 FLIP-27 source 实现（`watermark-column` 读选项已在 source 侧支持）。具体：
1. `IcebergTableSource` 的 `implements` 列表新增 `SupportsSourceWatermark`。
2. 实现 `applySourceWatermark()`：校验 FLIP-27 source 已启用、`watermark-column` 已配置，否则抛出明确错误。
3. 测试侧新增流式 `TableEnvironment` 工具方法，并补充无效配置（缺 watermark-column）与有效配置（TUMBLE 窗口查询）的测试。

改动对 Flink v1.18 与 v1.19 两个模块完全相同。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java` (修改, +16/-1 lines)

**修改目的**：声明并实现 SupportsSourceWatermark 能力。

**工作逻辑**：
- 新增 import `SupportsSourceWatermark` 与 `FlinkReadOptions`。
- 类声明 `implements` 列表新增 `SupportsSourceWatermark`（与 `ScanTableSource`、`SupportsProjectionPushDown`、`SupportsFilterPushDown`、`SupportsLimitPushDown` 并列）。
- 新增 `applySourceWatermark()` 方法（接口要求）：
  - `Preconditions.checkArgument(readableConfig.get(TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE), "Source watermarks are supported only in flip-27 iceberg source implementation")`：强制要求使用 FLIP-27 source（旧版 source 不支持 watermark）。
  - `Preconditions.checkNotNull(properties.get(FlinkReadOptions.WATERMARK_COLUMN), "watermark-column needs to be configured to use source watermark.")`：要求配置 `watermark-column` 读选项指定 watermark 来源列。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestSqlBase.java` (修改, +15 lines)

**修改目的**：提供流式 TableEnvironment 工具方法。

**工作逻辑**：新增 `streamingTEnv` 字段与 `getStreamingTableEnv()` 方法（双重检查锁懒初始化），用 `EnvironmentSettings.inStreamingMode()` 创建流式 TableEnvironment，供 watermark 测试使用（watermark 与窗口操作需在流模式下执行）。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java` (修改, +54/-1 lines)

**修改目的**：新增 source watermark 的有效/无效配置测试。

**工作逻辑**：
- `before()` 中同时配置 batch 与 streaming 两个 TableEnvironment（提取 `setUpTableEnv` 静态方法复用配置，含启用 FLIP-27 source、禁用并行度推断等）。
- 新增 `@AfterEach after()`：测试后删除表以避免干扰后续测试。
- `testWatermarkInvalidConfig`：建表后用 `WATERMARK FOR eventTS AS SOURCE_WATERMARK()` 创建 Flink 表但不设 `watermark-column`，执行 `SELECT *` 时断言抛出 `NullPointerException`，消息为 `watermark-column needs to be configured to use source watermark.`（验证 `applySourceWatermark` 的校验）。
- `testWatermarkValidConfig`：建表后用 `WITH ('watermark-column'='t1')` 与 `SOURCE_WATERMARK()` 创建 Flink 表，执行 `SELECT t1, t2 FROM TABLE(TUMBLE(TABLE flink_table, DESCRIPTOR(eventTS), INTERVAL '1' SECOND))` 窗口聚合查询，用 `TestHelpers.assertRecordsWithOrder` 验证结果与预期记录一致（验证有效配置下窗口查询正常工作）。

### `flink/v1.19/flink/...` 下同名三个文件 (同上)

**修改目的**：对 Flink v1.19 模块应用完全相同的修改。

**工作逻辑**：v1.19 与 v1.18 改动一致。

## 总结

本次提交将 PR #12191 回溯到 Flink v1.18 与 v1.19 模块，使 Iceberg Flink source 支持 Flink SQL 的 source watermark 机制。核心改动是让 `IcebergTableSource` 实现 `SupportsSourceWatermark` 接口，在 `applySourceWatermark()` 中校验 FLIP-27 source 已启用且 `watermark-column` 已配置，使 Flink SQL 能用 `WATERMARK FOR ... AS SOURCE_WATERMARK()` 声明并执行 `TUMBLE` 等窗口聚合。新增流式 TableEnvironment 测试工具与有效/无效配置的回归测试，两个 Flink 版本模块改动一致。
