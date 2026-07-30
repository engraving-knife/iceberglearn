# 提交 2256：Spark 3.4: Backport #12836 for row lineage support in spark parquet reader (#13353)

## 提交信息

- **序号**：2256 / 4088
- **哈希**：feeb045a73a315bd4e5104f770671108d44aef33
- **短哈希**：feeb045a7
- **日期**：2025-06-18 21:14:39 -0700
- **作者**：Drew Gallardo
- **提交说明**：Spark 3.4: Backport #12836 for row lineage support in spark parquet reader
- **PR/Issue**：#13353 (backports #12836)

## 总体目的

本提交将行级血缘（row lineage）支持回移植到 Spark 3.4 的 Parquet 读取器。行级血缘是 Iceberg 的一个特性，通过 `_row_id` 和 `_last_updated_sequence_number` 等元数据列来追踪数据行级别的变更历史。这些元数据列在读取 Parquet 文件时需要特殊处理，因为它们可能需要被替换为常量值（通过 `idToConstant` 映射）或在文件中不存在时提供默认值。

在原有的 `SparkParquetReaders` 实现中，元数据列（如 `ROW_POSITION`、`IS_DELETED`）的处理逻辑是内联在 struct 读取器中的，使用了多个临时 Map（`typesById`、`maxDefinitionLevelsById`）来跟踪字段信息。这种实现方式不够灵活，难以支持新的行级血缘元数据列。回移植 #12836 的改动重构了这一逻辑，使其能够通过 `ParquetValueReaders.replaceWithMetadataReader` 统一处理元数据列。

## 如何达成设计目的

- 重构 `SparkParquetReaders.struct()` 方法，简化字段读取器的构建逻辑，移除不再需要的临时 Map。
- 将元数据列（常量列、ROW_POSITION、IS_DELETED 等）的处理逻辑委托给 `ParquetValueReaders.replaceWithMetadataReader` 方法。
- 新增 `defaultReader()` 方法处理字段读取器的默认选择逻辑（使用现有 reader、initialDefault 或 nulls）。
- 在 `SparkBatch.supportsCometBatchReads()` 中排除 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 字段，因为这些元数据列不支持 Comet 批量读取。
- 增强 `AvroDataTest` 测试基类，新增行级血缘测试支持。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java` (修改, +60/-57 lines)

**修改目的**：重构 Parquet 读取器以支持行级血缘元数据列。

**工作逻辑**：
1. 在 `struct()` 方法开头新增 null 检查——当 expected 为 null 时返回空的 `InternalRowReader`，避免 NPE。
2. 移除了 `typesById` 和 `maxDefinitionLevelsById` 两个临时 Map，改为计算单个 `constantDefinitionLevel`（使用 `type.getMaxDefinitionLevel(currentPath())`）。
3. 字段读取器的选择逻辑从内联的 if-else 链改为调用 `ParquetValueReaders.replaceWithMetadataReader(id, reader, idToConstant, constantDefinitionLevel)` 和 `defaultReader(field, reader, constantDefinitionLevel)`。
4. 新增 `defaultReader()` 私有方法封装默认读取器选择逻辑：如果 reader 不为 null 则使用它，否则检查 `field.initialDefault()`，再否则对可选字段返回 nulls reader，对必填字段抛出异常。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (修改, +5/-1 lines)

**修改目的**：排除行级血缘元数据列的 Comet 批量读取。

**工作逻辑**：在 `supportsCometBatchReads()` 方法中新增两个条件判断：`field.fieldId() != MetadataColumns.ROW_ID.fieldId()` 和 `field.fieldId() != MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER.fieldId()`，确保 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 列不会使用 Comet 批量读取，因为这些元数据列需要特殊的读取逻辑。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java` (修改, +58/0 lines)

**修改目的**：在测试基类中添加行级血缘测试支持。

**工作逻辑**：
1. 新增 `ID_TO_CONSTANT` 常量 Map，映射 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 的 fieldId 到测试常量值。
2. 新增 `writeAndValidate(Schema, Schema, List<Record>)` 抽象方法重载，支持传入具体记录列表。
3. 新增 `supportsRowLineage()` 方法（默认返回 false），子类可覆盖以声明支持行级血缘。
4. 新增 `testRowLineage()` 测试方法，创建包含 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 的 schema，构造多条记录（部分带有元数据值，部分没有），验证读取器能正确处理这些混合情况。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java` (修改, +45/-XX lines)

**修改目的**：适配测试辅助工具以支持行级血缘测试中的记录验证。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReader.java` (修改, +45/-XX lines)

**修改目的**：在 Spark Parquet 读取器测试中实现行级血缘测试方法。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkTestHelperBase.java` (修改, +7/-XX lines)

**修改目的**：适配测试基类以支持行级血缘测试所需的配置。

## 总结

本提交将行级血缘（row lineage）支持回移植到 Spark 3.4 的 Parquet 读取器。核心改动是重构了 `SparkParquetReaders` 的字段读取器构建逻辑，将元数据列处理委托给统一的 `replaceWithMetadataReader` 方法，同时排除了 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 列的 Comet 批量读取支持。这为 Spark 3.4 用户提供了行级血缘追踪能力，是 UPDATE/MERGE 操作支持的基础设施。
