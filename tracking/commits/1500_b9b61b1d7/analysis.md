# 提交 1500：Avro: Support default values for generic data (#11786)

## 提交信息

- **序号**：1500 / 4088
- **哈希**：b9b61b1d72ebb192d5e90453ff7030ece73d2603
- **短哈希**：b9b61b1d7
- **日期**：2024-12-16（Mon Dec 16 14:31:01 2024 -0800）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Avro: Support default values for generic data (#11786)
- **PR/Issue**：#11786

## 总体目的

这是上一个提交（1499 Parquet defaults）的 Avro 对应版本。Iceberg schema 字段可声明 `initial default`，当老数据文件缺少新增字段时，读取时应按默认值填充。Avro 此前的 generic data 读取器（`DataReader`）在字段缺失时同样一律返回 null，未实现默认值语义，且对必填字段缺失也无显式报错。

本提交引入新的 `PlannedDataReader` 作为 `DataReader` 的替代品，基于"读取计划"（read plan）构建器 `ValueReaders.buildReadPlan` 与 `PlannedStructReader`（已在 `core` 的 avro 包中存在）实现默认值填充；并把所有 generic data Avro 读取入口（`BaseDeleteLoader`、`GenericReader`、`IcebergInputFormat`、各测试）从 `DataReader::create` + `createReaderFunc` 切换到 `PlannedDataReader::create` + 新的 `createResolvingReader` API。`DataReader` 与旧 `RawDecoder` 构造器标记为 `@Deprecated`（2.0.0 移除），但保留以兼容。

## 如何达成设计目的

1. 新增 `PlannedDataReader<T> implements DatumReader<T>, SupportsRowPosition`：
   - `create(expectedSchema)` / `create(expectedSchema, idToConstant)` 工厂。
   - `setSchema(Schema fileSchema)` 用 `AvroWithPartnerVisitor.visit(expectedSchema.asStruct(), fileSchema, new ReadBuilder(...))` 构建 `ValueReader<T>`。`ReadBuilder` 是 `AvroWithPartnerVisitor<Type, ValueReader<?>>`，在 `record(...)` 中调用 `ValueReaders.buildReadPlan(expected, record, fieldReaders, idToConstant)` 生成读取计划，再用 `GenericReaders.struct(readPlan, expected)` 构造基于计划的 struct reader。其它 primitive/array/map/union 方法复用 `GenericReaders`/`ValueReaders` 已有实现。
2. 在 `GenericReaders` 中新增 `struct(List<Pair<Integer, ValueReader<?>>>, StructType)` 工厂与 `PlannedRecordReader` 内部类（继承 `ValueReaders.PlannedStructReader<Record>`，实现 `reuseOrCreate`/`get`/`set`，复用 `GenericRecord.create(structType)`）。
3. `RawDecoder` 新增静态工厂 `create(readSchema, readerFunction, writeSchema)`，接收一个接收 Iceberg schema 的函数（而非旧版接收 Avro schema 的函数），便于对接 `PlannedDataReader::create`；旧构造器标记 `@Deprecated`。`IcebergDecoder.addSchema` 改用 `RawDecoder.create(readSchema, PlannedDataReader::create, writeSchema)`；`KeyMetadataDecoder` 同样切换。
4. 把 `Avro.read(...).createReaderFunc(DataReader::create)` 全部替换为 `Avro.read(...).createResolvingReader(PlannedDataReader::create)`。`createResolvingReader` 是 Avro 读取构建器上的新方法，专门接收"按 Iceberg schema 构造 resolving reader"的函数，配合 `PlannedDataReader` 实现默认值与 schema 演进解析。
5. 测试：`data` 模块的 `TestGenericData`（Avro 版）新增与 Parquet 版对称的默认值测试（必填无默认抛错、默认值、null 默认、嵌套 struct/map/list 默认）；多个测试基类把读取调用切换到新 API。

## 修改详情

### `core/src/main/java/org/apache/iceberg/data/avro/PlannedDataReader.java`（新增）

**修改目的**：实现支持默认值与 schema 解析的 generic data Avro reader。

**工作逻辑**：

- 字段：`expectedSchema`（Iceberg 期望 schema）、`idToConstant`（分区列等常量）、`reader`（构建出的 `ValueReader<T>`）。
- `setSchema(Schema fileSchema)`：调用 `AvroWithPartnerVisitor.visit(expectedSchema.asStruct(), fileSchema, new ReadBuilder(idToConstant), FieldIDAccessors.get())` 构建读取器。partner 是期望 struct 类型，fileSchema 是文件 Avro schema，访问器按 field ID 对齐。
- `ReadBuilder.record(...)`：若 partner 为 null（文件有、期望没有的记录）走 `ValueReaders.skipStruct`；否则 `ValueReaders.buildReadPlan(expected, record, fieldReaders, idToConstant)` 生成读取计划（`List<Pair<Integer, ValueReader<?>>>`），再用 `GenericReaders.struct(readPlan, expected)` 包装。`buildReadPlan` 内部已实现"按字段 ID 找 reader、缺失字段用 `initialDefault`、必填无默认抛错"的语义（在 `core` 的 avro `ValueReaders` 中）。
- `ReadBuilder.primitive(...)` 处理 Avro logical types（date/time/timestamp/decimal/uuid）与基础类型，并处理 INT→LONG、FLOAT→DOUBLE 的类型提升。
- `read(T reuse, Decoder decoder)` 直接委托 `reader.read(decoder, reuse)`；`setRowPositionSupplier` 透传给支持行位置的 reader。

### `core/src/main/java/org/apache/iceberg/data/avro/GenericReaders.java`

**修改目的**：提供基于读取计划的 struct reader 构造入口。

**工作逻辑**：

- 新增 `import org.apache.iceberg.util.Pair;`。
- 新增 `static ValueReader<Record> struct(List<Pair<Integer, ValueReader<?>>> readPlan, StructType struct)`，返回 `new PlannedRecordReader(readPlan, struct)`。
- 新增私有静态类 `PlannedRecordReader extends ValueReaders.PlannedStructReader<Record>`：`reuseOrCreate(Object reuse)` 复用传入的 Record 或新建 `GenericRecord.create(structType)`；`get`/`set` 委托 `struct.get(pos)` / `struct.set(pos, value)`。

### `core/src/main/java/org/apache/iceberg/data/avro/RawDecoder.java`

**修改目的**：提供接收 Iceberg schema 函数的工厂，便于对接 `PlannedDataReader`。

**工作逻辑**：

- 新增静态工厂 `<D> RawDecoder<D> create(org.apache.iceberg.Schema readSchema, Function<org.apache.iceberg.Schema, DatumReader<D>> readerFunction, Schema writeSchema)`：`DatumReader<D> reader = readerFunction.apply(readSchema); reader.setSchema(writeSchema); return new RawDecoder<>(reader);`。注意函数入参是 Iceberg schema（旧版是 Avro schema）。
- 旧构造器 `RawDecoder(readSchema, Function<Schema, DatumReader<?>>, writeSchema)` 标记 `@Deprecated`（2.0.0 移除）。
- 新增私有构造器 `RawDecoder(DatumReader<D> reader)` 供工厂使用。

### `core/src/main/java/org/apache/iceberg/data/avro/IcebergDecoder.java`

**修改目的**：切换到新 `RawDecoder.create`。

**工作逻辑**：`addSchema(writeSchema)` 中：

```java
RawDecoder<D> decoder = RawDecoder.create(readSchema, PlannedDataReader::create, writeSchema);
```

替换原先 `new RawDecoder<>(readSchema, avroSchema -> DataReader.create(readSchema, avroSchema), writeSchema)`。

### `core/src/main/java/org/apache/iceberg/encryption/KeyMetadataDecoder.java`

**修改目的**：切换到新 `RawDecoder.create`。

**工作逻辑**：把 `new RawDecoder<>(readSchema, GenericAvroReader::create, writeSchema)` 改为 `RawDecoder.create(readSchema, GenericAvroReader::create, writeSchema)`。注意 `GenericAvroReader::create` 的签名需匹配新工厂期望的 `Function<IcebergSchema, DatumReader>`。

### `core/src/main/java/org/apache/iceberg/data/avro/DataReader.java`

**修改目的**：标记旧 reader 为弃用。

**工作逻辑**：在类上加 `@Deprecated` 注解与 Javadoc `@deprecated will be removed in 2.0.0; use PlannedDataReader instead.`。类体不变，保留兼容。

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java`

**修改目的**：删除文件加载走新 reader。

**工作逻辑**：`case AVRO:` 分支：

```java
return Avro.read(inputFile)
    .project(projection)
    .reuseContainers()
    .createResolvingReader(PlannedDataReader::create)
    .build();
```

替换 `createReaderFunc(DataReader::create)`。import 由 `DataReader` 改为 `PlannedDataReader`。

### `data/src/main/java/org/apache/iceberg/data/GenericReader.java`

**修改目的**：generic data 读取走新 reader。

**工作逻辑**：Avro 读取构建处：

```java
Avro.read(input)
    .project(fileProjection)
    .createResolvingReader(schema -> PlannedDataReader.create(schema, partition))
    .split(task.start(), task.length());
```

替换原先 `createReaderFunc(avroSchema -> DataReader.create(fileProjection, avroSchema, partition))`。注意新 API 把 schema 解析交给 `createResolvingReader`，分区常量 `partition` 通过闭包传入。

### `mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java`

**修改目的**：MapReduce 输入格式走新 reader。

**工作逻辑**：`case GENERIC:` 分支：

```java
avroReadBuilder.createResolvingReader(
    schema -> PlannedDataReader.create(
        schema, constantsMap(task, IdentityPartitionConverters::convertConstant)));
```

替换原先 `createReaderFunc((expIcebergSchema, expAvroSchema) -> DataReader.create(...))`。

### `data/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java`

**修改目的**：覆盖默认值读取场景（与 Parquet 版对称）。

**工作逻辑**：

- `writeAndValidate(Schema)` 重构为 `writeAndValidate(writeSchema, expectedSchema)`；写入用 `writeSchema`、读取投影用 `expectedSchema`，调用从 `createReaderFunc(DataReader::create)` 改为 `createResolvingReader(PlannedDataReader::create)`。
- 新增与 Parquet 版对称的测试：`testMissingRequiredWithoutDefault`（抛 `IllegalArgumentException("Missing required field: missing_str")`）、`testDefaultValues`（默认 "orange"、34）、`testNullDefaultValue`（optional date 缺失返回 null）、`testNestedDefaultValue`（嵌套 struct 内 float 默认 -0.0F）、`testMapNestedDefaultValue`（map 值 struct 内 int 默认 34）、`testListNestedDefaultValue`（list 元素 struct 内 int 默认 34）。

### 测试基类切换（`TestAvroDataWriter`、`TestAvroDeleteWriters`、`TestAvroFileSplit`、`TestEncryptedAvroFileSplit`、`TestGenericReadProjection`、`TestAppenderFactory`、`TestFileWriterFactory`、`TestGenericSortedPosDeleteWriter`、`TestTaskEqualityDeltaWriter`、`AbstractTestFlinkAvroReaderWriter`）

**修改目的**：把读取调用从 `createReaderFunc(DataReader::create)` 切换到 `createResolvingReader(PlannedDataReader::create)`。

**工作逻辑**：每个测试文件中：

- import 由 `org.apache.iceberg.data.avro.DataReader` 改为 `org.apache.iceberg.data.avro.PlannedDataReader`。
- `Avro.read(...).project(schema).createReaderFunc(DataReader::create).build()` 改为 `Avro.read(...).project(schema).createResolvingReader(PlannedDataReader::create).build()`（部分文件链式格式微调）。

这是机械式替换，目的是让所有 Avro generic 读取路径都走新的默认值支持实现。

## 小结

- **成效**：Avro generic data 读取现支持字段默认值语义，与 Parquet 版对齐；新增 `PlannedDataReader` 作为 `DataReader` 的替代，基于读取计划实现 schema 演进解析；引入 `RawDecoder.create` 静态工厂与 `Avro.createResolvingReader` API 统一了"按 Iceberg schema 构造 reader"的模式；旧 `DataReader` 与旧 `RawDecoder` 构造器标记弃用（2.0.0 移除）。测试覆盖顶层与 struct/map/list 嵌套默认值场景，以及必填无默认、optional 无默认边界。
- **影响范围**：跨 `core`、`data`、`mr`、`flink/v1.20` 四个模块共 20 个文件，533 行新增/42 行删除。涉及主代码（新增 `PlannedDataReader`、`GenericReaders`/`RawDecoder`/`IcebergDecoder`/`KeyMetadataDecoder`/`DataReader` 改动、`BaseDeleteLoader`/`GenericReader`/`IcebergInputFormat` 切换）与多个测试。
- **回迁到 1.4.x 的注意事项**：
  - 本提交是默认值特性的 Avro 实现，**应与 1499（Parquet defaults）一起回迁**，以保证默认值语义在所有文件格式上一致。
  - 强依赖前置条件：1.4.x 必须已具备 `ValueReaders.buildReadPlan`、`ValueReaders.PlannedStructReader`、`Avro.createResolvingReader` API、`Types.NestedField.initialDefault()`/`withInitialDefault(...)` builder。这些可能在更早的提交中引入，回迁前需确认 1.4.x 已有；若缺失，需先回迁前置提交。
  - `PlannedDataReader` 是新增类，回迁安全；`DataReader` 标记 `@Deprecated` 是兼容性变更，不影响现有调用方。
  - `RawDecoder.create` 是新增工厂，旧构造器仍可用，回迁后 `KeyMetadataDecoder`、`IcebergDecoder` 切换到新工厂是必要的一并改动。
  - 测试切换（10 个测试文件）是机械式 API 替换，回迁时需逐文件确认 1.4.x 中对应测试的基线；若 1.4.x 的 `Avro.read(...)` 构建器尚无 `createResolvingReader` 方法，则切换无法进行，必须先回迁该构建器方法。
  - 回迁后应运行 `core`、`data`、`mr`、`flink` 模块的 Avro 相关测试套件，特别关注 schema 演进、删除文件读取、MapReduce 输入格式等场景。
