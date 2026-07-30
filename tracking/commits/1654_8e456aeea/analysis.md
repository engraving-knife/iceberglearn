# 提交 1654 8e456aeea 分析

## 提交信息
- 哈希：8e456aeeabd0a40b23864edadd622b45cb44572c
- 日期：2025-01-28 12:13:42 -0700
- 作者：Ryan Blue
- 消息：Parquet: Clean up Parquet generic and internal readers (#12102)

## 总体目的

本提交对 Iceberg 的 Parquet 读取器（readers）体系进行了一次较系统的清理与重构。Iceberg 在 Parquet 模块中维护着一套 `ParquetValueReader` 抽象，由 `BaseParquetReaders` 提供通用逻辑，再由 Flink (`FlinkParquetReaders`)、Spark (`SparkParquetReaders`)、Generic (`GenericParquetReaders`)、Internal (`InternalReader`)、Avro (`ParquetAvroValueReaders`) 等子类针对各自的内存模型进行特化。

此前这套代码存在若干历史遗留问题：
- `StructReader` 构造函数携带一个未被实际使用的 `List<Type> types` 参数，所有子类都需要冗余地维护和传递这个列表，徒增噪声。
- `ParquetValueReader.setPageSource(PageReadStore, long rowPosition)` 是已废弃方法，但各子类仍逐个重复实现"委托给 setPageSource(pageStore)"的样板代码。
- 读取器实例的创建散落在多处，缺乏统一的工厂方法，部分逻辑（如 decimal 根据 primitive 类型选择 reader、time/timestamp 根据 unit 选择 reader）在 BaseParquetReaders 的 visitor 和子类中重复实现。
- 部分内部类可见性过宽（`static` 包级），未表达"仅供内部使用"的意图。

本次重构在不改变读取器运行时行为的前提下，清理了上述冗余，集中了工厂方法，明确了类型与可见性，为后续扩展（如 Variant 类型读取器）打下更整洁的基础。

## 如何达成设计目的

设计思路：
1. 删除无用参数：将 `StructReader` 及其所有子类的构造函数中的 `List<Type> types` 参数移除，调用点同步清理。
2. 上提默认实现：在接口 `ParquetValueReader` 中为废弃的 `setPageSource(pageStore, rowPosition)` 提供 `default` 实现（委托给单参版本），删除各子类中重复的委托代码。
3. 集中工厂方法：在 `ParquetValueReaders` 中新增一组静态工厂方法（`unboxed`、`strings`、`byteBuffers`、`intsAsLongs`、`floatsAsDoubles`、`bigDecimals`、`times`、`timestamps`），把"根据 ColumnDescriptor 的逻辑类型和原始类型选择合适 reader"的逻辑收敛到一处。
4. 简化 visitor：将 `LogicalTypeAnnotationParquetValueReaderVisitor` 重命名为更贴切的 `LogicalTypeReadBuilder`，移除不再需要的 `primitive` 字段（decimal 的 primitive 类型判断移入 `bigDecimals` 工厂方法），并采用更简洁的具体类型导入。
5. 收紧可见性：将 `ConstantReader`、`PositionReader` 等纯内部类改为 `private static`，明确不对外暴露。

### 修改详情

#### parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReader.java
- 将已 `@Deprecated` 的 `setPageSource(PageReadStore, long rowPosition)` 由抽象方法改为 `default` 方法，默认委托给 `setPageSource(pageStore)`。这样各子类不再需要重复实现该方法，同时保持向后兼容（旧调用方仍可调用两参版本）。

#### parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java
改动核心：
- 新增多个静态工厂方法，统一 reader 创建：
  - `unboxed(desc)`：返回 `UnboxedReader`，用于读取 boolean/int/long/double 等原始类型。
  - `strings(desc)`：返回 `StringReader`。
  - `byteBuffers(desc)`：返回 `BytesReader`（输出 ByteBuffer）。
  - `intsAsLongs(desc)`、`floatsAsDoubles(desc)`：类型提升读取器。
  - `bigDecimals(desc)`：根据 desc 的 primitive 类型（BINARY/FIXED_LEN_BYTE_ARRAY -> BinaryAsDecimalReader，INT64 -> LongAsDecimalReader，INT32 -> IntegerAsDecimalReader）返回对应 decimal reader，并校验逻辑类型为 DecimalLogicalTypeAnnotation。
  - `times(desc)`：校验为 TimeLogicalTypeAnnotation，MILLIS 返回 TimeMillisReader，否则返回 UnboxedReader（MICROS 直接读 long）。
  - `timestamps(desc)`：INT96 返回 TimestampInt96Reader；否则校验为 TimestampLogicalTypeAnnotation，MILLIS 返回 TimestampMillisReader，MICROS 返回 UnboxedReader。
- 删除旧的 `millisAsTimes`、`millisAsTimestamps` 静态方法（功能合并入 `times`/`timestamps`）；`int96Timestamps` 保留但内部简化为 `new TimestampInt96Reader(desc)`。
- `recordReader` 工厂方法删除 `List<Type> types` 参数。
- `NullReader`、`ConstantReader`、`PositionReader` 等内部类删除对 `setPageSource(pageStore, rowPosition)` 的重写（依赖接口 default 方法）。
- `ConstantReader`、`PositionReader` 由 `static`（包级）改为 `private static`，收紧可见性。
- `ByteArrayReader` 等内部引用简化（去掉冗余的 `ParquetValueReaders.` 前缀）。
- `StructReader`：
  - 旧构造函数 `StructReader(List<Type> types, List<ParquetValueReader<?>> readers)` 标记 `@Deprecated`（注释将在 1.9.0 移除），内部委托给新构造函数。
  - 新增 `StructReader(List<ParquetValueReader<?>> readers)` 作为推荐构造函数。
  - 删除 `setPageSource(pageStore, rowPosition)` 的重写。
- `RecordReader` 同步改为单参数 readers 构造。

#### parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java
- 导入整理：引入 `Preconditions`、`TypeID`、各 `LogicalTypeAnnotation` 子类型（Date/Enum/Int/Json/String/Time/Timestamp），简化代码。
- `timeReader(ColumnDescriptor desc, TimeUnit unit)` 改为 `timeReader(ColumnDescriptor desc)`：内部从 `desc.getPrimitiveType().getLogicalTypeAnnotation()` 取出 TimeLogicalTypeAnnotation 并校验，再取 unit，逻辑不变但调用方更简洁。
- `timestampReader(ColumnDescriptor desc, TimeUnit unit, boolean isAdjustedToUTC)` 改为 `timestampReader(ColumnDescriptor desc, boolean isAdjustedToUTC)`：同样从 desc 中提取 TimestampLogicalTypeAnnotation 并校验。
- 内部 visitor 类 `LogicalTypeAnnotationParquetValueReaderVisitor` 重命名为 `LogicalTypeReadBuilder`：
  - 移除 `primitive` 字段（不再需要，因为 decimal 的 primitive 判断已移入 `ParquetValueReaders.bigDecimals`）。
  - 各 visit 方法改用具体类型导入，并改用新的工厂方法（如 `ParquetValueReaders.strings(desc)` 代替 `new ParquetValueReaders.StringReader(desc)`）。
  - `visit(IntLogicalTypeAnnotation)` 增加校验：UINT64 不允许（无法用 long 表示）；非 LONG 时若为 UINT32 也不允许（无法用 int 表示）。
- `readPrimitive` 方法中：BINARY 根据 expected 是 STRING 走 `strings` 否则走 `byteBuffers`；INT32 LONG 走 `intsAsLongs` 否则 `unboxed`；FLOAT DOUBLE 走 `floatsAsDoubles` 否则 `unboxed`；BOOLEAN/INT64/DOUBLE 走 `unboxed`；INT96 走 `timestampReader(desc, true)`。

#### parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetReaders.java
- `createStructReader` 调用 `ParquetValueReaders.recordReader` 时删除 `types` 参数。

#### parquet/src/main/java/org/apache/iceberg/data/parquet/InternalReader.java
- 导入清理（移除 `LogicalTypeAnnotation`、`PrimitiveType`）。
- `timeReader` 重写简化为 `return ParquetValueReaders.times(desc);`（旧实现中仅 MILLIS 走 TimeMillisReader，其余走 UnboxedReader，与 `times` 工厂一致）。
- `timestampReader` 重写简化为 `return ParquetValueReaders.timestamps(desc);`（旧实现的 INT96、MILLIS、其余三种情况均被 `timestamps` 工厂覆盖）。
- `createStructReader` 删除 `types` 参数。

#### parquet/src/main/java/org/apache/iceberg/parquet/ParquetAvroValueReaders.java
- `RecordReader` 构造函数及 `createRecordReader` 调用中删除 `List<Type> types` 参数的构建与传递。

#### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java
- `RowDataReader` 构造函数删除 `types` 参数。
- 在按字段 id 构造 reorderedFields 时，移除对 `types` 列表的同步维护（原本每个分支都要 add 一个 type 或 null）。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java
- `InternalRowReader` 构造函数删除 `types` 参数。
- `createStructReader` 中移除 `types` 列表维护。
- `readPrimitive` 中 `TIMESTAMP_MILLIS` 改为调用 `ParquetValueReaders.timestamps(desc)`（旧调用 `millisAsTimestamps`）；`TIMESTAMP_MICROS` 改为 `new UnboxedReader<>(desc)`（与 `timestamps` 工厂 MICROS 分支一致）。

## 小结

本次清理成效：
- 显著减少了样板代码：删除 `List<Type> types` 这一无用参数在 8 个文件中减少了大量冗余的列表维护代码；`setPageSource` 默认实现上提消除了 6+ 处重复委托。
- 提升了可维护性：reader 创建逻辑集中在 `ParquetValueReaders` 的工厂方法中，未来新增类型或修改逻辑只需改一处。
- 增强了健壮性：在 `bigDecimals`、`times`、`timestamps`、`IntLogicalTypeAnnotation` 处增加 Preconditions 校验，对非法逻辑类型提前抛出明确异常。
- 收紧了封装：内部 reader 类改为 private static。

影响范围：Parquet 读取器模块（core/parquet、flink v1.20、spark v3.5），不改变运行时行为，属于纯重构。

回迁到 1.4.x 注意事项：
- 这是一个无行为变更的纯重构，回迁风险较低，但 1.4.x 通常不做此类清理以保持稳定性。
- 若 1.4.x 已存在相关 reader 代码且后续有基于此重构的提交需要回迁（如 Variant 读取器），则需先回迁本提交。
- 回迁时需注意 `@Deprecated` 注释中提到"将在 1.9.0 移除"，与 1.4.x 版本号不符，可保留注释以保持向上游一致。
- 由于 `StructReader` 旧构造函数被保留为 @Deprecated，回迁不会破坏外部子类（若有）的兼容性。
- 注意本提交同时涉及 flink v1.20 和 spark v3.5 两个集成模块，回迁时需确认 1.4.x 维护的 flink/spark 版本范围，可能需要调整目标版本目录。
