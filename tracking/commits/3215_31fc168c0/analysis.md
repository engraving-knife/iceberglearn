# 提交 3215：Spark 4.1: Introduce constants for Spark metadata columns (#15245)

## 提交信息

- **序号**：3215 / 4088
- **哈希**：31fc168c02782c017201b2943d7946cccbd08c80
- **短哈希**：31fc168c0
- **日期**：2026-02-06
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Introduce constants for Spark metadata columns (#15245)
- **PR/Issue**：#15245

## 总体目的

Iceberg 的 Spark 4.1 集成模块中，`SparkTable` 与 `SparkChangelogTable` 都实现了 `metadataColumns()`，用以向 Spark 声明该表可暴露的元数据列（如 `SPEC_ID`、`_partition`、`_file_path`、`_pos`、`_deleted`，以及行血缘启用时的 `_row_id` 与 `LAST_UPDATED_SEQUENCE_NUMBER`）。然而这两处对同一批元数据列的定义是各自内联用 `SparkMetadataColumn.builder()...build()` 现场构造的，存在明显的重复：同样的列名、Spark 数据类型、可空性、以及 `preserveOnReinsert/preserveOnUpdate/preserveOnDelete` 等行血缘保留标志，在两个类里被各写一遍，既冗长又容易随演进产生不一致（漂移）。

本提交新增一个集中式工具类 `SparkMetadataColumns`，把各 Spark 元数据列的定义固化为 `public static final` 常量（不可变描述符），并提供一个按表生成 `_partition` 列的 `partition(Table)` 工厂方法；随后将 `SparkChangelogTable` 与 `SparkTable` 的 `metadataColumns()` 改为引用这些常量，消除重复、保证两表定义一致、并显著缩短方法体。这同时为行血缘相关的 `ROW_ID`/`LAST_UPDATED_SEQUENCE_NUMBER` 提供了单一可复用定义，便于后续其它需要这些元数据列的场景直接引用。此次为纯重构，元数据列的定义（名称、类型、可空性、保留行为）与改动前逐一对应、行为不变。

## 如何达成设计目的

整体思路是"抽取常量 + 复用引用"。新增 `SparkMetadataColumns` 集中持有全部元数据列常量；`SparkChangelogTable` 与 `SparkTable` 的 `metadataColumns()` 不再内联构造，而是引用常量并组装为数组返回。涉及三个文件：`SparkMetadataColumns.java` 为新增工具类，`SparkChangelogTable.java` 与 `SparkTable.java` 为重构消费方。设计上把与具体表无关的列提为共享静态常量（不可变且可安全跨调用复用），把依赖表分区类型的 `_partition` 列保留为按表构造的工厂方法。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkMetadataColumns.java` (+86/-0)

**修改目的**：集中定义可复用的 Spark 元数据列常量。

**工作逻辑**：
新增工具类，私有构造。定义如下 `public static final SparkMetadataColumn` 常量，均基于 `MetadataColumns` 的列名与 Spark 类型构建：

- `SPEC_ID`：`MetadataColumns.SPEC_ID.name()`、`IntegerType`、可空。
- `FILE_PATH`：`MetadataColumns.FILE_PATH.name()`、`StringType`、非空。
- `ROW_POSITION`：`MetadataColumns.ROW_POSITION.name()`、`LongType`、非空。
- `IS_DELETED`：`MetadataColumns.IS_DELETED.name()`、`BooleanType`、非空。
- `ROW_ID`：`MetadataColumns.ROW_ID.name()`、`LongType`、可空，并设 `preserveOnReinsert(true)`、`preserveOnUpdate(true)`、`preserveOnDelete(false)`——即行 id 在重新插入与更新时保留、删除时丢弃。
- `LAST_UPDATED_SEQUENCE_NUMBER`：`LongType`、可空，三项 preserve 均为 `false`。

另提供静态方法 `partition(Table table)`：以 `MetadataColumns.PARTITION_COLUMN_NAME` 为名，数据类型由 `SparkSchemaUtil.convert(Partitioning.partitionType(table))` 按表分区类型转换得到，可空——因为分区类型依赖具体表，无法固化为单一常量，故保留为按表构造。`SparkMetadataColumn` 由 builder 构建为不可变描述符，故常量可安全跨调用共享。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogTable.java` (+5/-30)

**修改目的**：用常量替换 changelog 表的内联元数据列构造。

**工作逻辑**：
原 `metadataColumns()` 先用 `SparkSchemaUtil.convert(Partitioning.partitionType(icebergTable))` 计算分区类型，再内联构造 `SPEC_ID`、`PARTITION_COLUMN_NAME`、`FILE_PATH`、`ROW_POSITION`、`IS_DELETED` 五个 `SparkMetadataColumn`。现改为直接返回引用常量的数组：`{ SparkMetadataColumns.SPEC_ID, SparkMetadataColumns.partition(icebergTable), SparkMetadataColumns.FILE_PATH, SparkMetadataColumns.ROW_POSITION, SparkMetadataColumns.IS_DELETED }`。分区类型的计算随之移入 `SparkMetadataColumns.partition()`。移除不再需要的 `MetadataColumns`、`Partitioning`、`DataType`、`DataTypes` 等 import。定义与原先逐一对应，行为不变。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+12/-52)

**修改目的**：用常量替换 Spark 表的内联元数据列构造，统一两表定义。

**工作逻辑**：
原 `metadataColumns()` 用 `ImmutableList.Builder<SparkMetadataColumn>` 现场构造 5 个基础列，并在 `TableUtil.supportsRowLineage(icebergTable)` 为真时追加 `ROW_ID` 与 `LAST_UPDATED_SEQUENCE_NUMBER`（含 preserve 标志）两个行血缘列。现改为以 `List<SparkMetadataColumn> cols = Lists.newArrayList()` 收集：依次 `add` 五个基础常量（`SPEC_ID`、`partition(icebergTable)`、`FILE_PATH`、`ROW_POSITION`、`IS_DELETED`），行血缘启用时再 `add` `SparkMetadataColumns.ROW_ID` 与 `SparkMetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER`，最后 `toArray(SparkMetadataColumn[]::new)` 返回。从 `ImmutableList.Builder` 改为可变 `ArrayList` 仅为收集便利，对最终返回的数组语义无影响。移除 `Partitioning`、`ImmutableList`、`DataType`、`DataTypes` 等 import，新增 `List`、`Lists` import。常量与原内联定义逐一对应，行为不变；同时与 `SparkChangelogTable` 共用同一份常量，保证两表元数据列定义完全一致、不再有漂移风险。

## 总结

本提交通过新增 `SparkMetadataColumns` 常量类，把 Spark 4.1 模块中 `SparkTable` 与 `SparkChangelogTable` 重复内联构造的元数据列定义集中固化为可复用常量（含行血缘的 `ROW_ID`/`LAST_UPDATED_SEQUENCE_NUMBER` 保留行为），并让两表引用之。该重构消除了重复定义、保证两表元数据列一致性、缩短并清晰了 `metadataColumns()` 方法，为后续行血缘相关元数据列的复用奠定基础，且不改变任何既有行为。
