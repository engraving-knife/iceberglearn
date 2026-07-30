# 提交 2056：Parquet: Implement Variant array writes (#12847)

## 提交信息

- **序号**：2056 / 4088
- **哈希**：242717c8c516dcc49bc4368b77cd3f3af40720c8
- **短哈希**：242717c8c
- **日期**：2025-04-29 13:48:12 -0700
- **作者**：Aihua Xu
- **提交说明**：Parquet: Implement Variant array writes (#12847)
- **PR/Issue**：#12847

## 总体目的

Iceberg 正在引入 Variant 类型（半结构化数据，类似 JSON）。Variant 支持 shredding（拆分）——把 Variant 中具有统一类型的字段拆出到 Parquet 的强类型列，未匹配部分仍以原始字节形式存于 `value`/`metadata` 列。此前 Parquet shredding 已支持 primitive 与 object，但 array 类型未实现：`ParquetVariantUtil` 的 `array()` 直接返回 null（不 shred），`VariantWriterBuilder.array()` 抛出 `UnsupportedOperationException("Array is not yet supported")`，导致带 array 的 Variant 写入直接失败。

本提交实现 Variant array 的 Parquet 写入与 shredding：能根据数组元素的多数类型选出 shred 类型，构建 3-level list schema，并由 `ArrayWriter` 按 Parquet 重复语义写出每个元素；空数组写为 null。同时增强测试工具与随机生成器以覆盖 array 场景。

## 如何达成设计目的

整体设计沿用现有 shredding 框架的访问者模式：
1. **Schema 构造（`ParquetVariantUtil.array`）**：对 array 的每个元素递归求 shredding 类型，过滤 null 后选出现次数最多的类型作为 shred 类型，构建 `typed_value` 下 `element` 字段的 3-level list schema。元素结果为空时返回 null（不 shred）。
2. **Writer 构造（`VariantWriterBuilder.array`）**：根据 schema 计算各定义/重复级别，调用 `ParquetVariantWriters.array(...)` 创建 `ArrayWriter`，再用 `ParquetVariantWriters.shredded(...)` 包装成 shredded writer（同时保留未匹配时的 `value` 列写入）。
3. **`ArrayWriter` 实现**：实现 `TypedWriter`，`write(parentRepetition, value)` 中把 `VariantArray` 的每个元素用元素 writer 写一次，第一个元素用 `parentRepetition` 作为 rl、其余元素用 `repetitionLevel` 作为 rl，以符合 Parquet list 的重复语义；空数组写为 null。
4. **辅助改进**：`VariantArray` 接口与 `SerializedArray`/`ValueArray` 增加 `toString()`；`VariantTestUtil.createArray` 改为接受 `VariantValue...` 并使用 `writeTo` 而非 buffer；`RandomVariants` 真正生成随机 array（原先 ARRAY 分支会落入 OBJECT）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/VariantArray.java` (修改, +19/-0 lines)

**修改目的**：为数组提供统一的字符串表示工具。

**工作逻辑**：
新增静态方法 `asString(VariantArray arr)`，遍历数组元素用 `arr.get(i)` 取值并拼成 `VariantArray([e0, e1, ...])` 字符串，便于调试与测试断言。

### `api/src/main/java/org/apache/iceberg/variants/SerializedArray.java` (修改, +5/-0 lines)

**修改目的**：为序列化数组实现提供 `toString()`。

**工作逻辑**：
新增 `toString()` 委托给 `VariantArray.asString(this)`。

### `core/src/main/java/org/apache/iceberg/variants/ValueArray.java` (修改, +5/-0 lines)

**修改目的**：为基于值的数组实现提供 `toString()`。

**工作逻辑**：
同上，新增 `toString()` 委托 `VariantArray.asString(this)`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantUtil.java` (修改, +26/-1 lines)

**修改目的**：实现 array 节点的 shredding schema 构造。

**工作逻辑**：
`array(VariantArray array, List<Type> elementResults)`：
- 元素结果为空时返回 null。
- 否则过滤非 null 的元素类型，按类型分组计数，取出现次数最多的类型作为 `shredType`（`max(Map.Entry.comparingByValue())`），若无则回退到第一个元素类型。
- 调用 `list(shredType)` 构建一个 `Types.optionalList().element(field("element", shreddedType)).named("typed_value")` 的 3-level list GroupType。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantWriterBuilder.java` (修改, +9/-1 lines)

**修改目的**：实现 array 节点的 writer 构造。

**工作逻辑**：
`array(GroupType array, ParquetValueWriter<?> valueWriter, ParquetValueWriter<?> elementWriter)`：原先抛 `UnsupportedOperationException`，现改为：
- 计算 `valueDL`（VALUE 路径定义级）、`typedDL`（TYPED_VALUE 路径定义级）、`repeatedDL`/`repeatedRL`（TYPED_VALUE/LIST 路径的重复级别）。
- 通过 `ParquetVariantWriters.array(repeatedDL, repeatedRL, elementWriter)` 创建 `typedWriter`。
- 通过 `ParquetVariantWriters.shredded(valueDL, valueWriter, typedDL, typedWriter)` 包装为 shredded writer 返回。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantWriters.java` (修改, +61/-0 lines)

**修改目的**：实现 array 的实际写入逻辑。

**工作逻辑**：
- 新增静态工厂 `array(repeatedDefinitionLevel, repeatedRepetitionLevel, elementWriter)`，返回 `new ArrayWriter(...)`（cast elementWriter 为 `ParquetValueWriter<VariantValue>`）。
- 新增私有类 `ArrayWriter implements TypedWriter`：持有 definitionLevel/repetitionLevel/writer/children。`types()` 返回 `Set.of(PhysicalType.ARRAY)`。
- `write(parentRepetition, value)`：把 `value.asArray()` 取出，若 `numElements() == 0` 调用 `writeNull(writer, parentRepetition, definitionLevel)`；否则遍历元素，第一个元素用 `parentRepetition` 作为 rl，其余元素用 `repetitionLevel` 作为 rl，调用 `writer.write(rl, element)`。这样符合 Parquet 重复级别语义——同一数组的后续元素 rl 为 repetitionLevel 触发新一行。
- `columns()` 返回 writer.columns()；`setColumnStore` 委托给 writer。

### `api/src/test/java/org/apache/iceberg/variants/VariantTestUtil.java` (修改, +5/-4 lines)

**修改目的**：让测试工具能直接基于 `VariantValue` 构造数组缓冲。

**工作逻辑**：
`createArray` 由 `Serialized...` 入参改为 `VariantValue...`，使用 `value.sizeInBytes()` 计算大小、`value.writeTo(buffer, offset)` 写入值，替代原来基于 `Serialized.buffer()` 的实现。可见性从包级改为 `public`。

### `core/src/test/java/org/apache/iceberg/RandomVariants.java` (修改, +7/-1 lines)

**修改目的**：随机生成真实 Variant array 而非回退到 object。

**工作逻辑**：
`ARRAY` 分支不再 fallthrough 到 OBJECT，改为：`Variants.array()` 创建 `ValueArray`，随机 0..9 个元素，每个调用 `randomVariant(random, metadata, randomType(random))` 添加。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantWriters.java` (修改, +60/-2 lines)

**修改目的**：为 Variant array 写入添加测试覆盖。

**工作逻辑**：
- 新增多个测试 buffer 与 `VariantArray` 常量：空数组、纯字符串数组、混合类型数组、嵌套数组、混合嵌套数组、数组内含 object、混合 object 数组，以及 object 内含数组。
- 把这些 Variant 加入 `VARIANTS` 数组，被参数化测试覆盖写入与读回校验。
- 新增辅助方法 `array(VariantValue...)` 构造 `ValueArray`。

## 总结

本提交实现 Variant array 在 Parquet 中的 shredding 写入能力：在 schema 构造侧选多数类型作为 shred 类型构建 3-level list，在 writer 侧通过 `ArrayWriter` 按 Parquet 重复级别写出元素并处理空数组，在 builder 侧把原先的 `UnsupportedOperationException` 替换为真实的 shredded writer 构造。配套改进测试工具与随机生成器，并加入丰富的 array 测试用例（空、嵌套、混合类型、含 object 等）。
