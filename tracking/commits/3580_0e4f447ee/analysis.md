# 提交 3580：Data: Add TCK tests for metrics collection in BaseFormatModelTests (#15906)

## 提交信息

- **序号**：3580 / 4088
- **哈希**：0e4f447ee0d0d22effd49d7189540651af66a3c5
- **短哈希**：0e4f447ee
- **日期**：2026-04-24 14:14:39 +0200
- **作者**：GuoYu
- **提交说明**：Data: Add TCK tests for metrics collection in BaseFormatModelTests (#15906)
- **PR/Issue**：#15906

## 总体目的

该提交为 Iceberg 数据格式的技术兼容性套件（TCK, Technology Compatibility Kit）添加了指标收集（metrics collection）的测试用例。`BaseFormatModelTests` 是所有数据格式实现（Parquet、ORC、Avro）必须通过的基类测试，确保不同格式实现的行为一致。

指标收集是 Iceberg 的重要特性，在写入数据文件时收集列级别的统计信息（值计数、null 计数、上下界、列大小等），用于查询优化时的文件裁剪。该提交添加了对不同 `MetricsMode`（None、Counts、Truncate、Full）下指标收集行为的全面测试，验证各格式实现是否正确收集和报告指标。同时新增了两个功能特性标记：`FEATURE_COLUMN_LEVEL_METRICS`（列级指标）和 `FEATURE_COLUMN_METRICS_TRUNCATE_BINARY`（二进制截断指标）。

## 如何达成设计目的

在 `BaseFormatModelTests` 中新增多个参数化测试方法（针对 Avro、Parquet、ORC 三种格式），分别测试：
1. 默认模式（Full）下的指标收集：验证计数、边界、列大小。
2. None 模式：验证所有指标为 null。
3. Counts 模式：验证值计数和 null 计数存在，但边界为 null。
4. Truncate 模式（字符串和二进制）：验证边界值被正确截断。

通过 `MISSING_FEATURES` map 标记各格式不支持的特性，Avro 不支持列级指标和二进制截断，ORC 不支持二进制截断。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+581/-3 lines)

**修改目的**：添加指标收集的 TCK 测试。

**工作逻辑**：
- 新增 `@TempDir` 表目录、`MetricsConfig`/`MetricsModes` 等导入。
- 新增两个特性标记：`FEATURE_COLUMN_LEVEL_METRICS` 和 `FEATURE_COLUMN_METRICS_TRUNCATE_BINARY`。
- 更新 `MISSING_FEATURES`：Avro 排除列级指标和二进制截断；ORC 排除二进制截断。
- `afterEach` 中新增 `TestTables.clearTables()` 清理。
- 新增测试方法：
  - `testDataWriterMetricsCollection`：默认 Full 模式，验证 counts、bounds、columnSize。
  - `testDataWriterMetricsWithNoneMode`：None 模式，所有指标为 null。
  - `testDataWriterMetricsWithCountsMode`：Counts 模式，counts 存在但 bounds 为 null。
  - `testDataWriterMetricsWithTruncateMode`：字符串截断模式，验证下界截断为 "abcde"，上界截断并递增为 "abcdf"。
  - `testDataWriterMetricsWithTruncateModeForBinary`：二进制截断模式。
- 新增辅助方法：`assertCounts`、`assertBounds`、`assertColumnSize`、`assertCountsNull`、`assertBoundsNull`、`assertColumnSizeEmpty`、`assertTruncateBoundsForFirstColumn`、`writeGenericRecords`（带 MetricsConfig 参数的重载）、`config`（创建 MetricsConfig）等。

### `data/src/test/java/org/apache/iceberg/data/DataGenerators.java` (+12/-0 lines)

**修改目的**：为数据生成器添加额外支持。

**工作逻辑**：
为 TCK 测试的数据生成器添加必要的辅助方法或字段，支持指标收集测试中使用的数据生成需求。

## 总结

该提交为 Iceberg 数据格式 TCK 添加了全面的指标收集测试，覆盖了 Full、None、Counts、Truncate 四种 MetricsMode，以及字符串和二进制类型的截断边界验证。这些测试确保不同格式实现（Parquet、ORC、Avro）在指标收集方面的行为一致性，是格式兼容性验证的重要组成部分。
