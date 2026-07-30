# 提交 0128：Spark 3.5: Use DataFile constants in SparkDataFile (#8936)

## 提交信息

- **序号**：0128 / 4088
- **哈希**：4a3d266a6946f00602cfddc809e5480f3139f352
- **短哈希**：4a3d266a6
- **日期**：2023-11-02 09:42:11 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Use DataFile constants in SparkDataFile (#8936)
- **PR/Issue**：#8936

## 总体目的

这个提交是一个代码质量改进，将 `SparkDataFile` 中大量硬编码的字符串字面量替换为 `DataFile` 接口中已定义的常量引用，消除字符串字面量与字段定义之间的隐式耦合，降低因字段名变更而产生不一致的风险。

`SparkDataFile` 是 Iceberg Spark 3.5 模块中用于将 Spark 的 `Row` 适配为 Iceberg `DataFile` 的实现类。它在构造时需要按字段名从 Iceberg 的 struct 类型中取出各字段的类型信息（如 `lower_bounds`、`upper_bounds`、`key_metadata`、`partition` 的类型），并在读取阶段按字段名从 Spark 的 `StructType` 中定位各字段的列位置（如 `file_path`、`file_format`、`record_count`、`file_size_in_bytes` 等共 14 个字段的位置）。这些字段名在 Iceberg 的 `DataFile` 接口中已经以 `Types.NestedField` 常量（如 `FILE_PATH`、`FILE_FORMAT`、`LOWER_BOUNDS` 等，可通过 `.name()` 获取字段名字符串）和字符串常量（如 `PARTITION_NAME = "partition"`）的形式集中定义。

本提交前，`SparkDataFile` 在这些位置直接书写字符串字面量（如 `"file_path"`、`"lower_bounds"`、`"partition"`）。这种做法的问题在于：字段名是"魔法字符串"，与 `DataFile` 接口中的权威定义之间没有编译期关联。一旦 `DataFile` 中某个字段名发生变更（虽然在稳定 API 下概率很低，但维护上仍存在风险），`SparkDataFile` 中的字面量不会随之更新，会导致字段定位失败、运行时抛出 `IllegalArgumentException`，且这类问题难以在编译期或静态检查阶段被发现。

通过改用 `DataFile` 常量，字段名与其权威定义建立显式依赖，编译器能在常量变更时追踪到所有使用点，提升代码的可维护性与健壮性。同时代码可读性也更好——读者能直接看出某处引用的是 `DataFile` 规范定义的字段，而非随意拼写的字符串。

## 如何达成设计目的

整体设计思路是将 `SparkDataFile` 构造方法与 `fieldPosition` 辅助方法中所有以字符串字面量形式出现的 `DataFile` 字段名，逐一替换为对应的 `DataFile` 常量。对于 `Types.NestedField` 类型的常量（如 `FILE_PATH`、`LOWER_BOUNDS`），通过 `.name()` 获取字段名字符串；对于已是字符串类型的常量（如 `PARTITION_NAME`），直接引用。改动集中在单个文件、单个类，纯机械替换，无行为变化。同时顺带为字段赋值语句补充了 `this.` 前缀，使代码风格更统一。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkDataFile.java`

**修改目的**：用 `DataFile` 常量替换硬编码的字符串字面量，建立字段名与权威定义的显式关联。

**工作逻辑**：

1. **构造方法中的类型查找**：原先通过 `type.fieldType("lower_bounds")`、`type.fieldType("upper_bounds")`、`type.fieldType("key_metadata")`、`type.fieldType("partition")` 获取字段类型，现在分别改为 `type.fieldType(DataFile.LOWER_BOUNDS.name())`、`type.fieldType(DataFile.UPPER_BOUNDS.name())`、`type.fieldType(DataFile.KEY_METADATA.name())`、`type.fieldType(DataFile.PARTITION_NAME)`。对于投影类型中的 partition 字段，同样由 `projectedType.fieldType("partition")` 改为 `projectedType.fieldType(DataFile.PARTITION_NAME)`。注意 `PARTITION_NAME` 本身就是 `String` 常量，故无需 `.name()`；其余为 `Types.NestedField` 常量，需调用 `.name()` 取字段名字符串。

2. **字段位置映射**：构造方法后半段通过 `positions.get(...)` 获取各字段在 Spark `StructType` 中的列索引，共 14 处。原先全部使用字面量，如 `positions.get("file_path")`、`positions.get("file_format")`、`positions.get("partition")`、`positions.get("record_count")`、`positions.get("file_size_in_bytes")`、`positions.get("column_sizes")`、`positions.get("value_counts")`、`positions.get("null_value_counts")`、`positions.get("nan_value_counts")`、`positions.get("lower_bounds")`、`positions.get("upper_bounds")`、`positions.get("key_metadata")`、`positions.get("split_offsets")`、`positions.get("sort_order_id")`。现在全部改为对应常量：`positions.get(DataFile.FILE_PATH.name())`、`positions.get(DataFile.FILE_FORMAT.name())`、`positions.get(DataFile.PARTITION_NAME)`、`positions.get(DataFile.RECORD_COUNT.name())`、`positions.get(DataFile.FILE_SIZE.name())`、`positions.get(DataFile.COLUMN_SIZES.name())`、`positions.get(DataFile.VALUE_COUNTS.name())`、`positions.get(DataFile.NULL_VALUE_COUNTS.name())`、`positions.get(DataFile.NAN_VALUE_COUNTS.name())`、`positions.get(DataFile.LOWER_BOUNDS.name())`、`positions.get(DataFile.UPPER_BOUNDS.name())`、`positions.get(DataFile.KEY_METADATA.name())`、`positions.get(DataFile.SPLIT_OFFSETS.name())`、`positions.get(DataFile.SORT_ORDER_ID.name())`。同时为这些赋值语句统一加上 `this.` 前缀（如 `this.filePathPosition = ...`），提升风格一致性。

3. **`fieldPosition` 中的分区兜底判断**：在私有方法 `fieldPosition` 中，当 `sparkType.fieldIndex(name)` 抛出 `IllegalArgumentException` 时，原代码用 `name.equals("partition")` 判断是否为非分区表的 partition 字段缺失场景（此时返回 -1）。现改为 `name.equals(DataFile.PARTITION_NAME)`，使兜底判断同样基于权威常量。

经过上述改动，`SparkDataFile` 中所有引用 `DataFile` 字段名的位置均与 `DataFile` 接口的常量定义建立编译期关联，行为完全不变，但可维护性显著提升。

## 小结

本提交通过将 `SparkDataFile` 中 14 处字段名硬编码字面量替换为 `DataFile` 接口的常量引用，消除魔法字符串与权威定义之间的隐式耦合，提升代码可维护性与字段名变更时的编译期可追踪性。
