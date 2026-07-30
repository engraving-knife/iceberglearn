# 提交 3667：ORC: Add _row_id and _last_updated_sequence_number reader in Orc to support lineage (#15776)

## 提交信息

- **序号**：3667 / 4088
- **哈希**：77e7dbb245908b42277130eb3417d823b5932876
- **短哈希**：77e7dbb24
- **日期**：2026-05-08 15:46:29 +0200
- **作者**：GuoYu
- **提交说明**：ORC: Add _row_id and _last_updated_sequence_number raeder in Orc to support lineage (#15776)
- **PR/Issue**：#15776

## 总体目的

这个提交为 Iceberg 的 ORC 读取器添加了对 `_row_id` 和 `_last_updated_sequence_number` 元数据列的读取支持，以支持 lineage（数据血缘）场景。

Iceberg 的 lineage 功能需要在读取数据时获取每行的 `_row_id`（行唯一标识）和 `_last_updated_sequence_number`（最后更新的序列号）元数据列。这些列在 Parquet 读取器中已支持，但 ORC 读取器此前未支持，导致 ORC 格式的表无法使用 lineage 功能。

此外，原 ORC `StructReader` 使用基于位置的绑定（position-based binding），在 MOR（Merge-on-Read）和 lineage 场景下，当投影的 struct 字段顺序与文件 schema 顺序不一致时会导致字段错位。本提交引入基于 field-id 的绑定机制，解决了这一问题。

## 如何达成设计目的

1. 在 `OrcValueReaders.StructReader` 中新增基于 `TypeDescription` 的构造函数，使用 field-id 而非位置来绑定 reader 与 ORC schema 字段，并新增 `orcFieldIndex` 映射数组。
2. 新增 `RowIdReader` 和处理 `_last_updated_sequence_number` 的逻辑，支持从 ORC 文件中读取这些元数据列。
3. 在 `ORC.java` 的 build 方法中，从排除的元数据字段集合中移除 `ROW_ID` 和 `LAST_UPDATED_SEQUENCE_NUMBER`，使其能被读取。
4. 更新各引擎（Spark、Flink、Generic）的 ORC reader 适配新的构造函数。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/OrcValueReaders.java` (+202/-16 lines)

**修改目的**：新增基于 field-id 的 StructReader 和元数据列 reader。

**工作逻辑**：
1. 原有基于位置的构造函数标记为 `@Deprecated`，新增基于 `TypeDescription` 的构造函数。
2. 新增 `orcFieldIndex` 数组，将每个投影字段位置映射到 ORC schema 的 child index。
3. `readersByFieldId()` 和 `buildFieldIdToOrcIndex()` 方法建立 field-id 到 reader 和 ORC index 的映射。
4. `handleRowIdField()`：处理 `_row_id` 字段，创建 `RowIdReader`（结合 firstRowId 和文件中的 file id reader）。
5. `handleLastUpdatedSeqField()`：处理 `_last_updated_sequence_number` 字段。
6. 新增 `RowIdReader` 内部类，读取行 ID。

### `orc/src/main/java/org/apache/iceberg/orc/ORC.java` (+8/-2 lines)

**修改目的**：允许 `_row_id` 和 `_last_updated_sequence_number` 被读取。

**工作逻辑**：
```java
Set<Integer> idsToExclude =
    Sets.difference(
        Sets.union(constantFieldIds, MetadataColumns.metadataFieldIds()),
        ImmutableSet.of(
            MetadataColumns.ROW_ID.fieldId(),
            MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER.fieldId()));
return new OrcIterable<>(file, conf, TypeUtil.selectNot(schema, idsToExclude), ...);
```
从排除集合中移除这两个字段，使其保留在读取 schema 中。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcReaders.java` (+30 lines)

**修改目的**：适配新的 StructReader 构造函数。

### `core/src/main/java/org/apache/iceberg/data/orc/GenericOrcReader.java` (+1/-1 line)

**修改目的**：适配新构造函数。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReader.java` (+1/-1 line)

**修改目的**：适配新构造函数。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReaders.java` (+12/-3 lines)

**修改目的**：适配新构造函数并支持元数据列。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcReader.java` (+1/-1 line)

**修改目的**：适配新构造函数。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcValueReaders.java` (+12/-3 lines)

**修改目的**：适配新构造函数并支持元数据列。

### 测试文件

- `core/src/test/java/org/apache/iceberg/data/orc/TestGenericData.java`：适配测试。
- `core/src/test/java/org/apache/iceberg/maintenance/api/TestRewriteDataFiles.java`、`OperatorTestBase.java`：lineage 集成测试。
- `core/src/test/java/org/apache/iceberg/TestRowLevelOperationsWithLineage.java` (+12 lines)：新增 lineage 测试。

## 总结

这个提交为 ORC 读取器添加了 `_row_id` 和 `_last_updated_sequence_number` 元数据列的读取支持，使 ORC 格式的表也能使用 Iceberg 的 lineage 功能。同时将 ORC StructReader 从基于位置的绑定改为基于 field-id 的绑定，解决了 MOR 和 lineage 场景下字段错位的问题。改动覆盖 ORC 核心、Generic/Flink/Spark 三种 reader 适配，并附带 lineage 集成测试。后续通过 #16256 backport。
