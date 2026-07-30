# 提交 1567：Avro: Add writers for the internal object model (#11919)

## 提交信息

- **序号**：1567
- **哈希**：a100e6a2d193e35ce84a5c2ce91d365aa10cf2ea
- **短哈希**：a100e6a2d
- **日期**：2025-01-11（Sat Jan 11 00:09:56 2025 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Avro: Add writers for the internal object model (#11919)
- **PR/Issue**：#11919

## 总体目的

Iceberg 在内存中有两套对象模型：
- **Generic 模型**：基于 Avro 的 `GenericData.Record`/`GenericData.Fixed`/`Utf8` 等 Avro 原生类型。
- **Internal 模型**：基于 Iceberg 自己定义的 `StructLike` 接口与 `Type.TypeID#javaClass()` 指定的 Java 类型（如 `ByteBuffer` 表示 fixed/binary、`UUID` 表示 uuid、`String` 表示 string 等）。

`InternalReader` 早已存在，可把 Avro 文件读成 internal 模型对象，但一直没有对应的 `InternalWriter`——internal 模型可以读 Avro，却不能写 Avro。这是模型对称性的缺失，也阻碍了 internal 模型在写入路径上的使用（例如某些引擎希望直接以 `StructLike` 写出，避免与 Avro Generic 类耦合）。

本提交补齐这一缺口：新增 `InternalWriter`，把 internal 模型对象（`StructLike`、`ByteBuffer`、`UUID` 等）写入 Avro 文件；并抽出公共基类 `BaseWriteBuilder` 消除 `GenericAvroWriter` 与 `InternalWriter` 之间的 schema 访问重复逻辑。配套补齐测试基础设施（`RandomInternalData`、`InternalTestHelpers`、`TestInternalAvro`），并把 `RandomAvroData` 中的 list/map 生成代码下沉到 `api` 模块的 `RandomUtil` 以便跨模型复用。

## 如何达成设计目的

1. **抽出公共 schema 访问基类** `BaseWriteBuilder extends AvroSchemaVisitor<ValueWriter<?>>`：把 `record`/`union`/`array`/`map`/`primitive` 这些与 Avro schema 结构相关、与具体对象模型无关的访问逻辑统一到基类；只有"如何写 record"和"如何写 fixed"两类与对象模型相关的差异作为抽象方法 `createRecordWriter(List<ValueWriter<?>>)` 与 `fixedWriter(int length)` 留给子类。
2. **GenericAvroWriter 改造**：内部 `WriteBuilder` 改为继承 `BaseWriteBuilder`，只实现两个工厂方法（`createRecordWriter` → `ValueWriters.record`，`fixedWriter` → `ValueWriters.genericFixed`），保持原有行为。
3. **新增 `InternalWriter`**：结构与 `GenericAvroWriter` 对称（`MetricsAwareDatumWriter<T>`，持有 `ValueWriter<T>`，`setSchema` 时通过 `WriteBuilder` 构造）。其 `WriteBuilder` 同样继承 `BaseWriteBuilder`，但 `createRecordWriter` → `ValueWriters.struct`（写 `StructLike`），`fixedWriter` → `ValueWriters.fixedBuffers`（写 `ByteBuffer`）。
4. **`ValueWriters` 新增两个 writer**：
   - `struct(List<ValueWriter<?>>)` → `StructLikeWriter` 继承 `StructWriter<StructLike>`，通过 `struct.get(pos, Object.class)` 取字段值。
   - `fixedBuffers(int length)` → `FixedByteBufferWriter`，把 `ByteBuffer` 写为 Avro fixed（校验 `bytes.remaining() == length`）。
5. **`InternalReader` 微调**：删除 `FIXED` 分支下的 `return ValueReaders.fixed(primitive);` 这一行，让其 fall-through 到 `BYTES` 的 `ValueReaders.byteBuffers()`，与 internal 模型用 `ByteBuffer` 表示 fixed 类型保持一致（修复 reader 侧对称性，否则 fixed 列读取会得到 `GenericFixed` 而非 `ByteBuffer`）。
6. **测试基础设施**：
   - `RandomUtil`（api 测试模块）新增 `generateList`/`generateMap`（从 `RandomAvroData` 抽取）。
   - `RandomAvroData` 改为调用 `RandomUtil`，消除重复。
   - 新增 `RandomInternalData`：用 `TypeUtil.CustomOrderSchemaVisitor` 生成 `StructLike`（基于 `GenericRecord`），`FIXED`/`BINARY` 转 `ByteBuffer`、`UUID` 转 `UUID`，匹配 internal model 的 Java 类型。
   - 新增 `InternalTestHelpers`：递归按 Iceberg 类型比较 `StructLike`/`List`/`Map`。
   - 新增 `TestInternalAvro extends AvroDataTest`：覆盖所有 schema 的写-读回环，用 `InternalWriter` 写、`InternalReader` 读，`InternalTestHelpers.assertEquals` 比较。

### 修改详情

#### `api/src/test/java/org/apache/iceberg/util/RandomUtil.java`
**修改目的**：把 list/map 随机生成逻辑下沉到 api 模块供两套模型复用。

**关键变更**：新增 `generateList(Random, Types.ListType, Supplier<Object>)` 与 `generateMap(Random, Types.MapType, Supplier<Object>, Supplier<Object>)`，逻辑与原 `RandomAvroData` 中相同（5% 概率返回 null、key 去重、string key 转 `toString`）。

#### `core/src/main/java/org/apache/iceberg/avro/BaseWriteBuilder.java`（新增 116 行）
**修改目的**：抽公共 schema 访问基类。

**关键内容**：
- `record` → 委托 `createRecordWriter(fields)`（抽象）。
- `union` → 校验为 2 元 option union，按 NULL 位置选 `ValueWriters.option`。
- `array` → 若是 `LogicalMap`（Avro map-as-array）走 `arrayMap`，否则 `array`。
- `map` → `ValueWriters.map(strings(), valueWriter)`。
- `primitive` → 按 logical type（date/time-micros/timestamp-micros/decimal/uuid）和 primitive type（null/boolean/int/long/float/double/string/fixed/bytes）分派；`fixed` 委托抽象 `fixedWriter(primitive.getFixedSize())`。

#### `core/src/main/java/org/apache/iceberg/avro/GenericAvroWriter.java`
**修改目的**：内联 `WriteBuilder` 改为继承 `BaseWriteBuilder`，仅保留 Generic 特有差异。

**关键变更**：删除原 `record`/`union`/`array`/`map`/`primitive` 五个方法（移到基类），仅保留 `createRecordWriter` → `ValueWriters.record(fields)` 与 `fixedWriter` → `ValueWriters.genericFixed(length)`。文件净减少约 70 行。

#### `core/src/main/java/org/apache/iceberg/avro/InternalReader.java`
**修改目的**：让 fixed 类型读取走 `byteBuffers` 以匹配 internal 模型。

**关键变更**：`FIXED` case 下删除 `return ValueReaders.fixed(primitive);`（让控制流落到下一个 case 之前的 `byteBuffers()`——实际是删除该 return 后 `FIXED` 与 `BYTES` 共享 `return ValueReaders.byteBuffers();`）。

#### `core/src/main/java/org/apache/iceberg/avro/InternalWriter.java`（新增 74 行）
**修改目的**：internal 模型的 Avro 写入器。

**关键内容**：`public class InternalWriter<T> implements MetricsAwareDatumWriter<T>`，提供 `static <D> InternalWriter<D> create(Schema schema)` 工厂；`setSchema` 通过 `AvroSchemaVisitor.visit(schema, new WriteBuilder())` 构造 `ValueWriter<T>`；`write` 与 `metrics` 委托给 `ValueWriter`。内部 `WriteBuilder extends BaseWriteBuilder`，`createRecordWriter` → `ValueWriters.struct(fields)`，`fixedWriter` → `ValueWriters.fixedBuffers(length)`。

#### `core/src/main/java/org/apache/iceberg/avro/ValueWriters.java`
**修改目的**：新增 internal 模型需要的 writer。

**关键变更**：
- 新增 `public static ValueWriter<ByteBuffer> fixedBuffers(int length)` → 内部类 `FixedByteBufferWriter`，校验长度后 `encoder.writeBytes(bytes)`。
- 新增 `public static ValueWriter<StructLike> struct(List<ValueWriter<?>> writers)` → 内部类 `StructLikeWriter extends StructWriter<StructLike>`，实现 `get(struct, pos)` 调 `struct.get(pos, Object.class)`。

#### `core/src/test/java/org/apache/iceberg/InternalTestHelpers.java`（新增 110 行）
**修改目的**：测试期 deep-equal 工具，按 Iceberg 类型递归比较 `StructLike`/`List`/`Map` 与原始值。

#### `core/src/test/java/org/apache/iceberg/RandomInternalData.java`（新增 104 行）
**修改目的**：测试期随机生成 `StructLike` 数据。

**关键内容**：`RandomDataGenerator extends TypeUtil.CustomOrderSchemaVisitor<Object>`，`primitive` 把 `byte[]` 包装为 `ByteBuffer.wrap(...)`（FIXED/BINARY）、把 `byte[]` 转 `UUID.nameUUIDFromBytes(...)`（UUID），其余直接用 `RandomUtil.generatePrimitive`；`list`/`map` 委托给 `RandomUtil.generateList/generateMap`。

#### `core/src/test/java/org/apache/iceberg/avro/RandomAvroData.java`
**修改目的**：去重，复用 `RandomUtil`。

**关键变更**：`list` 与 `map` 方法体替换为 `return RandomUtil.generateList(random, list, elementResult);` 与 `return RandomUtil.generateMap(random, map, keyResult, valueResult);`，删除本地实现的 50 行重复代码。

#### `core/src/test/java/org/apache/iceberg/avro/TestInternalAvro.java`（新增 65 行）
**修改目的**：internal 模型 Avro 写-读回环测试。

**关键内容**：继承 `AvroDataTest`（参数化覆盖多种 schema）；`writeAndValidate` 用 `RandomInternalData.generate` 生成 100 条 `StructLike`，`Avro.writeData(...).createWriterFunc(InternalWriter::create)` 写入，`Avro.read(...).createResolvingReader(InternalReader::create)` 读回，`InternalTestHelpers.assertEquals` 逐条比较。

## 小结

- **成效**：补齐 internal object model 的 Avro 写入能力，使读写对称；抽出公共 `BaseWriteBuilder` 消除 `GenericAvroWriter` 与 `InternalWriter` 间的重复；测试基础设施（`RandomInternalData`/`InternalTestHelpers`/`TestInternalAvro`）让 internal 模型具备与 Generic 模型对等的覆盖。同时修正 `InternalReader` 对 fixed 类型的不一致行为。
- **影响范围**：`core` 模块的 Avro 写入路径新增 `InternalWriter`、`BaseWriteBuilder`、`ValueWriters` 两个新 writer；测试基础设施变更涉及 `api` 与 `core` 测试模块。无公共 API 破坏（`InternalWriter` 是新增）。
- **回迁到 1.4.x 的注意事项**：这是新增功能（补齐 internal model 写入器），并非 bug 修复。1.4.x 作为维护分支通常不引入新功能；但若 1.4.x 已存在 `InternalReader` 而缺 `InternalWriter`，且 1.4.x 上的某些 bug 修复依赖该对称性，可单独评估。**通常无需回迁**；如确需 internal model 写入支持，可考虑升级到包含本 PR 的版本。
