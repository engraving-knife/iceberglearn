# 提交 1885：Flink: Support source watermark for flink sql windows (#12191)

## 提交信息

- **序号**：1885 / 4088
- **哈希**：2c746e6785a392fe0abc0eb9f0ec3e3dd674f4d7
- **短哈希**：2c746e678
- **日期**：2025-03-19 20:29:16 +0100
- **作者**：Swapna Marru
- **提交说明**：Flink: Support source watermark for flink sql windows (#12191)
- **PR/Issue**：#12191

## 总体目的

本提交为 Flink Iceberg source 添加对 Flink SQL 窗口函数所需的水印（watermark）支持，使 Iceberg 表能作为源表参与 Flink SQL 的窗口聚合（如 TUMBLE/HOP 等）。

背景：Flink SQL 的窗口函数（如 `TUMBLE(TABLE t, DESCRIPTOR(eventTS), INTERVAL '1' SECOND)`）要求源表定义 watermark。Flink 提供了 `SOURCE_WATERMARK()` 机制，允许 source 自行产生 watermark 而非由 SQL DDL 中的 watermark 表达式生成。要支持这一机制，TableSource 需实现 `SupportsSourceWatermark` 接口。

此前 Iceberg 的 `IcebergTableSource` 未实现该接口，因此无法在 Flink SQL 中使用 `WATERMARK FOR eventTS AS SOURCE_WATERMARK()` 语法，导致基于 Iceberg 源表的窗口聚合无法工作。本提交通过让 `IcebergTableSource` 实现 `SupportsSourceWatermark` 并在 `applySourceWatermark` 中校验配置，使该功能可用。

## 如何达成设计目的

1. **实现 SupportsSourceWatermark**：`IcebergTableSource` 新增实现 `SupportsSourceWatermark` 接口，并实现 `applySourceWatermark()` 方法。

2. **配置校验**：`applySourceWatermark()` 中做两项校验：
   - 必须启用 FLIP-27 source（`TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE`），因为只有 FLIP-27 source 实现支持 source watermark。
   - 必须配置 `watermark-column`（`FlinkReadOptions.WATERMARK_COLUMN`），指定从哪个时间列产生 watermark。若未配置则抛出 NPE 并给出明确消息。

3. **测试基础设施**：在 `TestSqlBase` 中新增 streaming 模式的 `TableEnvironment`（`getStreamingTableEnv`），因为窗口查询需要在流模式下执行。在 `TestIcebergSourceSql` 的 `before` 中同时对批和流 TableEnv 进行配置。

4. **测试用例**：新增两个测试——无效配置（未设 watermark-column）抛出异常；有效配置（设 watermark-column='t1'）成功执行 TUMBLE 窗口查询。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java` (修改, +14/-1 lines)

**修改目的**：实现 SupportsSourceWatermark 接口以支持 Flink SQL source watermark。

**工作逻辑**：
- 导入 `SupportsSourceWatermark` 和 `FlinkReadOptions`。
- 类声明新增 `implements ..., SupportsSourceWatermark`。
- 实现 `applySourceWatermark()` 方法：
  - 校验 `readableConfig.get(TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE)` 为 true，否则抛 `IllegalArgumentException`（"Source watermarks are supported only in flip-27 iceberg source implementation"）。
  - 校验 `properties.get(FlinkReadOptions.WATERMARK_COLUMN)` 不为 null，否则抛 `NullPointerException`（"watermark-column needs to be configured to use source watermark."）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java` (修改, +50/-2 lines)

**修改目的**：测试 source watermark 在无效/有效配置下的行为。

**工作逻辑**：
- `before` 方法重构：提取 `setUpTableEnv(TableEnvironment)` 静态方法，对批和流 TableEnv 都进行配置（启用 FLIP-27、禁用并行度推断等）。
- 新增 `@AfterEach after()` 方法删除测试表。
- `testWatermarkInvalidConfig`：创建 Iceberg 表和 Flink 表（`WATERMARK FOR eventTS AS SOURCE_WATERMARK()` 但不设 watermark-column），执行 SELECT 时断言抛出 NPE，消息为 "watermark-column needs to be configured to use source watermark."。
- `testWatermarkValidConfig`：创建 Iceberg 表并插入数据，创建 Flink 表（`WITH ('watermark-column'='t1')` + `SOURCE_WATERMARK()`），执行 TUMBLE 窗口查询 `TABLE(TUMBLE(TABLE flink_table, DESCRIPTOR(eventTS), INTERVAL '1' SECOND))`，断言返回预期记录。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestSqlBase.java` (修改, +13/-0 lines)

**修改目的**：提供 streaming 模式的 TableEnvironment。

**工作逻辑**：新增 `streamingTEnv` 字段和 `getStreamingTableEnv()` 方法（双重检查锁，`EnvironmentSettings.inStreamingMode()`），供需要流模式的测试使用。

## 总结

本提交为 Flink Iceberg source 添加 `SupportsSourceWatermark` 实现，使 Iceberg 表能通过 `SOURCE_WATERMARK()` 语法参与 Flink SQL 窗口聚合。`applySourceWatermark` 校验必须启用 FLIP-27 source 且配置 watermark-column。新增 streaming TableEnv 测试基础设施和两个测试用例（无效配置抛异常、有效配置成功执行 TUMBLE 窗口查询）。
