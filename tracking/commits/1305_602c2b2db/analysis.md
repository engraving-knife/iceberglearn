# 提交 1305：Flink 1.20: Update Flink to use planned Avro reads (#11386)

## 提交信息

- **序号**：1305 / 4088
- **哈希**：602c2b2dbecb81d7d84940f988579add7ffd1030
- **短哈希**：602c2b2db
- **日期**：2024-10-29（Tue Oct 29 17:54:24 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Flink 1.20: Update Flink to use planned Avro reads
- **PR/Issue**：#11386

## 总体目的

Iceberg 在 main 分支上已经引入了一套新的 Avro 读取基础设施（PR #9366 引入 `AvroWithPartnerVisitor`，PR #11108 引入 `ValueReaders.PlannedStructReader` 与 `ValueReaders.buildReadPlan`、`Avro.ReadBuilder.createResolvingReader`）。Spark 3.5 也已在 PR #11299 中迁移到这套新基础设施。本提交把 Flink 1.20 集成模块的 Avro 读取路径从旧实现 `FlinkAvroReader` 迁移到新的"planned Avro reads"实现 `FlinkPlannedAvroReader`，让 Flink 1.20 与 Spark 3.5 共享同一套读取语义。

新旧两套实现的核心差异：

1. **schema 对齐方式不同**：
   - 旧 `FlinkAvroReader` 用 `AvroSchemaWithTypeVisitor`，依赖"文件 Avro schema 与 Iceberg 期望 schema 结构位置一一对应"，靠外层 `ProjectionDatumReader` + Avro 的 `ResolvingDecoder` 处理列增删/重命名。
   - 新 `FlinkPlannedAvroReader` 用 `AvroWithPartnerVisitor` + `FieldIDAccessors`，按字段 ID 对齐 Iceberg 类型与 Avro schema，不依赖结构位置对齐。
2. **缺失字段处理更完善**：新实现通过 `ValueReaders.buildReadPlan` 在构建期就生成"读取计划" `(position, reader)` 列表，对期望 schema 中但文件里没有的字段，按以下优先级填充：
   - 分区常量（`idToConstant`）→ 用 `ConstantReader` 替换；
   - 字段 `initialDefault` → 用 `ConstantReader`；
   - `MetadataColumns.IS_DELETED` → 默认 false；
   - `MetadataColumns.ROW_POSITION` → 用 `ValueReaders.positions()`；
   - 可选字段 → 用 `ConstantReader(null)`；
   - 必填字段缺失 → 抛 `IllegalArgumentException`。
   旧实现依赖 `ResolvingDecoder.readFieldOrder` 处理缺失字段，无法支持 `initialDefault` 与 Iceberg 元数据列。
3. **常量注入提前**：旧实现把 `idToConstant` 在 `StructReader.read` 末尾通过 `positions/constants` 数组写入；新实现直接在 `buildReadPlan` 阶段把常量替换为 `ConstantReader`，避免每条记录都走一遍常量赋值循环。
4. **未投影字段跳过**：新实现 `PlannedStructReader.read` 中 `positions[i] == null` 时直接 `readers[i].skip(decoder)`，跳过文件中存在但不在期望 schema 里的字段。

旧 `FlinkAvroReader` 类被标记为 `@Deprecated`（注释"will be removed in 1.8.0; use FlinkPlannedAvroReader instead"），但保留以兼容外部调用方。

## 如何达成设计目的

- 新增 `FlinkPlannedAvroReader`，实现 `DatumReader<RowData>` 与 `SupportsRowPosition`：
  - 静态工厂 `create(Schema)` / `create(Schema, Map<Integer, ?> constants)`；
  - 构造时仅记录 `expectedType`（`schema.asStruct()`）与 `idToConstant`，`reader` 字段延迟到 `setSchema(Schema fileSchema)` 时构建；
  - `setSchema` 调用 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ReadBuilder(idToConstant), FieldIDAccessors.get())` 构建 `ValueReader<RowData>`；
  - 内部 `ReadBuilder` 继承 `AvroWithPartnerVisitor<Type, ValueReader<?>>`，按 Iceberg `Type` 伙伴与 Avro schema 节点构造对应 `ValueReader`；对 record 调用 `ValueReaders.buildReadPlan` + `FlinkValueReaders.struct(readPlan, numFields)` 返回新的 `PlannedStructReader`。
- 在 `FlinkValueReaders` 中新增 `struct(List<Pair<Integer, ValueReader<?>> readPlan, int numFields)` 工厂与 `PlannedStructReader` 内部类，把 `ValueReaders.PlannedStructReader<RowData>` 适配到 Flink `GenericRowData`。
- 在 `RowDataFileScanTaskReader.newAvroIterable` 中把 `createReaderFunc(readSchema -> new FlinkAvroReader(...))` 改为 `createReaderFunc(readSchema -> FlinkPlannedAvroReader.create(schema, idToConstant))`。
- 测试侧把 `TestFlinkAvroReaderWriter`、`TestRowProjection` 中的 `createReaderFunc(FlinkAvroReader::new)` 改为 `createResolvingReader(FlinkPlannedAvroReader::create)`，以验证新 reader 与新 builder 路径的正确性。
- 旧 `FlinkAvroReader` 类与构造函数加 `@Deprecated` 注解 + javadoc，提示 1.8.0 移除。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroReader.java`（修改，+12 行）

**修改目的**：标记旧 reader 为 `@Deprecated`，引导外部调用方迁移到 `FlinkPlannedAvroReader`。

**工作逻辑**：

- 类级加 `@Deprecated` + javadoc `@deprecated will be removed in 1.8.0; use FlinkPlannedAvroReader instead.`；
- 两个 public 构造函数（`FlinkAvroReader(Schema, Schema)` 与 `FlinkAvroReader(Schema, Schema, Map<Integer, ?>)`）分别加 `@Deprecated` 与同名 javadoc。
- 类内部逻辑保持不变（仍用 `AvroSchemaWithTypeVisitor` + `StructReader`），允许旧调用方继续工作但发出 deprecation 警告。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkPlannedAvroReader.java`（新增，192 行）

**修改目的**：基于 `AvroWithPartnerVisitor` 与 `ValueReaders.PlannedStructReader` 的新 Flink Avro 读取器。

**工作逻辑**：

- `public class FlinkPlannedAvroReader implements DatumReader<RowData>, SupportsRowPosition`；
- 字段：`Types.StructType expectedType`、`Map<Integer, ?> idToConstant`、`ValueReader<RowData> reader`；
- 静态工厂 `create(org.apache.iceberg.Schema schema)` 与 `create(org.apache.iceberg.Schema schema, Map<Integer, ?> constants)`，委托给私有构造器，构造器把 `schema.asStruct()` 存为 `expectedType`；
- `setSchema(Schema fileSchema)`：调用
  ```java
  AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ReadBuilder(idToConstant), AvroWithPartnerVisitor.FieldIDAccessors.get())
  ```
  得到 `ValueReader<RowData>`。`FieldIDAccessors` 通过 Iceberg `Type.asStructType().field(fieldId)` 按 ID 查找伙伴字段类型，无需结构位置对齐。
- `read(RowData reuse, Decoder decoder)`：`reader.read(decoder, reuse)`；
- `setRowPositionSupplier(Supplier<Long> posSupplier)`：若 reader 实现 `SupportsRowPosition` 则下推，供 position 列使用。
- 内部 `private static class ReadBuilder extends AvroWithPartnerVisitor<Type, ValueReader<?>>`：
  - `record(Type partner, Schema record, List<ValueReader<?>> fieldReaders)`：`partner == null` 时 `ValueReaders.skipStruct(fieldReaders)`（未投影的结构整体跳过）；否则 `ValueReaders.buildReadPlan(expected.asStructType(), record, fieldReaders, idToConstant)` 生成读取计划，再 `FlinkValueReaders.struct(readPlan, expected.fields().size())`。注释中 TODO 提到"应传 expected 以复用 struct 容器"，留作后续优化。
  - `union` → `ValueReaders.union(options)`；`array` → `FlinkValueReaders.array(elementReader)`；`arrayMap` → `FlinkValueReaders.arrayMap(keyReader, valueReader)`；`map` → `FlinkValueReaders.map(FlinkValueReaders.strings(), valueReader)`（Iceberg map 的 key 总是 string）；
  - `primitive(Type partner, Schema primitive)`：先看 Avro `LogicalType`：
    - `date` → `ValueReaders.ints()`（Flink 与 Avro 均以 int 表示日期，自纪元天数）；
    - `time-micros` → `FlinkValueReaders.timeMicros()`；
    - `timestamp-millis` / `timestamp-micros` → `FlinkValueReaders.timestampMills()` / `timestampMicros()`；
    - `decimal` → `FlinkValueReaders.decimal(ValueReaders.decimalBytesReader(primitive), precision, scale)`；
    - `uuid` → `FlinkValueReaders.uuids()`；
    - 其它 logical type 抛 `IllegalArgumentException`。
  - 否则按 Avro primitive 类型分发：NULL→`nulls()`、BOOLEAN→`booleans()`、INT→ 若 partner 是 LONG 则 `intsAsLongs()` 否则 `ints()`、LONG→`longs()`、FLOAT→ 若 partner 是 DOUBLE 则 `floatsAsDoubles()` 否则 `floats()`、DOUBLE→`doubles()`、STRING→`FlinkValueReaders.strings()`、FIXED→`ValueReaders.fixed(size)`、BYTES→`ValueReaders.bytes()`、ENUM→`FlinkValueReaders.enums(symbols)`，其它抛异常。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueReaders.java`（修改，+32 行）

**修改目的**：为 `FlinkPlannedAvroReader` 提供基于 read plan 的 struct 读取器。

**工作逻辑**：

- 新增 import `org.apache.iceberg.util.Pair`；
- 新增包级静态工厂：
  ```java
  static ValueReader<RowData> struct(List<Pair<Integer, ValueReader<?>>> readPlan, int numFields) {
    return new PlannedStructReader(readPlan, numFields);
  }
  ```
- 新增私有静态内部类 `PlannedStructReader extends ValueReaders.PlannedStructReader<RowData>`：
  - 字段 `int numFields`；
  - `reuseOrCreate(Object reuse)`：若 `reuse instanceof GenericRowData` 且 `getArity() == numFields` 则复用，否则 `new GenericRowData(numFields)`；
  - `get(RowData struct, int pos)`：始终返回 `null`（Flink `GenericRowData` 不支持按位置读取复用值，因此每次都新建值对象）；
  - `set(RowData struct, int pos, Object value)`：`((GenericRowData) struct).setField(pos, value)`；
  - 实际 `read` 逻辑由父类 `ValueReaders.PlannedStructReader.read` 提供：按 `readers[]` 顺序读，`positions[i] != null` 时调用 `set(struct, positions[i], readers[i].read(decoder, get(struct, positions[i])))`，否则 `readers[i].skip(decoder)`。
- 旧的 `struct(readers, struct, idToConstant)` 与 `StructReader` 保留，供旧 `FlinkAvroReader` 使用。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java`（修改，+2/-2 行）

**修改目的**：把扫描任务读取 Avro 数据文件的 reader 工厂切换到新实现。

**工作逻辑**：在 `newAvroIterable` 中：

```java
// before
.createReaderFunc(readSchema -> new FlinkAvroReader(schema, readSchema, idToConstant));
// after
.createReaderFunc(readSchema -> FlinkPlannedAvroReader.create(schema, idToConstant));
```

注意：仍调用 `createReaderFunc(Function<Schema, DatumReader<?>>)`（单参数版本），并未切到 `createResolvingReader`。这是因为 `RowDataFileScanTaskReader` 通过 `Avro.read(...).project(schema)` + `ProjectionDatumReader` 走标准投影路径，新 reader 的 `setSchema` 内部已经用 `AvroWithPartnerVisitor` 按 field ID 解析文件 schema，因此无需 `createResolvingReader` 的双参数回调。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroReaderWriter.java`（修改，+1/-1 行）

**修改目的**：把测试中的 reader 工厂改为新实现，并切换到 `createResolvingReader` 验证新 API。

**工作逻辑**：

```java
// before
.createReaderFunc(FlinkAvroReader::new)
// after
.createResolvingReader(FlinkPlannedAvroReader::create)
```

`createResolvingReader` 是 main 分支 `Avro.ReadBuilder` 新增的方法（PR #9366 引入），接收 `BiFunction<org.apache.iceberg.Schema, Schema, DatumReader<?>>`，在 builder 内部把 Iceberg schema 与 Avro readSchema 同时传给 reader 工厂，使 reader 可基于两种 schema 自行解析。这里 `FlinkPlannedAvroReader::create` 的签名 `(Schema, Map<Integer, ?>) -> FlinkPlannedAvroReader` 与 `BiFunction<Schema, Schema, DatumReader<?>>` 不直接匹配，实际依赖 `create(Schema)` 的单参重载（`BiFunction` 第二个参数 readSchema 被忽略，因为新 reader 在 `setSchema` 时才接收文件 schema）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestRowProjection.java`（修改，+1/-1 行）

**修改目的**：与 `TestFlinkAvroReaderWriter` 一致，把投影读取测试切到新 reader。

**工作逻辑**：把 `Avro.read(...).project(readSchema).createReaderFunc(FlinkAvroReader::new)` 改为 `.createResolvingReader(FlinkPlannedAvroReader::create)`，验证新 reader 在投影场景下仍能正确返回唯一记录。

## 小结

- **成效**：Flink 1.20 的 Avro 读取路径迁移到 main 分支统一的 `AvroWithPartnerVisitor` + `PlannedStructReader` 基础设施，与 Spark 3.5 对齐。新实现按字段 ID 对齐文件 schema 与期望 schema，原生支持分区常量、字段 `initialDefault`、Iceberg 元数据列（ROW_POSITION、IS_DELETED），并在构建期生成 read plan 跳过未投影字段，避免依赖 Avro `ResolvingDecoder` 处理 Iceberg 语义。旧 `FlinkAvroReader` 标记 `@Deprecated`，计划 1.8.0 移除。
- **影响范围**：仅 `flink/v1.20/flink` 模块，4 个生产文件（1 新增 3 修改）+ 2 个测试文件。`FlinkPlannedAvroReader` 是 public 类，可被外部 Flink 集成代码直接使用。`FlinkValueReaders.PlannedStructReader` 为包级私有。无对 API 模块的修改，无格式变更。
- **回迁到 1.4.x 的注意事项**：此提交强依赖 main 分支已合入的以下基础设施，1.4.x 分支目前并不具备：
  1. `core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerVisitor.java`（PR #9366）；
  2. `ValueReaders.PlannedStructReader` 与 `ValueReaders.buildReadPlan`（PR #11108）；
  3. `Avro.ReadBuilder.createResolvingReader`（PR #9366）；
  4. `ValueReaders.replaceWithConstant` / `ValueReaders.constant` 等辅助方法。
  
  因此回迁时必须先回迁上述三个基础 PR，再回迁本提交，否则编译失败。同时注意：
  - 1.4.x 上其它 Flink 版本（1.15/1.16/1.17）仍使用旧 `FlinkAvroReader`，本提交仅迁移 1.20，回迁后需评估是否一并迁移其它版本以保持一致性；
  - 旧 `FlinkAvroReader` 的 `@Deprecated` 提示 1.8.0 移除，1.4.x 若计划长期维护，可暂不标记 deprecation 以避免误导；
  - `PlannedStructReader.get` 始终返回 null（不复用 Flink RowData 字段值），对读性能有轻微影响，但与 Spark 实现一致，可接受。
