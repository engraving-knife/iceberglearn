# 提交 3584：Data: Add TCK tests for Metadata Columns in BaseFormatModelTests (#15675)

## 提交信息

- **序号**：3584 / 4088
- **哈希**：03347ff6c971d2785dbd12956f27a2627c9558fe
- **短哈希**：03347ff6c
- **日期**：2026-04-24 22:34:56 +0200
- **作者**：GuoYu
- **提交说明**：Data: Add TCK tests for Metadata Columns in BaseFormatModelTests (#15675)
- **PR/Issue**：#15675

## 总体目的

该提交为 Iceberg 数据格式的 TCK（技术兼容性套件）添加了元数据列（Metadata Columns）和模式演进（Schema Evolution）相关测试。元数据列是 Iceberg 的特殊列，不存储在数据文件中，而是在读取时由引擎动态填充，包括 `FILE_PATH`（文件路径）、`SPEC_ID`（分区规范 ID）、`ROW_POSITION`（行位置）、`IS_DELETED`（是否删除）等。

该提交确保不同格式实现（Parquet、ORC、Avro）正确支持元数据列的读取，以及模式演进场景（新增带默认值的列）。新增了两个功能特性标记：`FEATURE_READER_DEFAULT`（读取时默认值支持）和 `FEATURE_META_ROW_LINEAGE`（行级血缘元数据支持），其中 ORC 格式不支持这两个特性。

## 如何达成设计目的

在 `BaseFormatModelTests` 中新增多个参数化测试方法，覆盖：
1. 元数据列读取：`FILE_PATH`、`SPEC_ID`、`ROW_POSITION`、`IS_DELETED` 等。
2. 模式演进：新增带初始默认值的列后，读取旧数据时使用默认值。
3. 投影读取：读取部分列时正确返回元数据列值。
4. 行级血缘：验证行级血缘元数据的读取。

各格式实现通过 `TestFlinkFormatModel` 和 `TestSparkFormatModel` 子类覆写或标记支持/不支持的特性。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+534/-16 lines)

**修改目的**：添加元数据列和模式演进的 TCK 测试。

**工作逻辑**：
- 新增抽象方法 `convertConstantToEngine`，子类需实现将常量转换为引擎特定类型。
- 新增特性标记 `FEATURE_READER_DEFAULT` 和 `FEATURE_META_ROW_LINEAGE`。
- 更新 `MISSING_FEATURES`：ORC 不支持 `FEATURE_META_ROW_LINEAGE` 和 `FEATURE_READER_DEFAULT`。
- 新增测试方法：
  - `testReaderSchemaEvolutionNewColumnWithDefault`：写入旧 schema 数据，用新增带默认值列的 evolved schema 读取，验证新列使用默认值。
  - `testReadMetadataColumnFilePath`：读取 `FILE_PATH` 元数据列。
  - `testReadMetadataColumnSpecId`：读取 `SPEC_ID` 元数据列。
  - `testReadMetadataColumnRowPosition`：读取 `ROW_POSITION` 元数据列。
  - `testReadMetadataColumnIsDeleted`：读取 `IS_DELETED` 元数据列。
  - `testReadMultipleMetadataColumns`：同时读取多个元数据列。
  - `testReadMetadataColumnWithDataColumns`：混合读取数据列和元数据列。
- 新增辅助方法 `readAndAssertMetadataColumn`、`copy` 等。
- 投影读取逻辑优化：使用 `copy(record, projectedSchema, projectedSchema)` 替代 `projectRecords`。

### `flink/v1.20/...`、`flink/v2.0/...`、`flink/v2.1/...` 的 `TestFlinkFormatModel.java` (+26/-0 lines each)

**修改目的**：Flink 格式模型测试适配新增的抽象方法和特性。

**工作逻辑**：
实现 `convertConstantToEngine` 方法，将 Iceberg 常量转换为 Flink 引擎类型。

### `spark/v3.4/...`、`spark/v3.5/...`、`spark/v4.0/...`、`spark/v4.1/...` 的 `TestSparkFormatModel.java` (+7/-0 lines each)

**修改目的**：Spark 格式模型测试适配新增的抽象方法。

**工作逻辑**：
实现 `convertConstantToEngine` 方法，将 Iceberg 常量转换为 Spark 引擎类型。

## 总结

该提交为 Iceberg 数据格式 TCK 添加了元数据列和模式演进的全面测试，确保各格式实现正确支持 `FILE_PATH`、`SPEC_ID`、`ROW_POSITION`、`IS_DELETED` 等元数据列的读取，以及新增带默认值列的模式演进场景。这些测试是格式兼容性验证的重要组成部分，覆盖了 Flink 和 Spark 两个引擎的格式模型实现。
