# 提交 2275：Arrow: Refactor APIs for Parquet Reading with V2 Encodings (#13290)

## 提交信息

- **序号**：2275 / 4088
- **哈希**：4213a5014efabc32a161d70cdd1ecd8148ff2e69
- **短哈希**：4213a5014
- **日期**：2025-06-25 14:19:42 -0500
- **作者**：Eric Maynard
- **提交说明**：Arrow: Refactor APIs for Parquet Reading with V2 Encodings (#13290)
- **PR/Issue**：#13290

## 总体目的

本提交重构了 Arrow 向量化 Parquet 读取器的值读取器 API，引入新的 `VectorizedValuesReader` 接口和 `VectorizedPlainValuesReader` 实现类，替代原先直接使用 `ValuesAsBytesReader` 的做法。这是为支持 Parquet V2 编码（如 RLE/Bit-Packed Hybrid 等）向量化读取所做的架构准备。

此前，`VectorizedPageIterator` 和 `VectorizedParquetDefinitionLevelReader` 直接依赖具体的 `ValuesAsBytesReader` 类来读取 PLAIN 编码的值。这种紧耦合限制了扩展性——若要支持其他编码的向量化读取，无法在不修改所有调用点的情况下替换读取器实现。通过抽象出 `VectorizedValuesReader` 接口，解耦了值读取器与具体实现，使得未来可以新增支持 V2 编码的向量化读取器实现，而调用方代码保持不变。

重构后，`VectorizedPlainValuesReader` 继承 `ValuesAsBytesReader` 并实现 `VectorizedValuesReader` 接口，将原有的 PLAIN 编码读取逻辑封装在该类中。所有原先直接调用 `ValuesAsBytesReader` 方法（如 `getBuffer`、`readBooleanAsInt`）的调用点统一改为通过接口方法（如 `readBinary`、`readBoolean`）调用，使接口更清晰、语义更明确。

## 如何达成设计目的

- 新增 `VectorizedValuesReader` 接口，定义向量化值读取器的统一契约，包含单值读取方法（`readBoolean`、`readInteger`、`readLong` 等）、批量读取方法（`readIntegers`、`readLongs` 等）和 `readBinary`、`initFromPage` 方法。该接口设计上与 Parquet 的 `ValuesReader` 抽象类协作（注释说明实现类应为 `ValuesReader` 实例）。
- 新增 `VectorizedPlainValuesReader` 类，继承 `ValuesAsBytesReader` 并实现 `VectorizedValuesReader` 接口，封装 PLAIN 编码的向量化读取逻辑。
- `VectorizedPageIterator` 中将 `plainValuesReader` 字段类型从 `ValuesAsBytesReader` 改为 `VectorizedValuesReader`，在 `initDataReader` 中根据编码类型选择具体实现（PLAIN 编码创建 `VectorizedPlainValuesReader`）。
- `VectorizedParquetDefinitionLevelReader` 中所有接受 `ValuesAsBytesReader` 参数的方法签名改为 `VectorizedValuesReader`，并调整对应的读取方法调用（`getBuffer` → `readBinary().toByteBuffer()`，`readBooleanAsInt` → `readBoolean() ? 1 : 0`）。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedValuesReader.java` (新增, +79/0 lines)

**修改目的**：定义向量化值读取器的统一接口，为支持多种编码的向量化读取提供抽象层。

**工作逻辑**：该接口声明了向量化读取所需的所有方法：单值读取（`readBoolean`、`readByte`、`readShort`、`readInteger`、`readLong`、`readFloat`、`readDouble`、`readBinary`）、批量读取（`readIntegers`、`readLongs`、`readFloats`、`readDoubles`，直接将值读入 Arrow `FieldVector`）以及 `initFromPage` 生命周期方法。接口注释说明实现类应为 Parquet `ValuesReader` 的子类，该接口通过组合方式"扩展"了 `ValuesReader` 的能力。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPlainValuesReader.java` (新增, +82/0 lines)

**修改目的**：提供 PLAIN 编码的向量化值读取器实现。

**工作逻辑**：继承 `ValuesAsBytesReader`（保留原有基于字节的读取能力），实现 `VectorizedValuesReader` 接口。`readBinary(int len)` 方法从底层 `getBuffer(len)` 获取 ByteBuffer，优先使用其 backing array（零拷贝）构造 `Binary`，否则拷贝到新数组。批量读取方法 `readIntegers/readLongs/readFloats/readDoubles` 通过私有 `readValues` 方法统一处理，按 typeWidth 从底层获取连续字节块并 `setBytes` 到 Arrow 向量的 data buffer。`readByte`/`readShort` 复用 `readInteger` 并强制转型。定义了 INT_SIZE/LONG_SIZE/FLOAT_SIZE/DOUBLE_SIZE 常量。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPageIterator.java` (+19/-20 lines)

**修改目的**：将值读取器字段类型从具体类改为接口，并根据编码类型实例化具体实现。

**工作逻辑**：
- 字段 `plainValuesReader`（`ValuesAsBytesReader`）改名为 `valuesReader`（`VectorizedValuesReader`），`reset()` 中置 null 不变。
- `initDataReader` 中逻辑反转：原代码在非字典编码时仅允许 PLAIN 编码（`if (dataEncoding != Encoding.PLAIN)` 抛异常），否则创建 `ValuesAsBytesReader`。新代码改为：若 `dataEncoding == Encoding.PLAIN` 则创建 `VectorizedPlainValuesReader`，否则抛出 `UnsupportedOperationException`。初始化 `initFromPage` 调用增加了 IOException 捕获并包装为 `ParquetDecodingException`。
- `previousReader` 的类型转换从直接赋值改为 `(ValuesReader) valuesReader` 强制转换（因为接口不直接继承 `ValuesReader`）。
- `setPreviousReader` 调用和所有 `nextBatch` 调用中的 `plainValuesReader` 改为 `valuesReader`（10 处 reader 类型的方法调用）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java` (+22/-21 lines)

**修改目的**：将所有值读取器参数类型从 `ValuesAsBytesReader` 改为 `VectorizedValuesReader`，并适配新的接口方法。

**工作逻辑**：
- 所有 `nextBatch`、`nextVal`、`setNextNValuesInVector` 等方法的 `ValuesAsBytesReader valuesReader` 参数统一改为 `VectorizedValuesReader valuesReader`（涉及 NumericBaseReader 的 LongReader/DoubleReader/FloatReader/IntegerReader、TimestampMillisReader、TimestampInt96Reader、FixedSizeBinaryReader、VarWidthReader、BooleanReader、DictionaryReader 等多个内部类）。
- 方法调用适配：`valuesReader.getBuffer(len)` 改为 `valuesReader.readBinary(len).toByteBuffer()`（TimestampInt96Reader、FixedSizeBinaryReader、VarWidthReader、setNextNValuesInVector）；`valuesReader.readBooleanAsInt()` 改为 `valuesReader.readBoolean() ? 1 : 0`（BooleanReader）。`readLong`、`readDouble`、`readFloat`、`readInteger` 等方法签名在接口中一致，无需改动调用。

## 总结

本提交通过引入 `VectorizedValuesReader` 接口和 `VectorizedPlainValuesReader` 实现类，对 Arrow 向量化 Parquet 读取器进行了面向接口的重构。这解耦了读取器调用方与具体实现，为后续支持 Parquet V2 编码的向量化读取奠定了架构基础。重构涉及约 208 行新增代码，保持了功能等价性，是一次为未来扩展做准备的架构性改动。
