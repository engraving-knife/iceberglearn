# 提交 0718：Flink: Move ParquetReader to LogicalTypeAnnotationVisitor

## 提交信息
- **序号**：0718 / 4088
- **哈希**：c9f775b8063e9af4c14de12659c36a4286e4b000
- **短哈希**：c9f775b80
- **日期**：2024-04-26 08:50:48 +0200
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Flink: Move ParquetReader to LogicalTypeAnnotationVisitor (#9719)
- **PR/Issue**：#9719

## 总体目的

本提交对 Flink 集成模块中 Parquet 读取器（`FlinkParquetReaders`）的类型分发逻辑进行重构，将其从基于 `OriginalType`（已过时的枚举类型）的 `switch` 语句迁移到基于 `LogicalTypeAnnotation` 的访问者模式（Visitor Pattern）。

这次重构的直接动机是为支持**纳秒级时间戳（nanosecond timestamp）**铺平道路。Parquet 规范中，时间戳的逻辑类型注解（`TimestampLogicalTypeAnnotation`）支持三种时间精度：MILLIS（毫秒）、MICROS（微秒）和 NANOS（纳秒）。然而，旧的 `OriginalType` 枚举（`TIMESTAMP_MILLIS`、`TIMESTAMP_MICROS`）只能表示毫秒和微秒两种精度，**无法表示纳秒精度**。因此，只要读取器还依赖 `OriginalType` 做类型分发，就无法正确识别和处理纳秒级时间戳。

通过迁移到 `LogicalTypeAnnotation` 体系，读取器可以访问 `TimestampLogicalTypeAnnotation.getUnit()` 返回的 `TimeUnit` 枚举，该枚举包含 `MILLIS`、`MICROS`、`NANOS` 三个值，从而为未来支持纳秒时间戳扫清障碍。

这次重构同时提升了代码的可维护性和可扩展性。访问者模式将每种逻辑类型的处理逻辑分散到独立的 `visit` 方法中，避免了原来巨大的、嵌套的 `switch` 语句，符合开闭原则（OCP）——新增逻辑类型支持时只需添加新的 `visit` 方法，而不需要修改集中的分发逻辑。

## 如何达成设计目的

重构的核心策略是引入一个新的内部静态类 `LogicalTypeAnnotationParquetValueReaderVisitor`，实现 Parquet 的 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueReader<?>>` 接口。这个访问者负责根据 Parquet 字段的逻辑类型注解（`LogicalTypeAnnotation`）返回对应的 `ParquetValueReader` 实例。

**重构前的架构**：在 `primitive()` 方法中，通过 `primitive.getOriginalType()` 获取 `OriginalType` 枚举值，然后用一个大的 `switch` 语句分发到不同的 Reader 构造逻辑。对于 DECIMAL 类型，还需要嵌套一个内层 `switch` 来处理不同的底层物理类型（BINARY、INT64、INT32）。

**重构后的架构**：
1. 在 `primitive()` 方法中，通过 `primitive.getLogicalTypeAnnotation()` 获取 `LogicalTypeAnnotation` 对象。
2. 如果非 null，则调用 `logicalTypeAnnotation.accept(visitor)`，由访问者根据具体的逻辑类型子类分发到对应的 `visit` 方法。
3. 每个 `visit` 方法返回 `Optional<ParquetValueReader<?>>`，如果返回 empty 则通过 `orElseThrow` 抛出 `UnsupportedOperationException`。

这种设计利用了 Parquet 库本身提供的访问者模式基础设施，`LogicalTypeAnnotation` 是一个抽象基类，其各个子类（`StringLogicalTypeAnnotation`、`DecimalLogicalTypeAnnotation`、`TimestampLogicalTypeAnnotation` 等）各自实现 `accept` 方法，调用访问者对应的 `visit` 重载。这是一种经典的双重分派（double dispatch）机制。

重构同时保证了三个 Flink 版本（v1.17、v1.18、v1.19）的代码同步修改，确保行为一致性。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/DataTest.java`
**修改目的**：将 `SUPPORTED_PRIMITIVES` 常量的可见性从 `private` 改为 `protected`，以便子类（如 `TestFlinkParquetReader`）可以访问。

**工作逻辑**：原来的 `private static final StructType SUPPORTED_PRIMITIVES` 改为 `protected static final StructType SUPPORTED_PRIMITIVES`。这是因为新增的测试 `testBuildReader` 需要在 `TestFlinkParquetReader`（继承自 `DataTest`）中使用这个常量来构造测试 schema。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`
**修改目的**：将类型分发逻辑从 `OriginalType` switch 迁移到 `LogicalTypeAnnotation` 访问者模式。

**工作逻辑**：

1. **新增导入**：引入 `java.util.Optional`、`org.apache.parquet.schema.LogicalTypeAnnotation`。

2. **新增 `LogicalTypeAnnotationParquetValueReaderVisitor` 内部静态类**：实现 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueReader<?>>` 接口，构造时接收三个参数：
   - `PrimitiveType primitive`：字段的物理类型，用于 DECIMAL 等需要区分底层物理类型的场景
   - `ColumnDescriptor desc`：列描述符，传递给各个 Reader 构造器
   - `org.apache.iceberg.types.Type.PrimitiveType expected`：Iceberg 期望的类型，用于 INT 类型判断是否需要将 int 提升为 long

   访问者实现了以下 `visit` 方法，每个对应一种逻辑类型注解：
   - `visit(StringLogicalTypeAnnotation)` → `StringReader`：处理 UTF8 字符串
   - `visit(EnumLogicalTypeAnnotation)` → `StringReader`：处理枚举（与字符串相同处理）
   - `visit(JsonLogicalTypeAnnotation)` → `StringReader`：处理 JSON（与字符串相同处理）
   - `visit(DecimalLogicalTypeAnnotation)` → 根据 `primitive.getPrimitiveTypeName()` 分发：
     - BINARY / FIXED_LEN_BYTE_ARRAY → `BinaryDecimalReader`
     - INT64 → `LongDecimalReader`
     - INT32 → `IntegerDecimalReader`
     - 其他 → 调用父类默认实现（返回 empty，最终抛异常）
   - `visit(DateLogicalTypeAnnotation)` → `UnboxedReader`：处理日期
   - `visit(TimeLogicalTypeAnnotation)` → 根据 `timeLogicalType.getUnit()` 分发：
     - MILLIS → `MillisTimeReader`
     - MICROS → `LossyMicrosToMillisTimeReader`（有损转换：微秒转毫秒）
     - 其他（含 NANOS）→ 调用父类默认实现（**关键点：这为未来支持 NANOS 留出了扩展点**）
   - `visit(TimestampLogicalTypeAnnotation)` → 根据 `getUnit()` 和 `isAdjustedToUTC()` 分发：
     - MILLIS + UTC → `MillisToTimestampTzReader`
     - MILLIS + 非UTC → `MillisToTimestampReader`
     - MICROS + UTC → `MicrosToTimestampTzReader`
     - MICROS + 非UTC → `MicrosToTimestampReader`
     - 其他（含 NANOS）→ 调用父类默认实现（**关键点：这为未来支持 NANOS 留出了扩展点**）
   - `visit(IntLogicalTypeAnnotation)` → 根据 `getBitWidth()` 和 `expected` 类型分发：
     - width ≤ 32 且 expected 为 Long → `IntAsLongReader`（int 提升为 long）
     - width ≤ 32 且 expected 非 Long → `UnboxedReader`
     - width ≤ 64 → `UnboxedReader`
     - 其他 → 调用父类默认实现
   - `visit(BsonLogicalTypeAnnotation)` → `ByteArrayReader`：处理 BSON

3. **修改 `primitive()` 方法**：删除原来基于 `primitive.getOriginalType()` 的大 switch 语句（约 47 行），替换为：
   ```java
   LogicalTypeAnnotation logicalTypeAnnotation = primitive.getLogicalTypeAnnotation();
   if (logicalTypeAnnotation != null) {
     return logicalTypeAnnotation
         .accept(new LogicalTypeAnnotationParquetValueReaderVisitor(primitive, desc, expected))
         .orElseThrow(() -> new UnsupportedOperationException(...));
   }
   ```
   原来嵌套在 DECIMAL case 中的物理类型 switch 被移入 `visit(DecimalLogicalTypeAnnotation)` 方法中。原来 `INT_64` 的 OriginalType case 被移除，因为 INT64 逻辑类型会被 `visit(IntLogicalTypeAnnotation)` 处理（width ≤ 64 分支返回 `UnboxedReader`）。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`
**修改目的**：同 v1.17，同步重构。

**工作逻辑**：与 v1.17 完全相同的修改。Flink 不同版本的集成代码保持同步。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`
**修改目的**：同 v1.17，同步重构。

**工作逻辑**：与 v1.17 完全相同的修改。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`
**修改目的**：新增 `testBuildReader` 测试，验证重构后能正确构建覆盖所有支持类型的 Reader。

**工作逻辑**：新增的测试手动构造一个包含 17 个字段的 Parquet `MessageType`，覆盖以下类型：
- INT64（id）、BINARY（data）、BOOLEAN（b）、INT32（i）
- INT64（l）、FLOAT（f）、DOUBLE（d）
- DATE（INT32 + dateType 注解）
- TIMESTAMP_MICROS with zone（ts_tz）、TIMESTAMP_MICROS without zone（ts）
- STRING（BINARY + stringType 注解）
- FIXED_LEN_BYTE_ARRAY（fixed，长度 7）
- BINARY（bytes）
- DECIMAL(9,0) on INT64、DECIMAL(11,2) on INT64、DECIMAL(38,10) on FIXED_LEN_BYTE_ARRAY
- TIME_MICROS（INT64 + timeType 注解）

然后调用 `FlinkParquetReaders.buildReader(new Schema(SUPPORTED_PRIMITIVES.fields()), fileSchema)` 构建读取器，断言 `reader.columns().size()` 等于 `SUPPORTED_PRIMITIVES.fields().size()`。这个测试验证了重构后所有类型路径都能正常工作，不会抛出 `UnsupportedOperationException`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`
**修改目的**：同 v1.17，同步新增测试。

**工作逻辑**：与 v1.17 完全相同的测试代码。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`
**修改目的**：同 v1.17，同步新增测试。

**工作逻辑**：与 v1.17 完全相同的测试代码。

## 小结

- **成效**：成功达成目的。重构将类型分发逻辑从过时的 `OriginalType` 枚举迁移到 `LogicalTypeAnnotation` 访问者模式，为支持纳秒级时间戳扫清了障碍。重构保持了所有原有类型的处理行为不变，并通过新增测试验证了正确性。
- **影响范围**：影响 Flink 集成模块的三个版本（v1.17、v1.18、v1.19）的 `FlinkParquetReaders` 类和对应的测试类 `TestFlinkParquetReader`，以及 `data` 模块的 `DataTest` 基类（仅可见性修改）。不影响其他模块。
- **回迁到 1.4.x 的注意事项**：
  1. 需要确认 1.4.x 分支支持哪些 Flink 版本。1.4.x 可能只支持较早的 Flink 版本（如 v1.15、v1.16、v1.17），需要检查是否存在对应的 `FlinkParquetReaders` 文件。
  2. 重构本身是行为保持的（behavior-preserving），不改变现有类型的读取行为，风险较低。
  3. 如果 1.4.x 分支的 Parquet 依赖版本不支持 `LogicalTypeAnnotation` 访问者接口，则需要先升级 Parquet 依赖。
  4. 回迁时需同步修改所有 Flink 版本目录下的 `FlinkParquetReaders` 和 `TestFlinkParquetReader`，以及 `DataTest` 的可见性修改。
  5. 如果 1.4.x 不计划支持纳秒时间戳，此重构的优先级可以降低，但仍有价值（改善代码可维护性）。
