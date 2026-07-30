# 提交 1884：ORC: Support timestamp(9), variant, and unknown in generics (#12567)

## 提交信息

- **序号**：1884 / 4088
- **哈希**：608345b35197f38292eed8ae2635bd036f56eb6b
- **短哈希**：608345b35
- **日期**：2025-03-19 11:40:55 -0700
- **作者**：Ryan Blue
- **提交说明**：ORC: Support timestamp(9), variant, and unknown in generics (#12567)
- **PR/Issue**：#12567

## 总体目的

本提交为 Iceberg 的 ORC 模块添加对三种 Iceberg 类型的支持：timestamp(9)（纳秒精度时间戳）、Variant（变体半结构化类型）和 Unknown（未知类型，用于 name mapping 等场景）。

背景：Iceberg 正在扩展类型系统以支持纳秒级时间戳（`TimestampNanoType`）和 Variant 类型。ORC 作为支持的文件格式之一，需要在 schema 转换、读写器、schema visitor 等各环节支持这些新类型。此外，Unknown 类型（表示字段类型未知，常用于读取旧文件或 name mapping）需要被正确处理——在 ORC schema 中应跳过而非报错。

本提交覆盖了 ORC 模块的完整类型支持链路：schema 转换（Iceberg↔ORC）、schema visitor（OrcSchemaVisitor、OrcSchemaWithTypeVisitor）、读写器（GenericOrcReader/Writer、GenericOrcReaders/Writers）、以及各 visitor 实现（ApplyNameMapping、RemoveIds、HasIds、EstimateOrcAvgWidthVisitor、OrcToIcebergVisitor）。

## 如何达成设计目的

整体设计思路：

1. **Variant 的 ORC 表示**：由于 ORC 没有原生的 Variant 类型，使用一个带 `iceberg.struct-type=VARIANT` 属性的 struct 表示，struct 包含两个 binary 字段：`metadata` 和 `value`（与 Avro/Parquet 的表示一致）。

2. **TimestampNano 的 ORC 表示**：ORC 的 TIMESTAMP/TIMESTAMP_INSTANT 类本身不区分精度，通过新增 `iceberg.timestamp-unit` 属性（值为 `MICROS` 或 `NANOS`）来区分微秒和纳秒。读写器根据该属性或 Iceberg 类型选择正确的转换。

3. **Unknown 类型处理**：在 Iceberg→ORC 转换时返回 null（跳过该字段），在各 visitor 中对 null 做防御性处理。

4. **Visitor 扩展**：在 `OrcSchemaVisitor` 和 `OrcSchemaWithTypeVisitor` 中新增 `variant` 访问方法和 `visitVariant` 路由，当 struct 带有 `iceberg.struct-type=VARIANT` 属性时走 variant 路径。各具体 visitor（ApplyNameMapping、RemoveIds、HasIds、EstimateOrcAvgWidthVisitor、OrcToIcebergVisitor）实现 `variant` 方法。

5. **读写器实现**：GenericOrcReaders 新增 `VariantReader`（从 StructColumnVector 读取 metadata/value 两个 binary，构造 Variant）；GenericOrcWriters 新增 `VariantBinaryWriter` 抽象类、`VariantMetadataWriter`、`VariantValueWriter`、`VariantWriter`，以及 `TimestampTzNanoWriter`、`TimestampNanoWriter`。GenericOrcReader/Writer 的 visitor 重写 `variant` 方法返回对应读写器，`primitive` 方法对 null iPrimitive 返回 null（处理 Unknown）。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/ORCSchemaUtil.java` (修改, +57/-12 lines)

**修改目的**：扩展 Iceberg↔ORC schema 转换以支持 Variant、TimestampNano、Unknown。

**工作逻辑**：
- 新增常量：`ICEBERG_STRUCT_TYPE_ATTRIBUTE`（标识 ORC struct 对应的 Iceberg 类型）、`VARIANT`/`VARIANT_METADATA`/`VARIANT_VALUE`、`TIMESTAMP_UNIT`、`MICROS`/`NANOS`。
- `convert` 方法：
  - `UNKNOWN` case 返回 null（跳过字段）。
  - `TIMESTAMP` case 添加 `TIMESTAMP_UNIT=MICROS` 属性。
  - 新增 `TIMESTAMP_NANO` case，创建 TIMESTAMP/TIMESTAMP_INSTANT 并设置 `TIMESTAMP_UNIT=NANOS`。
  - 新增 `VARIANT` case，创建 struct 含 metadata/value 两个 binary 字段，设置 `ICEBERG_STRUCT_TYPE_ATTRIBUTE=VARIANT`。
- `convert` 调用处对 null 结果跳过 `addField` 和 `setAttribute`。
- `buildOrcProjection` 中对 `VARIANT` 同样处理；`LIST`/`MAP` 对 null 子类型抛出校验异常。
- `typeCompatibility` 新增 `TIMESTAMP_NANO` 分支。

### `orc/src/main/java/org/apache/iceberg/orc/OrcSchemaVisitor.java` (修改, +23/-2 lines)

**修改目的**：扩展 schema visitor 支持 variant 路由。

**工作逻辑**：
- `visit` 方法中 `STRUCT` case 检查 `ICEBERG_STRUCT_TYPE_ATTRIBUTE`，若为 `VARIANT` 走 `visitVariant`。
- 新增 `visitVariant` 方法：校验 struct 有且仅有 metadata/value 两个字段，递归访问后回调 `visitor.variant(variant, metadataResult, valueResult)`。
- 新增 `variant` 方法默认抛 `UnsupportedOperationException`。

### `orc/src/main/java/org/apache/iceberg/orc/OrcSchemaWithTypeVisitor.java` (修改, +25/-2 lines)

**修改目的**：扩展带 Iceberg 类型的 schema visitor 支持 variant。

**工作逻辑**：与 OrcSchemaVisitor 对称——`STRUCT` case 检查 variant 属性，新增 `visitVariant` 方法（将 iType 转为 VariantType），新增 `variant` 方法默认抛异常。

### `orc/src/main/java/org/apache/iceberg/orc/OrcToIcebergVisitor.java` (修改, +74/-46 lines)

**修改目的**：ORC→Iceberg schema 转换支持 variant 和 timestamp nano。

**工作逻辑**：
- 新增 `variant` 方法：从 ORC struct（带 VARIANT 属性）转换为 `Types.VariantType` 的 NestedField。
- `TIMESTAMP` case：读取 `TIMESTAMP_UNIT` 属性，MICROS 或 null → `TimestampType.withoutZone()`，NANOS → `TimestampNanoType.withoutZone()`。
- `TIMESTAMP_INSTANT` case：同上但 withZone/withZone。
- 将 LONG 和 BINARY 的类型转换逻辑提取为 `convertLong` 和 `convertBinary` 私有方法以简化 primitive 方法。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcReaders.java` (修改, +23/-0 lines)

**修改目的**：新增 Variant 读取器。

**工作逻辑**：新增 `variants()` 工厂和 `VariantReader` 类，`nonNullRead` 从 `StructColumnVector` 读取两个字段（metadata、value）的 binary，转小端后构造 `VariantMetadata` 和 `VariantValue`，返回 `Variant.of(...)`。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcWriters.java` (修改, +103/-0 lines)

**修改目的**：新增 Variant 和 TimestampNano 写入器。

**工作逻辑**：
- 新增 `timestampTzNanos()` 和 `timestampNanos()` 工厂，对应 `TimestampTzNanoWriter`（写 OffsetDateTime 的 millis + nano）和 `TimestampNanoWriter`（写 LocalDateTime，设 UTC）。
- 新增 `VariantBinaryWriter<T>` 抽象类：若值是 `Serialized` 直接写 buffer，否则分配小端 ByteBuffer 序列化（注：ORC 保留数组引用，不能复用缓冲区）。
- `VariantMetadataWriter`、`VariantValueWriter` 分别委托 `writeTo`。
- `VariantWriter` 组合两者，写入 `StructColumnVector` 的两个字段。
- 新增 `variants()` 工厂。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcReader.java` (修改, +13/-0 lines)

**修改目的**：reader 的 visitor 重写 variant 方法返回 `GenericOrcReaders.variants()`；primitive 方法对 null iPrimitive（Unknown）返回 null。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcWriter.java` (修改, +18/-0 lines)

**修改目的**：writer 的 visitor 重写 variant 方法返回 `GenericOrcWriters.variants()`；primitive 方法对 null iPrimitive 返回 null；新增 `TIMESTAMP_NANO` case 选择 nanos writer。

### `orc/src/main/java/org/apache/iceberg/orc/ApplyNameMapping.java` (修改, +7/-0 lines)

**修改目的**：name mapping visitor 支持 variant——克隆 variant type 并设置映射的 field id。

### `orc/src/main/java/org/apache/iceberg/orc/RemoveIds.java` (修改, +6/-0 lines)

**修改目的**：移除 Iceberg id 的 visitor 支持 variant——克隆并移除 Iceberg 属性。

### `orc/src/main/java/org/apache/iceberg/orc/HasIds.java` (修改, +5/-0 lines)

**修改目的**：检查是否有 Iceberg id 的 visitor 支持 variant——检查 variant 本身是否有 id。

### `orc/src/main/java/org/apache/iceberg/orc/EstimateOrcAvgWidthVisitor.java` (修改, +5/-0 lines)

**修改目的**：估算列宽的 visitor 支持 variant——返回固定值 128。

### `orc/src/main/java/org/apache/iceberg/orc/OrcValueReaders.java` (修改, +3/-1 lines)

**修改目的**：StructReader 中对 Unknown 类型字段填充 null 常量。

**工作逻辑**：在判断是否为 metadata 列的条件中增加 `|| field.type().typeId() == Type.TypeID.UNKNOWN`，使 Unknown 类型字段被当作常量 null 字段处理。

### `orc/src/test/java/org/apache/iceberg/orc/TestORCSchemaUtil.java` (修改, +13/-8 lines)

**修改目的**：测试 schema 新增 timestamp nano（带/不带时区）和 variant 字段；用 `Objects.equals` 简化 id 属性比较。

### `data/src/test/java/org/apache/iceberg/data/orc/TestGenericData.java` (修改, +14/-0 lines)

**修改目的**：启用 ORC 的 Variant、TimestampNanos、Unknown 测试。

**工作逻辑**：重写 `supportsVariant()`、`supportsTimestampNanos()`、`supportsUnknown()` 返回 true。

## 总结

本提交为 ORC 模块完整接入三种新类型：Variant（用带属性的 struct + 两个 binary 字段表示）、TimestampNano（用 `iceberg.timestamp-unit` 属性区分纳秒/微秒）、Unknown（转换时跳过）。覆盖了 schema 转换、两类 schema visitor、各具体 visitor 实现、读写器以及测试启用。这是 Iceberg 多格式新类型支持系列工作的一部分。
