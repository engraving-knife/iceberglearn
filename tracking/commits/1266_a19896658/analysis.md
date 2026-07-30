# 提交 1266：Spark 3.5: Update Spark to use planned Avro reads (#11299)

## 提交信息

- **序号**：1266 / 4088
- **哈希**：a19896658c98220ea0115d5d93ba6dff40c5c4e9
- **短哈希**：a19896658
- **日期**：2024-10-22（Tue Oct 22 09:13:01 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Spark 3.5: Update Spark to use planned Avro reads (#11299)
- **PR/Issue**：#11299

## 总体目的

Iceberg 读取 Avro 数据文件（如 manifest 文件、metadata 文件中的 Avro 格式数据）时，需要处理"文件实际 schema"与"期望读取 schema"之间的差异（列裁剪、字段重排、schema 演进）。此前的 `SparkAvroReader` 依赖 Avro 库自带的 `ResolvingDecoder` 来做 schema 解析：它接收期望的 Iceberg schema 和文件 Avro schema，让 Avro 库在读取时自动解析差异。

这种方式存在不足：

1. **依赖 Avro ResolvingDecoder 的行为**：ResolvingDecoder 按 Avro 字段名/位置做解析，与 Iceberg 的字段 ID 体系不完全对齐，在字段重命名或位置变化时可能出现解析问题。
2. **读取效率**：ResolvingDecoder 会按 `readFieldOrder()` 逐字段读取，无法提前规划只读必要列。
3. **缺乏控制力**：Iceberg 无法精确控制哪些字段被读取、以什么顺序读取，难以优化。

Iceberg core 中已有一套"partner visitor"模式（`AvroWithPartnerVisitor`）：用 Iceberg 类型作为"partner"遍历 Avro 文件 schema，按字段 ID 匹配构建只读必要字段的 `ValueReader` 树，并生成"读取计划"（read plan）——`List<Pair<Integer, ValueReader<?>>>`，其中 `Integer` 是输出 struct 中的位置、`ValueReader` 是该字段的读取器。core 中的 `ValueReaders.PlannedStructReader` 基于这个 read plan 读取，只读取需要的字段并放到正确位置。

本提交把 Spark 3.5 的 Avro 读取路径从旧的 `SparkAvroReader`（依赖 ResolvingDecoder）迁移到新的 `SparkPlannedAvroReader`（使用 planned reads / partner visitor 模式），让 Spark 读取 Avro 数据时使用 Iceberg 的字段 ID 解析而非 Avro 库的名称解析，提升正确性与效率。旧 `SparkAvroReader` 标记 `@Deprecated`（1.8.0 移除）。

## 如何达成设计目的

1. **新增 `SparkPlannedAvroReader`**：实现 `DatumReader<InternalRow>`，在 `setSchema(fileSchema)` 时用 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, readBuilder, FieldIDAccessors.get())` 构建 `ValueReader<InternalRow>` 树。内部 `ReadBuilder` 是一个 `AvroWithPartnerVisitor<Type, ValueReader<?>>`，为每种 Avro 类型节点创建对应的 Spark `ValueReader`（复用 `SparkValueReaders` 的工厂方法），record 节点用 `ValueReaders.buildReadPlan` 生成读取计划并创建 `SparkValueReaders.struct(readPlan, numFields)`。
2. **新增 `SparkValueReaders.PlannedStructReader`**：继承 core 的 `ValueReaders.PlannedStructReader<InternalRow>`，实现 `reuseOrCreate`/`get`/`set` 方法以适配 Spark 的 `InternalRow`/`GenericInternalRow`。
3. **暴露 core API**：把 `AvroWithPartnerVisitor.FieldIDAccessors` 从包级私有改为 `public`，把 `ValueReaders.buildReadPlan` 从包级私有改为 `public` 并把 `idToConstant` 参数从 `Map<Integer, Object>` 放宽为 `Map<Integer, ?>`，让 Spark 模块可调用。
4. **切换 `BaseRowReader`**：Avro 读取从 `new SparkAvroReader(projection, readSchema, idToConstant)` 改为 `SparkPlannedAvroReader.create(projection, idToConstant)`，并使用新的 `createResolvingReader` API（接收 Iceberg schema 而非 Avro schema）。
5. **测试迁移**：3 个测试从 `createReaderFunc(SparkAvroReader::new)` 改为 `createResolvingReader(SparkPlannedAvroReader::create)`。
6. **废弃旧 reader**：`SparkAvroReader` 类和构造方法标记 `@Deprecated`（1.8.0 移除）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkPlannedAvroReader.java`（新增，190 行）

**修改目的**：使用 planned reads 模式的新 Spark Avro reader。

**工作逻辑**：

- 实现 `DatumReader<InternalRow>` 和 `SupportsRowPosition`。
- 静态工厂 `create(Schema)` / `create(Schema, Map<Integer, ?> constants)`。
- `setSchema(Schema fileSchema)`：用 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ReadBuilder(idToConstant), FieldIDAccessors.get())` 构建 `ValueReader<InternalRow>`。`expectedType` 是期望 Iceberg schema 的 `asStruct()`。
- `read(InternalRow reuse, Decoder decoder)`：委托给 `reader.read(decoder, reuse)`。
- `setRowPositionSupplier(Supplier<Long>)`：透传给底层 reader（若实现 `SupportsRowPosition`）。
- 内部 `ReadBuilder extends AvroWithPartnerVisitor<Type, ValueReader<?>>`：
  - `record`：用 `ValueReaders.buildReadPlan(expected, record, fieldReaders, idToConstant)` 生成读取计划，返回 `SparkValueReaders.struct(readPlan, expected.fields().size())`。
  - `primitive`：按 Avro logical type / primitive type 创建对应 reader（date、timestamp-millis→micros 转换、timestamp-micros、decimal、uuid、int→long 提升、float→double 提升等），复用 `SparkValueReaders` 和 `ValueReaders` 的工厂方法。
  - `union`/`array`/`arrayMap`/`map`：委托 `SparkValueReaders`/`ValueReaders`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkValueReaders.java`（修改，+38 行）

**修改目的**：新增 `PlannedStructReader` 适配 Spark `InternalRow`。

**工作逻辑**：

- 新增工厂 `struct(List<Pair<Integer, ValueReader<?>>> readPlan, int numFields)` 返回 `new PlannedStructReader(readPlan, numFields)`。
- 新增 `PlannedStructReader extends ValueReaders.PlannedStructReader<InternalRow>`：
  - `reuseOrCreate`：若 reuse 是 `GenericInternalRow` 且字段数匹配则复用，否则新建 `GenericInternalRow(numFields)`。
  - `get(InternalRow, int pos)`：返回 `null`（planned reader 不复用子值，因 read plan 只读必要字段）。
  - `set(InternalRow, int pos, Object value)`：非 null 时 `struct.update(pos, value)`，null 时 `struct.setNullAt(pos)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java`（修改，+2/-2 行）

**修改目的**：切换 Avro 读取器实现。

**工作逻辑**：

- import 从 `SparkAvroReader` 改为 `SparkPlannedAvroReader`。
- `createReaderFunc(readSchema -> new SparkAvroReader(projection, readSchema, idToConstant))` 改为 `createReaderFunc(readSchema -> SparkPlannedAvroReader.create(projection, idToConstant))`。
  - 注意：这里仍用 `createReaderFunc`（接收 Avro schema 的单参数函数），但 `SparkPlannedAvroReader.create` 只用 Iceberg schema，忽略传入的 `readSchema`。实际上后续会切换到 `createResolvingReader` API（测试中已用），但 `BaseRowReader` 此处暂用 `createReaderFunc` 包装。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkAvroReader.java`（修改，+12 行）

**修改目的**：标记旧 reader 为 `@Deprecated`。

**工作逻辑**：类和两个构造方法各加 `@Deprecated` 和 `@deprecated will be removed in 1.8.0; use SparkPlannedAvroReader instead.` 的 javadoc。

### `core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerVisitor.java`（修改，1 行）

**修改目的**：把 `FieldIDAccessors` 从包级私有改为 `public`，让 Spark 模块可用。

```java
-  static class FieldIDAccessors implements AvroWithPartnerVisitor.PartnerAccessors<Type> {
+  public static class FieldIDAccessors implements AvroWithPartnerVisitor.PartnerAccessors<Type> {
```

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java`（修改，+2/-2 行）

**修改目的**：把 `buildReadPlan` 从包级私有改为 `public`，并放宽 `idToConstant` 类型。

```java
-  static List<Pair<Integer, ValueReader<?>>> buildReadPlan(
+  public static List<Pair<Integer, ValueReader<?>>> buildReadPlan(
       Types.StructType expected, Schema record, List<ValueReader<?>> fieldReaders,
-      Map<Integer, Object> idToConstant) {
+      Map<Integer, ?> idToConstant) {
```

放宽 `Map<Integer, Object>` 到 `Map<Integer, ?>` 是因为 `SparkPlannedAvroReader` 传入的 `constants` 类型是 `Map<Integer, ?>`（值类型可能是任意对象），原签名要求 `Map<Integer, Object>` 会导致类型不兼容。

### 测试文件（3 个文件，各 1-2 行修改）

- `TestSparkAvroEnums.java`、`TestSparkAvroReader.java`、`TestDataFrameWrites.java`：从 `.createReaderFunc(SparkAvroReader::new)` 改为 `.createResolvingReader(SparkPlannedAvroReader::create)`。`createResolvingReader` 是 Avro `ReadBuilder` 的新 API，接收 `Function<org.apache.iceberg.Schema, DatumReader<?>>`（只接收 Iceberg schema），与 `SparkPlannedAvroReader.create(Schema, Map)` 的签名匹配。

## 小结

- **成效**：把 Spark 3.5 的 Avro 读取路径从依赖 Avro `ResolvingDecoder` 的旧 `SparkAvroReader` 迁移到基于 Iceberg 字段 ID 解析的 `SparkPlannedAvroReader`（planned reads 模式）。新 reader 通过 `AvroWithPartnerVisitor` 按 Iceberg 类型遍历 Avro 文件 schema，用 `buildReadPlan` 生成只读必要字段的读取计划，提升 schema 演进场景下的正确性与读取效率。旧 reader 标记废弃（1.8.0 移除）。同时暴露了 core 的 `FieldIDAccessors` 和 `buildReadPlan` 供引擎模块复用。
- **影响范围**：`core`（2 个文件的可见性/签名修改）、`spark/v3.5`（1 个新类、1 个修改、1 个切换、3 个测试更新）。影响 Spark 3.5 读取 Iceberg Avro 数据文件（manifest、metadata）的路径。功能等价但解析机制不同，理论上对用户透明。
- **回迁到 1.4.x 的注意事项**：
  1. 需确认 1.4.x 分支上 core 的 `AvroWithPartnerVisitor`、`ValueReaders.PlannedStructReader`、`ValueReaders.buildReadPlan` 是否已存在。这些是 planned reads 基础设施，若 1.4.x 尚未引入，需先回迁相关基础提交。
  2. `createResolvingReader` 是 `Avro.ReadBuilder` 的新 API，需确认 1.4.x 上已合入该 API（可能在更早的提交中引入）。
  3. `SparkPlannedAvroReader` 是纯新增类，回迁安全。`SparkAvroReader` 的 `@Deprecated` 标记是信息性的，回迁后旧 reader 仍可用作回退。
  4. 测试用 `createResolvingReader(SparkPlannedAvroReader::create)`，需确认 1.4.x 的 `Avro.ReadBuilder` 支持该方法。
  5. 此提交是 Spark 3.5 专用的，若 1.4.x 还支持 Spark 3.4，需确认是否要同步回迁到 Spark 3.4（参见提交 1270/1269 等可能的 Spark 3.4 对应版本）。
