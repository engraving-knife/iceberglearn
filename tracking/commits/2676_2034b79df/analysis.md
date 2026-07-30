# 提交 2676：Flink: Backport add _row_id and _last_updated_sequence_number readers to 2.1 and 1.20 (#14168)

## 提交信息

- **序号**：2676 / 4088
- **哈希**：2034b79dfd33519e292d52e711f4cf44c09b8a06
- **短哈希**：2034b79df
- **日期**：2025-09-23 09:34:15 -0600
- **作者**：GuoYu
- **提交说明**：Flink: Backport add _row_id and _last_updated_sequence_number readers to 2.1 and 1.20 (#14168)
- **PR/Issue**：#14168（backport 自 #14148）

## 总体目的

本提交是 PR #14148（提交 2673）的 backport，将 Flink Parquet 读取器对 `_row_id` 和 `_last_updated_sequence_number` 元数据列的读取支持，从 Flink 2.0 模块同步到 Flink 2.1 和 Flink 1.20 两个模块。

Iceberg 的 Flink 适配按 Flink 版本维护了独立的模块（`flink/v1.20`、`flink/v2.0`、`flink/v2.1`），各模块的 `FlinkParquetReaders`、`TestHelpers` 和 `TestFlinkParquetReader` 代码结构相同但分别维护。提交 2673 仅修改了 `flink/v2.0` 模块，本提交将完全相同的修改应用到 `flink/v1.20` 和 `flink/v2.1` 两个模块，确保三个 Flink 版本的读取器行为一致。

## 如何达成设计目的

将提交 2673 对 `flink/v2.0` 模块的三处修改（`FlinkParquetReaders.java`、`TestHelpers.java`、`TestFlinkParquetReader.java`）原样复制到 `flink/v1.20` 和 `flink/v2.1` 对应的文件中。六个文件的改动内容与 2673 完全一致。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+60/-50 lines)

**修改目的**：为 Flink 1.20 模块添加 `_row_id` 和 `_last_updated_sequence_number` 读取支持。

**工作逻辑**：与提交 2673 中 `flink/v2.0` 的修改完全相同。重构 `struct()` 方法，将元数据列处理委托给 `ParquetValueReaders.replaceWithMetadataReader()`，提取 `defaultReader()` 辅助方法，移除 `typesById` 和 `maxDefinitionLevelsById` 局部映射，新增 null expected 防御。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java` (+56/-14 lines)

**修改目的**：增强 Flink 1.20 测试断言以支持行溯源元数据列。

**工作逻辑**：与提交 2673 相同。新增带 `idToConstant` 和 `rowPosition` 参数的 `assertRowData` 重载，新增 `getExpectedValue()` 方法处理 `_row_id`（按行递增）和 `_last_updated_sequence_number`（常量）的期望值计算。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+25/-16 lines)

**修改目的**：启用 Flink 1.20 行溯源测试。

**工作逻辑**：与提交 2673 相同。新增 `supportsRowLineage()` 返回 true，改用 `InMemoryOutputFile`，传入 `ID_TO_CONSTANT` 调用 `buildReader` 和 `assertRowData`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+60/-50 lines)

**修改目的**：为 Flink 2.1 模块添加相同支持。逻辑与上述 v1.20 完全一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java` (+56/-14 lines)

**修改目的**：增强 Flink 2.1 测试断言。逻辑与 v1.20 完全一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+25/-16 lines)

**修改目的**：启用 Flink 2.1 行溯源测试。逻辑与 v1.20 完全一致。

## 总结

本提交是提交 2673（PR #14148）的 backport，将 Flink Parquet 读取器对 `_row_id` 和 `_last_updated_sequence_number` 行溯源元数据列的读取支持从 Flink 2.0 同步到 Flink 1.20 和 Flink 2.1 两个模块。三个 Flink 版本模块的修改内容完全一致，确保了跨版本的行为一致性。这体现了 Iceberg 多版本引擎适配中 backport 操作的典型模式——同一功能需要在所有维护的引擎版本模块中同步落地。
