# 提交 1638：Parquet: Add readers and writers for the internal object model (#11904)

## 提交信息

- **序号**：1638 / 4088
- **哈希**：67c52b55c074615b3de15f54c9460df8eb36d10d
- **短哈希**：67c52b55c
- **日期**：2025-01-25（Sat Jan 25 03:23:09 2025 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Parquet: Add readers and writers for the internal object model
- **PR/Issue**：#11904
- **共同作者**：Ryan Blue <blue@apache.org>

## 总体目的

Iceberg 内部存在两套"内存对象模型"：

1. **Generic 模型**：`Record`（`GenericRecord`）+ java.time 类型（`LocalDate`/`LocalDateTime`/`OffsetDateTime`/`LocalTime`），用于 `iceberg-data` 通用读取路径；
2. **Internal 模型**：`StructLike` + `Type.TypeID#javaClass()` 给出的"原始 Java 类型"（date 写成 int、timestamp/time 写成 long、UUID 写成 `UUID`、fixed 写成 `ByteBuffer` 等），是 Iceberg 自身元数据/写入路径（如 manifest 写入、Spark `InternalRow` 适配）实际使用的紧凑表示。

此前 Parquet 模块的 `BaseParquetReaders` / `BaseParquetWriter` 把 Date/Time/Timestamp/Fixed 等类型的 Reader/Writer 实现作为 `private static` 内部类硬编码在基类里，且只产出 java.time 类型——也就是说，基类实际只能服务 Generic 模型。任何想直接读写 internal 模型（用 long/int/UUID 而非 java.time）的调用方只能：要么走 Generic 然后二次转换（性能损失），要么自己复制一份 Builder 逻辑（重复代码）。Spark 等引擎也因此各自在 `SparkParquetReaders` 里重复实现了 `TimestampMillisReader`/`TimestampInt96Reader`。

本提交重构 Parquet 读写器层次，引入对 internal 模型的原生支持：

1. 新增 `InternalReader` / `InternalWriter`，直接消费 `StructLike` + 原始 Java 类型（long timestamp、int date、UUID、ByteBuffer fixed 等），用 `UnboxedReader`/直接 long 写入，避免装箱与 java.time 转换；
2. 把原 `BaseParquetReaders`/`BaseParquetWriter` 中硬编码的 java.time Reader/Writer 下沉到 `GenericParquetReaders`/`GenericParquetWriter` 自身（变为 `static` 包级嵌套类），基类改为通过 `protected` 钩子方法（`fixedReader`/`dateReader`/`timeReader`/`timestampReader` 等）让子类决定具体实现——默认实现委托给 Generic 的嵌套类，`InternalReader`/`InternalWriter` 覆写为 unboxed 版本；
3. 在 `ParquetValueReaders`/`ParquetValueWriters` 中抽出可复用的工具 Reader/Writer（`uuids`、`int96Timestamps`、`millisAsTimes`、`millisAsTimestamps`、`recordReader`、`fixedBuffers`、`UUIDWriter`、`RecordWriter`），供 Spark 与 Internal 共享，消除重复；
4. `BaseParquetReaders`/`BaseParquetWriter` 标记 `@Deprecated`（1.8.0 起，1.9.0 改为包级私有），明确这是内部 API；
5. 新增 `TestInternalParquet` 验证 internal 模型读写往返；`RandomUtil`/`ArrowReaderTest` 配套调整（UUID 直接用 `UUID` 类型而非 `byte[]`）。

## 如何达成设计目的

通过"模板方法 + 钩子"重构：

- 基类 `BaseParquetReaders`/`BaseParquetWriter` 保留 Builder 主体逻辑，但把"如何为 date/time/timestamp/fixed 创建 Reader/Writer"抽成 `protected` 方法，默认委托给 Generic 的实现；
- `GenericParquetReaders`/`GenericParquetWriter` 继承基类，把原本散在基类里的 java.time Reader/Writer 类搬到自己内部（`static` 包级），通过基类默认钩子被调用——保持 Generic 行为不变；
- 新增 `InternalReader`/`InternalWriter` 继承基类，覆写钩子返回 unboxed 版本：date/time/timestamp 用 `UnboxedReader`/`longs`/`ints`，fixed 用 `fixedBuffers`（ByteBuffer），UUID 用 `uuids`，struct 用 `recordReader`/`recordWriter`（基于 `StructLike`）；
- 把可复用 Reader/Writer 提到 `ParquetValueReaders`/`ParquetValueWriters` 顶层静态工厂，Spark 与 Internal 共用；
- 标 `@Deprecated` 让外部不再依赖基类作为公共 API。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalReader.java`（新增，93 行）

**修改目的**：直接读取 Parquet 到 internal 模型（`StructLike` + 原始 Java 类型）。

**工作逻辑**：

- `public class InternalReader<T extends StructLike> extends BaseParquetReaders<T>`，单例 `INSTANCE`；
- 静态工厂 `create(Schema, MessageType)` 与 `create(Schema, MessageType, Map<Integer, ?> idToConstant)` 委托给 `INSTANCE.createReader(...)`；
- `createStructReader` → `ParquetValueReaders.recordReader(types, fieldReaders, structType)`（基于 `StructLike` 的 `RecordReader`，但 `RecordReader` 内部用 `GenericRecord`——见后文注意点）；
- `fixedReader` → `ParquetValueReaders.BytesReader`（返回 `byte[]`）；
- `dateReader` → `UnboxedReader`（date 作为 int 读，对应 internal 模型 date 的 javaClass 是 `int`/`Integer`）；
- `timeReader`：MILLIS → `millisAsTimes`（返回 long），其他 → `UnboxedReader`（long 微秒）；
- `timestampReader`：INT96 → `int96Timestamps`（long 纳秒转 long），MILLIS → `millisAsTimestamps`（long），其他 → `UnboxedReader`（long 微秒）。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalWriter.java`（新增，70 行）

**修改目的**：把 internal 模型直接写入 Parquet。

**工作逻辑**：

- `public class InternalWriter<T extends StructLike> extends BaseParquetWriter<T>`，单例；
- 静态工厂 `create(MessageType)`；
- `createStructWriter` → `ParquetValueWriters.recordWriter(writers)`（基于 `StructLike` 的 `RecordWriter`，通过 `struct.get(index, Object.class)` 取字段）；
- `fixedWriter` → `ParquetValueWriters.fixedBuffers(desc)`（写 `ByteBuffer`）；
- `dateWriter`/`timeWriter`/`timestampWriter` → `ints`/`longs`/`longs`（直接写原始整型，对应 internal 模型的 javaClass）。

类注释明确："A Writer that consumes Iceberg's internal in-memory object model. Iceberg's internal in-memory object model produces the types defined in `Type.TypeID#javaClass()`."

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java`（修改，+44/-170）

**修改目的**：把 java.time Reader 实现下沉到 Generic，基类改为模板方法 + 钩子。

**工作逻辑**：

- 类标 `@Deprecated`（"since 1.8.0, will be made package-private in 1.9.0"）；
- 删除大量 java.time import（`Instant`/`LocalDate`/`LocalDateTime`/`OffsetDateTime`/`ZoneOffset`/`ChronoUnit`/`TimeUnit`/`ByteBuffer`/`ByteOrder`）；
- 新增 4 个 `protected` 钩子：`fixedReader`、`dateReader`、`timeReader(desc, unit)`、`timestampReader(desc, unit, isAdjustedToUTC)`，默认实现 `return new GenericParquetReaders.XxxReader(desc)`（即委托给 Generic 的同名嵌套类）；
- `LogicalTypeAnnotationParquetValueReaderVisitor`（内部 visitor）改为调用钩子而非直接 `new`：
  - `visit(DateLogicalType)` → `dateReader(desc)`；
  - `visit(TimeLogicalType)` → `timeReader(desc, unit)`（不再在 visitor 内部分支 MICROS/MILLIS）；
  - `visit(TimestampLogicalType)` → `timestampReader(desc, unit, shouldAdjustToUTC)`；
  - 新增 `visit(UUIDLogicalType)` → `ParquetValueReaders.uuids(desc)`（之前 UUID 没有专门处理）；
- `ReadBuilder.primitive` 中：
  - `primitive.getOriginalType() != null` 改为 `primitive.getLogicalTypeAnnotation() != null`（语义等价，但更现代的 API）；
  - `FIXED_LEN_BYTE_ARRAY` 分支 `new FixedReader` → `fixedReader(desc)`；
  - `INT96` 分支 `new TimestampInt96Reader` → `timestampReader(desc, NANOS, true)`（统一走钩子）；
- 删除原本所有 `private static` 嵌套类（`DateReader`/`TimestampReader`/`TimestampMillisReader`/`TimestampInt96Reader`/`TimestamptzReader`/`TimestamptzMillisReader`/`TimeMillisReader`/`TimeReader`/`FixedReader`）以及 `EPOCH`/`EPOCH_DAY` 常量——这些全部搬到 `GenericParquetReaders`。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java`（修改，+47/-104）

**修改目的**：与 `BaseParquetReaders` 对称，把 java.time Writer 下沉到 Generic，基类改为钩子。

**工作逻辑**：

- 类标 `@Deprecated`；
- 删除 java.time import 与 `Binary` import；
- 新增 4 个 `protected` 钩子：`fixedWriter`、`dateWriter`、`timeWriter`、`timestampWriter(desc, isAdjustedToUTC)`，默认实现 `return new GenericParquetWriter.XxxWriter(desc)`；
- `LogicalTypeWriterVisitor`：泛型从 `ParquetValueWriters.PrimitiveWriter<?>` 改为 `ParquetValueWriter<?>`（更宽），visitor 从 `private static` 改为 `private`（内部类，能访问 `BaseParquetWriter` 钩子方法）；
  - `visit(DateLogicalType)` → `dateWriter(desc)`；
  - `visit(TimeLogicalType)` → 增加 `MICROS` 校验（"Cannot write time in %s, only MICROS is supported"），调 `timeWriter(desc)`；
  - `visit(TimestampLogicalType)` → 已有 `MICROS` 校验保留，调 `timestampWriter(desc, isAdjustedToUTC)`；
- `WriteBuilder.primitive`：`FIXED_LEN_BYTE_ARRAY` 分支 `new FixedWriter` → `fixedWriter(desc)`；
- 删除原 `DateWriter`/`TimeWriter`/`TimestampWriter`/`TimestamptzWriter`/`FixedWriter` 嵌套类与 `EPOCH`/`EPOCH_DAY` 常量——搬到 `GenericParquetWriter`。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetReaders.java`（修改，+120/-16）

**修改目的**：承接从基类下沉的 java.time Reader 实现，并暴露为 `static` 包级类供基类默认钩子调用。

**工作逻辑**：

- 引入 java.time / `ColumnDescriptor` 等 import；
- 新增 `EPOCH`/`EPOCH_DAY` 常量；
- 把原基类里的 `DateReader`/`TimestampReader`/`TimestampMillisReader`/`TimestampInt96Reader`/`TimestamptzReader`/`TimestamptzMillisReader`/`TimeMillisReader`/`TimeReader`/`FixedReader` 全部搬过来，可见性从 `private static` 改为 `static`（包级）——基类默认钩子 `return new GenericParquetReaders.DateReader(desc)` 即可调用；
- 原 `RecordReader`（Generic 自己的内部 struct reader）删除，改为调用 `ParquetValueReaders.recordReader(...)`——把 `RecordReader` 也提到 `ParquetValueReaders` 顶层（见下文），消除与 Spark/Internal 的重复。

注意 `TimeMillisReader.read` 由 `column.nextLong()` 改为 `column.nextInteger()`（time millis 在 Parquet 中是 int32），是一个小修正。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetWriter.java`（修改，+74/-5）

**修改目的**：承接从基类下沉的 java.time Writer 实现。

**工作逻辑**：

- 引入 java.time / `ColumnDescriptor` / `Binary` / `Preconditions` import；
- 新增 `EPOCH`/`EPOCH_DAY` 常量；
- 新增 `static` 包级 `DateWriter`/`TimeWriter`/`TimestampWriter`/`TimestamptzWriter`/`FixedWriter`，逻辑与原基类版本一致；
- 原 `RecordWriter`（内部 struct writer）删除，改为 `ParquetValueWriters.recordWriter(writers)`——把 `RecordWriter` 提到 `ParquetValueWriters` 顶层，且改为泛型 `<T extends StructLike>` 而非固定 `Record`，使 Internal 也能复用。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java`（修改，+124）

**修改目的**：提供共享的 Reader 工厂与 Reader 实现，供 Generic / Internal / Spark 复用。

**工作逻辑**：

- 新增工厂方法：
  - `uuids(desc)` → `UUIDReader`（读 Binary 转 `UUID`，用 `UUIDUtil.convert`）；
  - `int96Timestamps(desc)` → `TimestampInt96Reader extends UnboxedReader<Long>`（读 INT96 为 long，用 `ParquetUtil.extractTimestampInt96`）；
  - `millisAsTimes(desc)` → `TimeMillisReader extends UnboxedReader<Long>`（`1000L * nextInteger()`，把毫秒转微秒 long）；
  - `millisAsTimestamps(desc)` → `TimestampMillisReader extends UnboxedReader<Long>`（`1000L * nextLong()`，毫秒转微秒 long）；
  - `recordReader(types, readers, struct)` → `RecordReader extends StructReader<Record, Record>`（从 GenericParquetReaders 搬来，使用 `GenericRecord.copy()` 模板）；
- 新增 `UUIDReader extends PrimitiveReader<UUID>`。

这些 Reader 都返回 `Long`/`UUID`/`Record`——是 internal 模型与 Spark 都需要的"原始类型"，Spark 此前在自己模块里重复实现，现在统一引用。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueWriters.java`（修改，+67）

**修改目的**：提供共享的 Writer 工厂与 Writer 实现。

**工作逻辑**：

- 新增工厂方法：
  - `uuids(desc)` → `UUIDWriter`（用 `UUIDUtil.convertToByteBuffer(value, TL buffer)` 写 16 字节 Binary，ThreadLocal ByteBuffer 复用）；
  - `fixedBuffers(desc)` → `FixedBufferWriter`（写 `ByteBuffer`，校验 `remaining == length`，用 `Binary.fromReusedByteBuffer`）；
  - `recordWriter(writers)` → `RecordWriter<T extends StructLike>`（通过 `struct.get(index, Object.class)` 取字段，泛型化以支持 Internal 的 `StructLike`，而非只支持 `Record`）；
- 引入 `StructLike` / `UUIDUtil` / `ByteOrder` 等 import。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestInternalParquet.java`（新增，83 行）

**修改目的**：验证 `InternalReader`/`InternalWriter` 的 Parquet 往返正确性。

**工作逻辑**：

- 继承 `AvroDataTest`（参数化 schema 测试基类），覆写 `writeAndValidate(Schema)`：
  - 用 `RandomInternalData.generate(schema, 100, 1376L)` 生成 100 条 `StructLike` 随机数据；
  - 用 `Parquet.writeData(...).createWriterFunc(InternalWriter::create)` 写入 `InMemoryOutputFile`；
  - 用 `Parquet.read(...).createReaderFunc(fileSchema -> InternalReader.create(schema, fileSchema))` 读回；
  - 用 `InternalTestHelpers.assertEquals(schema.asStruct(), expected, actual)` 逐行断言；
  - 再用 `reuseContainers()` 读一次验证复用容器路径正确。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java`（修改，+2/-39）

**修改目的**：消除 Spark 中重复的 `TimestampMillisReader`/`TimestampInt96Reader`，改用共享工厂。

**工作逻辑**：

- 删除 `import ByteOrder` / `import ParquetUtil`；
- 删除内部 `TimestampMillisReader`/`TimestampInt96Reader` 两个嵌套类（共约 35 行）；
- 调用点改为：
  - `TIMESTAMP_MILLIS` 分支 `new TimestampMillisReader(desc)` → `ParquetValueReaders.millisAsTimestamps(desc)`；
  - `INT96` 分支 `new TimestampInt96Reader(desc)` → `ParquetValueReaders.int96Timestamps(desc)`。

行为完全等价，但代码下沉到 parquet 模块共享。

### `api/src/test/java/org/apache/iceberg/util/RandomUtil.java`（修改，+8/-5）

**修改目的**：测试辅助代码纯重命名，无行为变化。

**工作逻辑**：`generateList`/`generateMap` 的参数名 `elementResult`→`elementSupplier`、`keyResult`→`keySupplier`、`valueResult`→`valueSupplier`，与方法内引用同步改名。无逻辑变化（仅命名更准确，因为参数是 `Supplier`）。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/ArrowReaderTest.java`（修改，+5/-10）

**修改目的**：UUID 字段在测试数据模型中改为直接使用 `UUID` 类型而非 `byte[]`，与 internal 模型对 UUID 的 javaClass 约定一致。

**工作逻辑**：

- 数据生成处：`ByteBuffer bb = UUIDUtil.convertToByteBuffer(UUID.randomUUID()); byte[] uuid = bb.array();` → `UUID uuid = UUID.randomUUID();`；另一处 `UUID.fromString(...)` 同理简化；
- 断言处：`ColumnVector::getBinary` 改为 `(array, i) -> UUIDUtil.convert(array.getBinary(i))`，`FixedSizeBinaryVector::get` 同理改为包 `UUIDUtil.convert`——把 UUID 的字节还原为 `UUID` 对象再比较，与新的字段类型一致。

## 小结

- **成效**：补齐了 Parquet 对 Iceberg internal 内存模型（`StructLike` + 原始 Java 类型）的原生读写支持，避免了 internal→Generic→java.time 的二次转换；通过模板方法 + 钩子重构消除基类与子类、Spark 与 parquet 模块之间的重复 Reader/Writer 实现；新增 UUID Reader/Writer 填补了之前 UUID 类型无专门处理的空白；为后续 Spark/其他引擎直接基于 internal 模型读写 Parquet 铺路。
- **影响范围**：
  - 公共 API 影响：`BaseParquetReaders`/`BaseParquetWriter` 标 `@Deprecated`（1.9.0 改包级私有），外部若直接继承这两个基类需迁移到 `GenericParquetReaders`/`GenericParquetWriter` 或新的 `InternalReader`/`InternalWriter`；
  - 行为影响：Generic 路径行为不变（Reader/Writer 类只是搬家）；Spark 路径行为不变（替换为等价工厂）；`TimeMillisReader.read` 修正为 `nextInteger()` 是一处隐含 bug 修复（time millis 在 Parquet 是 int32）；
  - 新增 `InternalReader`/`InternalWriter`/`TestInternalParquet` 为纯新增。
- **回迁到 1.4.x 的注意事项**：
  - 此重构涉及多个模块（parquet/spark/api/arrow 测试），cherry-pick 时需整体回迁，注意 `ParquetValueReaders`/`ParquetValueWriters` 新增的工厂方法与嵌套类是基础；
  - `InternalReader`/`InternalWriter` 依赖 `ParquetUtil.extractTimestampInt96`、`UUIDUtil.convert`/`convertToByteBuffer`、`RandomInternalData`、`InternalTestHelpers`、`InMemoryOutputFile` 等辅助类，1.4.x 上需确认均已存在；
  - `BaseParquetReaders`/`BaseParquetWriter` 标 `@Deprecated` 是 API 信号，1.4.x 若有下游依赖这两个基类做自定义子类化，回迁后会收到 deprecation 警告，但不影响运行；
  - `ArrowReaderTest` 的 UUID 改动依赖 `RandomInternalData`/`InternalTestHelpers` 对 UUID 字段使用 `UUID` javaClass 的约定，1.4.x 上若 internal 模型对 UUID 的 javaClass 仍是 `byte[]`/`ByteBuffer`，则该测试改动不应回迁；
  - 该 PR 是 1.8.0 系列重构的一部分（deprecation 注释明确提到 1.8.0/1.9.0），1.4.x 回迁时可视需要只取 `InternalReader`/`InternalWriter` 主体而保留基类原状（不标 @Deprecated），但需手动调整子类钩子。
