# 提交 0482：Spark: Move the Writer to a visitor (#9440)

## 提交信息

- **序号**：0482 / 4088
- **哈希**：5d3be12e0d7e94aa6a63dfdc3cc7a7d712f54427
- **短哈希**：5d3be12e0
- **日期**：2024-02-07 08:13:22 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Spark: Move the Writer to a visitor (#9440)
- **PR/Issue**：#9440

## 总体目的

这个提交把 `SparkParquetWriters` 中原始类型（primitive）写入器的构建逻辑从基于 `getOriginalType()` 的 `switch` 分派改造成基于 Parquet 现代的 `LogicalTypeAnnotation` 访问者模式（Visitor 模式）。`SparkParquetWriters` 是 Iceberg Spark 3.5 集成层中负责把 Spark `InternalRow`/`ArrayData`/`MapData` 写入 Parquet 文件的工厂类，其内部的 `WriteBuilder.primitive(DataType sType, PrimitiveType primitive)` 方法是所有叶子类型节点写入器的总入口。重构前，该入口通过 `primitive.getOriginalType()` 拿到 Parquet 旧式的 `OriginalType` 枚举（如 `UTF8`、`DATE`、`INT_8`、`DECIMAL`、`BSON` 等），再用 `switch` 分派到对应的 `ParquetValueWriters` 工厂方法。

`OriginalType` 在新版 Parquet 中已经被标注为 `@Deprecated`，社区推荐使用 `LogicalTypeAnnotation` 体系。`OriginalType` 是一个枚举，表达能力有限，且需要为 `DECIMAL` 之类带参数的类型额外做 `instanceof` + 强转 `DecimalLogicalTypeAnnotation` 才能拿到精度/小数位。`LogicalTypeAnnotation` 本身就是面向对象的类型族（`StringLogicalTypeAnnotation`、`UUIDLogicalTypeAnnotation`、`IntLogicalTypeAnnotation`、`DecimalLogicalTypeAnnotation` 等），各自携带自己的元数据（如 `bitWidth`、`precision`、`scale`、`unit`），并通过 `accept(LogicalTypeAnnotationVisitor)` 提供类型安全的双重分派。改造后，原本集中在一个长 switch 里的字符串/日期/整数/小数/UUID 分派被拆分到独立 visitor 类的若干 `visit(...)` 重载方法中，每个方法只关心一种逻辑类型，可读性、可扩展性显著提高。

附带地，重构还修正了一个语义不清的耦合：原本 `INT_8`、`INT_16`、`INT_32` 这三种 Parquet 整数逻辑类型都被路由到 `ints(DataType type, ColumnDescriptor desc)` 辅助方法，该方法再用 Spark 的 `ByteType`/`ShortType` 实例判断来决定走 `tinyints`、`shorts` 还是 `ints` 写入器——也就是说，写入器的选择依赖 Spark 侧的类型而非 Parquet 侧的 bitWidth。这在 Spark 类型与 Parquet 逻辑类型不严格一一对应的场景下会出现选择不稳定的情况。新代码在 `IntLogicalTypeAnnotation.visit` 中直接依据 `bitWidth` 判断（≤8 → tinyints，≤16 → shorts，≤32 → ints，其余 → longs），把决策来源统一到 Parquet schema 自身，更符合"按物理/逻辑类型写出"的契约。

## 如何达成设计目的

整体做法是在 `WriteBuilder` 内部新增一个静态内部类 `LogicalTypeAnnotationParquetValueWriterVisitor`，实现 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueWriter<?>>`，针对每种逻辑类型提供一个 `visit(...)` 方法，返回 `Optional<ParquetValueWriter<?>>`。然后改写 `WriteBuilder.primitive`：先取 `primitive.getLogicalTypeAnnotation()`，若非空则调用其 `accept(visitor)` 取 `Optional`，再 `.orElseThrow(...)` 抛出 `UnsupportedOperationException`；若为空则回退到原有的按 `PrimitiveTypeName` 分派的 `switch`。同时删除原本的 `ints(DataType type, ColumnDescriptor desc)` 辅助方法以及 `ByteType`/`ShortType` 的导入，新增 `Optional` 的导入。Map 与 List 的 `visit` 实现直接委托给父接口的默认行为（返回 `Optional.empty()`），因为这两个逻辑类型对应的是 group 类型而非 primitive，不会进入 `primitive(...)` 这条入口，这两处属于对接口默认行为的显式确认。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java`

**修改目的**：把原始类型写入器的分派从 `OriginalType` 枚举 switch 迁移到 `LogicalTypeAnnotation` visitor 模式，去除对 Spark `ByteType`/`ShortType` 的依赖，并使用 Parquet 推荐的现代逻辑类型 API。

**工作逻辑**：

1. **import 调整**：新增 `java.util.Optional`，移除 `org.apache.spark.sql.types.ByteType` 和 `ShortType`（新代码不再依赖 Spark 类型分支来选 tinyints/shorts）。

2. **新增 `LogicalTypeAnnotationParquetValueWriterVisitor` 内部类**：实现 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueWriter<?>>`，持有 `ColumnDescriptor desc` 与 `PrimitiveType primitive`，并为以下逻辑类型提供 `visit` 重载：

   - `StringLogicalTypeAnnotation` / `EnumLogicalTypeAnnotation` / `JsonLogicalTypeAnnotation` → 返回 `utf8Strings(desc)`：三者底层都是字符串字节序列，统一用 UTF8 写入器。
   - `UUIDLogicalTypeAnnotation` → 返回 `uuids(desc)`：UUID 16 字节定长写入。注意这把原本只在"无 OriginalType"分支里通过 `LogicalTypeAnnotation.uuidType().equals(...)` 才能命中的 UUID 路径前移到逻辑类型分派中，更内聚。
   - `MapLogicalTypeAnnotation` / `ListLogicalTypeAnnotation` → `super.visit(...)`：这两类是 group 类型，不会进入 `primitive()` 入口，返回 `Optional.empty()` 是合理的占位。
   - `DecimalLogicalTypeAnnotation` → 依据 `primitive.getPrimitiveTypeName()` 进一步分派到 `INT32` 走 `decimalAsInteger`、`INT64` 走 `decimalAsLong`、`BINARY`/`FIXED_LEN_BYTE_ARRAY` 走 `decimalAsFixed`，其他返回 `Optional.empty()`，与原 switch 中 DECIMAL 分支语义一致。
   - `DateLogicalTypeAnnotation` → `ParquetValueWriters.ints(desc)`：日期以 INT32 写入，与原 `DATE → ints(...)` 行为一致。
   - `TimeLogicalTypeAnnotation` → 仅 `MICROS` 单位返回 `longs(desc)`，否则返回 `Optional.empty()`：与原 `TIME_MICROS → longs(desc)` 一致，但额外用单位做更精确的过滤。
   - `TimestampLogicalTypeAnnotation` → 仅 `MICROS` 单位返回 `longs(desc)`，与原 `TIMESTAMP_MICROS` 分支一致。
   - `IntLogicalTypeAnnotation` → 按 `bitWidth` 分派：`≤8` → `tinyints(desc)`，`≤16` → `shorts(desc)`，`≤32` → `ints(desc)`，否则 `longs(desc)`。这里取代了原 `INT_8/INT_16/INT_32` 三个枚举分支统一走 `ints(DataType, ColumnDescriptor)` 的逻辑，决策依据由"Spark 数据类型"换成了"Parquet 逻辑类型的 bitWidth"。
   - `BsonLogicalTypeAnnotation` → `byteArrays(desc)`，与原 `BSON` 分支一致。

3. **重写 `WriteBuilder.primitive(DataType sType, PrimitiveType primitive)`**：取 `desc = type.getColumnDescription(currentPath())`，再 `logicalTypeAnnotation = primitive.getLogicalTypeAnnotation()`；若非空，则 `logicalTypeAnnotation.accept(new LogicalTypeAnnotationParquetValueWriterVisitor(desc, primitive)).orElseThrow(() -> new UnsupportedOperationException("Unsupported logical type: " + primitive.getLogicalTypeAnnotation()))`。若为空，进入原来的按 `PrimitiveTypeName` 分派的 switch（`FIXED_LEN_BYTE_ARRAY`/`BINARY` 中区分 UUID 与 `byteArrays`，`BOOLEAN`/`INT32`/`INT64`/`FLOAT`/`DOUBLE` 走对应工厂）。其中原本的 `case INT32: return ints(sType, desc)` 改为 `return ParquetValueWriters.ints(desc)`，因为 `ints(DataType, ...)` 辅助方法已被移除——这里的 INT32 在没有逻辑注解的情况下就是普通 32 位整数，没必要再分流到 tinyints/shorts。

4. **移除私有辅助方法 `ints(DataType type, ColumnDescriptor desc)`**：原本依据 `ByteType`/`ShortType` 分派到 `tinyints`/`shorts`/`ints` 的逻辑被 `IntLogicalTypeAnnotation.visit` 的 bitWidth 分派替代，该辅助方法失去存在意义，连同 `ByteType`、`ShortType` 导入一起删除。

5. **空行 / 空白微调**：删除 `struct` 方法体内一个多余空行，统一代码风格（提交说明中 "Whitespace nits"、"Add newline" 子提交即指此）。

## 小结

本次重构把 `SparkParquetWriters.WriteBuilder.primitive` 的逻辑类型分派从基于已废弃的 `OriginalType` 枚举的 switch 迁移到 Parquet 推荐的 `LogicalTypeAnnotation` visitor 模式，新增一个 `LogicalTypeAnnotationParquetValueWriterVisitor` 内部类承载每种逻辑类型的写入器选择。附带地，整数类型写入器的选择从"依赖 Spark `ByteType`/`ShortType`"切换为"依据 Parquet `IntLogicalTypeAnnotation.bitWidth`"，决策来源更内聚、契约更清晰。整体行为对外保持不变，但代码可读性、可扩展性与对 Parquet 现代 API 的依从性显著提升，且为后续引入新的逻辑类型（如未来扩展的 `Variant`、`Float16` 等）提供了清晰的扩展点。
