# 提交 2300：Avro: Support row lineage inheritance for planned avro reader (#13070)

## 提交信息

- **序号**：2300 / 4088
- **哈希**：e64181d99bc58d8482d629e7945ce2510287bf74
- **短哈希**：e64181d99
- **日期**：2025-07-01 10:42:16 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Avro: Support row lineage inheritance for planned avro reader (#13070)
- **PR/Issue**：#13070

## 总体目的

本提交为 Iceberg 的 Avro 读取器添加行谱系（Row Lineage）继承支持。行谱系是 Iceberg v3 表格式的核心特性，通过为每行数据分配全局唯一的行 ID（`_row_id`）和最后更新序列号（`_last_updated_sequence_number`）来支持行级操作的正确性。

行谱系继承的核心思想是：当数据文件中没有显式存储行 ID 时，读取器可以通过文件的 `first_row_id`（从 Manifest 文件继承）加上行在文件中的位置来计算行 ID。类似地，如果文件中没有存储行的最后更新序列号，可以使用文件所属快照的序列号作为默认值。

此前，Parquet 读取器已支持行谱系继承，但 Avro 的计划读取器（Planned Avro Reader）尚未支持。本提交填补了这一空白，使 Avro 格式也能正确处理行谱系继承。

## 如何达成设计目的

设计通过在 `ValueReaders` 中添加两个专门的读取器来实现：

1. **`RowIdReader`**：读取行 ID 字段。如果数据文件中已存储行 ID（`idFromFile`），直接返回；否则通过 `firstRowId + 行位置` 计算行 ID。
2. **`LastUpdatedSeqReader`**：读取最后更新序列号字段。如果数据文件中已存储序列号（`rowLastUpdatedSeqNumber`），直接返回；否则使用文件的序列号（`fileSeqNumber`）作为默认值。

在读取计划构建阶段（`readPlan` 方法），通过检查字段 ID 是否为 `ROW_ID` 或 `LAST_UPDATED_SEQUENCE_NUMBER` 元数据列，决定使用哪个专用读取器。这些元数据列的常量值（`firstRowId`、`fileSeqNumber`）通过 `idToConstant` 映射传入。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java` (+167/-23 lines)

**修改目的**：重构读取计划构建逻辑，添加行谱系继承支持。

**工作逻辑**：

原 `readPlan` 方法被拆分为多个职责明确的方法：
- `addFileFieldReadersToPlan`：处理数据文件中存在的字段的读取器
- `addMissingFileReadersToPlan`：处理数据文件中缺失的字段的读取器
- `fileFieldReader`：根据字段 ID 选择合适的读取器（普通、RowId、LastUpdated）
- `fieldReader`：普通字段的读取器选择逻辑
- `createMissingFieldReader`：缺失字段的读取器创建逻辑

新增两个读取器类：

**`RowIdReader`**：实现 `ValueReader<Long>` 和 `SupportsRowPosition` 接口。
- 构造时接收 `firstRowId` 和原始的 `idReader`
- `read` 方法：先读取文件中的行 ID（`idFromFile`），再读取行位置（`pos`）。如果 `idFromFile` 非空则返回它，否则返回 `firstRowId + pos`（通过位置计算行 ID）
- 支持 `setRowPositionSupplier`，用于设置行位置供应器

**`LastUpdatedSeqReader`**：实现 `ValueReader<Long>` 接口。
- 构造时接收 `fileSeqNumber` 和原始的 `seqReader`
- `read` 方法：先读取文件中的序列号。如果非空则返回它，否则返回 `fileSeqNumber` 作为默认值

新增工厂方法 `rowIds(baseRowId, idReader)` 和 `lastUpdated(baseRowId, fileSeqNumber, seqReader)`，当常量值为 null 时返回 `ValueReaders.constant(null)`（表示行谱系不可用）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (+27/-6 lines)

**修改目的**：扩展行谱系测试以支持 Avro 格式。

**工作逻辑**：移除了 `@BeforeEach` 中的 `assumeThat(fileFormat).isEqualTo(FileFormat.PARQUET)` 限制（此前只测试 Parquet），添加了使用 Avro 格式和 SparkSessionCatalog 的新测试参数组。在 `latestSnapshot` 方法中添加 `table.refresh()` 确保获取最新的快照状态。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkAvroReader.java` (+33/-10 lines)

**修改目的**：更新 Avro 读取器测试以支持行谱系继承。

**工作逻辑**：重构 `writeAndValidate` 方法，从使用 `InMemoryOutputFile` 改为使用临时文件，从使用 `FileAppender` 改为使用 `DataWriter`（支持写入分区信息和元数据）。读取时使用 `SparkPlannedAvroReader.create(schema, ID_TO_CONSTANT)` 传入常量映射。新增 `supportsRowLineage()` 方法返回 `true`，启用行谱系相关测试。

## 总结

本提交为 Avro 格式的计划读取器添加了行谱系继承支持，使 Avro 格式能够像 Parquet 格式一样正确处理行 ID 和最后更新序列号的继承。核心实现是两个新的读取器类 `RowIdReader` 和 `LastUpdatedSeqReader`，它们在数据文件中没有显式存储这些元数据时，通过文件级别的常量值和行位置进行计算。这完善了 Iceberg v3 行谱系特性在多文件格式间的支持一致性。
