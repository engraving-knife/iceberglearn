# 提交 1963：Core, Spark: Add row lineage metadata columns, and surface them in SparkTable metadata columns (#12596)

## 提交信息

- **序号**：1963 / 4088
- **哈希**：0152075268851d397b60bcb66e5a7c346271dfd9
- **短哈希**：015207526
- **日期**：2025-04-04 08:37:48 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core, Spark: Add row lineage metadata columns, and surface them in SparkTable metadata columns (#12596)
- **PR/Issue**：#12596

## 总体目的

Iceberg v3 规范引入了行血缘（row lineage）能力，包含两个隐式元数据列：`_row_id`（行的唯一标识）与 `_last_updated_sequence_number`（行最后更新的序列号）。在此之前，Core 模块的 `MetadataColumns` 还没有定义这两个列，Spark 的 `SparkTable.metadataColumns()` 也没有把它们暴露给用户查询。

本提交的目的：
1. 在 Core 的 `MetadataColumns` 中新增 `_row_id` 与 `_last_updated_sequence_number` 两个元数据列定义，并注册到元数据列名映射与元数据字段 id 集合中，使它们能被识别为元数据列（避免与用户列冲突、可用于读取投影等）。
2. 在 Spark 3.5 的 `SparkTable.metadataColumns()` 中，当表格式版本 >= 3 时，把这两个行血缘元数据列暴露为 Spark 的 `SparkMetadataColumn`，使 Spark SQL 能直接 `SELECT _row_id, _last_updated_sequence_number` 查询；v1/v2 表则不暴露（查询会报解析错误）。

## 如何达成设计目的

设计上遵循现有元数据列的注册模式：

1. **`MetadataColumns`**：定义两个 `NestedField.optional`，分别使用字段 id `Integer.MAX_VALUE - 107`（`_row_id`）与 `Integer.MAX_VALUE - 108`（`_last_updated_sequence_number`），与规范中 metadata columns 表的 id（2147483540 / 2147483539）对应。把两者加入 `META_COLUMNS` 名字映射与 `META_IDS` id 集合，使 `MetadataColumns.metadataFieldIds()` 等方法能识别它们。
2. **`SparkTable.metadataColumns()`**：基础元数据列（`_spec_id`/`_partition`/`_file_path`/`_row_position`/`_is_deleted`）始终暴露；当 `TableUtil.formatVersion(table()) >= 3` 时，额外追加 `_row_id` 与 `_last_updated_sequence_number`（均为 `LongType`，nullable=true）。用 `ImmutableList.Builder` 构建列表后转为数组返回。
3. **测试**：在 `TestSparkMetadataColumns` 的参数化矩阵中加入 format version 3 的组合，并新增 `testRowLineageColumnsResolvedInV3OrHigher`：v3 表对空表查询两个行血缘列应返回空结果；v1/v2 表查询应抛 `AnalysisException`（列无法解析）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataColumns.java` (修改, +20/-2 lines)

**修改目的**：定义行血缘元数据列并注册。

**工作逻辑**：
- 新增 `ROW_ID = NestedField.optional(Integer.MAX_VALUE - 107, "_row_id", LongType, "Implicit row ID that is automatically assigned")`。
- 新增 `LAST_UPDATED_SEQUENCE_NUMBER = NestedField.optional(Integer.MAX_VALUE - 108, "_last_updated_sequence_number", LongType, "Sequence number when the row was last updated")`。
- 把两者加入 `META_COLUMNS` 名字映射与 `META_IDS` id 集合，使它们被识别为元数据列。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (修改, +26/-9 lines)

**修改目的**：在 v3 表中暴露行血缘元数据列。

**工作逻辑**：
- `metadataColumns()` 改用 `ImmutableList.Builder<SparkMetadataColumn>` 构建列表，先添加基础五个元数据列。
- 若 `TableUtil.formatVersion(table()) >= 3`，再追加：
  - `new SparkMetadataColumn(MetadataColumns.ROW_ID.name(), DataTypes.LongType, true)`
  - `new SparkMetadataColumn(MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER.name(), DataTypes.LongType, true)`
- 返回 `metadataColumns.build().toArray(SparkMetadataColumn[]::new)`。
- 引入 `TableUtil` 与 `ImmutableList` 的 import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java` (修改, +28/-0 lines)

**修改目的**：覆盖 v3 表行血缘列的暴露与 v1/v2 的拒绝。

**工作逻辑**：
- 参数化矩阵 `FORMAT_VERSION` 增加 format version 3 的组合（PARQUET/AVRO/ORC）。
- 新增 `testRowLineageColumnsResolvedInV3OrHigher`：
  - formatVersion >= 3：对空表 `SELECT _row_id, _last_updated_sequence_number, id` 应返回空列表（验证列可解析）。
  - 否则：`SELECT _row_id` 与 `SELECT _last_updated_sequence_number` 应抛 `AnalysisException`，消息包含 "A column or function parameter with name ... cannot be resolved"。

## 总结

本提交在 Core 的 `MetadataColumns` 中新增 v3 行血缘元数据列 `_row_id`（字段 id MAX_VALUE-107）与 `_last_updated_sequence_number`（MAX_VALUE-108），并注册到元数据列映射；在 Spark 3.5 的 `SparkTable.metadataColumns()` 中，当表格式版本 >= 3 时把这两个列作为 `SparkMetadataColumn` 暴露，使用户可通过 Spark SQL 查询行血缘信息，v1/v2 表则不暴露。测试覆盖了 v3 的可解析与 v1/v2 的拒绝场景。
