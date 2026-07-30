# 提交 0487：Spark 3.3: Move the Writer to a visitor (#9672)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0487 |
| 完整哈希 | 40fbd8dc5cbb47ba2f2bba0a771bb7c0a0d50a10 |
| 短哈希 | 40fbd8dc5 |
| 日期 | 2024-02-07（Wed Feb 7 09:43:41 2024 +0100） |
| 作者 | Fokko Driesprong <fokko@apache.org> |
| 说明 | Spark 3.3: Move the Writer to a visitor (#9672) |
| PR | #9672 |
| 上游 PR | #9440（本提交为其回port） |

提交统计：1 个文件修改，119 行新增，49 行删除。

涉及文件：`spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java`

## 总体目的

本提交是上游 PR #9440 向 Spark 3.3 模块的回port，对 `SparkParquetWriters` 中 Parquet 写入器的构建逻辑做了一次结构性重构：把原先基于 `primitive.getOriginalType()` 的 `switch` 分发，改造为基于 Parquet 新版 `LogicalTypeAnnotation` 的访问者（Visitor）模式分发。重构的核心动机是跟进 Parquet 自身的 API 演进——`OriginalType` 枚举（`UTF8`、`INT_8`、`INT_16`、`TIME_MICROS`、`TIMESTAMP_MICROS`、`BSON` 等）已被标记为过时（deprecated），上游推荐改用 `LogicalTypeAnnotation` 类层次结构，并通过 `LogicalTypeAnnotationVisitor` 以类型安全的方式对不同逻辑类型分别处理。

旧实现存在两个具体问题。其一，`OriginalType` 与 `LogicalTypeAnnotation` 是两套并行的类型描述体系，`OriginalType` 表达力有限（例如无法区分 INT_8/INT_16/INT_32 之外的字位宽度，也无法表达 UUID 这种较新的逻辑类型），且 Parquet 在新版本中持续向 `LogicalTypeAnnotation` 迁移，旧 API 长期看会被移除。其二，旧代码中 INT8/INT16 的判定依赖 Spark 的 `DataType`（通过 `ints(DataType type, ColumnDescriptor desc)` 辅助方法里 `type instanceof ByteType` / `type instanceof ShortType`），即用写入侧的 Spark 类型反推 Parquet 写入器种类；这把"如何写"的决策耦合到了"输入是什么 Spark 类型"上，逻辑上不如直接依据 Parquet schema 自身的 `IntLogicalTypeAnnotation.getBitWidth()` 来得自然与稳健。

重构后，所有逻辑类型到 `ParquetValueWriter` 的映射集中在一个独立的访问者类 `LogicalTypeAnnotationParquetValueWriterVisitor` 中，每个 `LogicalTypeAnnotation` 子类型对应一个 `visit` 重载，返回 `Optional<ParquetValueWriter<?>>`。这一改造使代码与 Parquet 推荐的扩展方式对齐：未来新增逻辑类型只需在访问者中新增一个 `visit` 重载，而不必修改集中的 `switch`；同时也顺带补齐了旧实现缺失的 UUID 逻辑类型支持（旧 `switch` 没有 UUID 分支）。

## 如何达成设计目的

实现路径分三步：首先新增一个实现了 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueWriter<?>>` 的静态内部类 `LogicalTypeAnnotationParquetValueWriterVisitor`，在其中为每种 `LogicalTypeAnnotation` 子类型（String/Enum/JSON/UUID/Map/List/Decimal/Date/Time/Timestamp/Int/Bson）实现 `visit` 方法，返回对应的 `ParquetValueWriter`；其次把 `primitive(DataType sType, PrimitiveType primitive)` 方法中的 `switch (primitive.getOriginalType())` 替换为 `primitive.getLogicalTypeAnnotation().accept(visitor)`，并对 `Optional` 空值抛出 `UnsupportedOperationException`；最后删除不再需要的 `ints(DataType type, ColumnDescriptor desc)` 辅助方法及其对 `ByteType`/`ShortType` 的依赖，改为在访问者的 `visit(IntLogicalTypeAnnotation)` 中按 `bitWidth` 选择 tinyints/shorts/ints/longs，并在无逻辑类型的 INT32 回退路径中直接调用 `ParquetValueWriters.ints(desc)`。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java`

**修改目的**：将 Parquet 写入器的逻辑类型分发从过时的 `OriginalType` switch 改造为基于 `LogicalTypeAnnotation` 的访问者模式，并对齐 Parquet 新版 API、补齐 UUID 支持、解耦对 Spark `DataType` 的依赖。

**工作逻辑**：

1. **导入调整**：新增 `java.util.Optional`（访问者方法返回 `Optional<ParquetValueWriter<?>>`）；移除 `org.apache.spark.sql.types.ByteType` 与 `org.apache.spark.sql.types.ShortType`（不再需要按 Spark 类型区分 INT8/INT16）。

2. **新增访问者类 `LogicalTypeAnnotationParquetValueWriterVisitor`**：位于 `Writer` 内部，构造时接收 `ColumnDescriptor desc` 与 `PrimitiveType primitive` 两个上下文（`desc` 用于创建写入器，`primitive` 用于在 Decimal 分支中判断底层 `PrimitiveTypeName`）。各 `visit` 重载的映射逻辑如下：
   - `StringLogicalTypeAnnotation` / `EnumLogicalTypeAnnotation` / `JsonLogicalTypeAnnotation` → `utf8Strings(desc)`，对应旧的 `UTF8`/`ENUM`/`JSON` 三个 case 合并。
   - `UUIDLogicalTypeAnnotation` → `uuids(desc)`，这是旧实现没有的新增分支，补齐了 UUID 逻辑类型的写入支持。
   - `MapLogicalTypeAnnotation` / `ListLogicalTypeAnnotation` → 调用 `super.visit(...)` 走默认实现（返回 `Optional.empty()`），因为 Map/List 是组合类型，不应在 primitive 写入器路径处理。
   - `DecimalLogicalTypeAnnotation` → 按 `primitive.getPrimitiveTypeName()` 分派：`INT32` → `decimalAsInteger`、`INT64` → `decimalAsLong`、`BINARY`/`FIXED_LEN_BYTE_ARRAY` → `decimalAsFixed`，其余返回 `Optional.empty()`。与旧逻辑等价，但旧代码在 default 分支抛异常，新版返回空交由外层 `orElseThrow` 统一处理。
   - `DateLogicalTypeAnnotation` → `ParquetValueWriters.ints(desc)`，对应旧 `DATE` case。
   - `TimeLogicalTypeAnnotation` → 仅当 `getUnit() == TimeUnit.MICROS` 时返回 `longs(desc)`，否则返回空。旧代码用 `TIME_MICROS` OriginalType 隐式只覆盖微秒，新版显式检查单位更严谨。
   - `TimestampLogicalTypeAnnotation` → 仅当 `getUnit() == TimeUnit.MICROS` 时返回 `longs(desc)`，否则返回空，对应旧 `TIMESTAMP_MICROS` case。
   - `IntLogicalTypeAnnotation` → 按 `getBitWidth()` 选择：`<=8` → `tinyints`、`<=16` → `shorts`、`<=32` → `ints`、`else` → `longs`。这是对旧 `INT_8`/`INT_16`/`INT_32` 三个 case 的合并与泛化，且判定依据从 Spark `DataType` 改为 Parquet 自身的 `bitWidth`，更贴合 schema 语义。
   - `BsonLogicalTypeAnnotation` → `byteArrays(desc)`，对应旧 `BSON` case。

3. **`primitive` 方法改造**：原 `if (primitive.getOriginalType() != null) { switch (...) }` 整块替换为：
   ```java
   LogicalTypeAnnotation logicalTypeAnnotation = primitive.getLogicalTypeAnnotation();
   if (logicalTypeAnnotation != null) {
     return logicalTypeAnnotation
         .accept(new LogicalTypeAnnotationParquetValueWriterVisitor(desc, primitive))
         .orElseThrow(() -> new UnsupportedOperationException(
             "Unsupported logical type: " + primitive.getLogicalTypeAnnotation()));
   }
   ```
   即先取逻辑类型注解，非空则交给访问者，访问者返回空（不识别）时由 `orElseThrow` 抛出与旧实现等价的 `UnsupportedOperationException`。

4. **无逻辑类型时的回退分支**：底层 `switch (primitive.getPrimitiveTypeName())` 中 `INT32` 分支由旧的 `ints(sType, desc)` 改为直接 `ParquetValueWriters.ints(desc)`，因为辅助方法 `ints(DataType, ColumnDescriptor)` 已删除（其 ByteType/ShortType 区分职责已迁移到访问者的 Int 分支）。

5. **删除 `ints(DataType type, ColumnDescriptor desc)` 辅助方法**：该方法原本依据 `type instanceof ByteType`/`ShortType` 选择 tinyints/shorts/ints，重构后该职责由访问者的 `visit(IntLogicalTypeAnnotation)` 按 `bitWidth` 承担，故整段删除，连带移除 `ByteType`/`ShortType` 导入。

## 小结

本提交是上游 #9440 向 Spark 3.3 模块的回port，把 `SparkParquetWriters` 中 Parquet 写入器的逻辑类型分发从过时的 `OriginalType` switch 重构为基于 `LogicalTypeAnnotation` 的访问者模式：新增 `LogicalTypeAnnotationParquetValueWriterVisitor` 集中承载各逻辑类型到写入器的映射，`primitive` 方法改为 `logicalTypeAnnotation.accept(visitor).orElseThrow(...)`，并删除依赖 Spark `DataType` 的 `ints` 辅助方法、改由访问者按 `IntLogicalTypeAnnotation.getBitWidth()` 决定 tinyints/shorts/ints/longs。重构使代码与 Parquet 新版 API 对齐、解耦对 Spark 类型的依赖、补齐 UUID 支持，并为后续新增逻辑类型提供可扩展的扩展点。属于纯内部重构，写入行为对外保持等价，回迁 1.4.x 风险较低但需配合测试验证各逻辑类型写入路径无回归。
