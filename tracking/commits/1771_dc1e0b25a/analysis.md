# 提交 1771：API: Move Variant interfaces and serialized implementations to API (#12374)

## 提交信息

- **序号**：1771 / 4088
- **哈希**：dc1e0b25ad1be42a431f4991381b16bf572a459e
- **短哈希**：dc1e0b25a
- **日期**：2025-02-21 15:12:59 -0800
- **作者**：Ryan Blue
- **提交说明**：API: Move Variant interfaces and serialized implementations to API (#12374)
- **PR/Issue**：#12374

## 总体目的

这个提交将 Variant（变体类型）相关的接口和序列化实现从 `core` 模块迁移到 `api` 模块。Variant 是 Iceberg 表格式中支持的一种半结构化数据类型（类似于 JSON 的灵活数据类型），包含元数据（metadata）和值（value）两部分。此前，Variant 的接口（如 `Variant`、`VariantValue`、`VariantMetadata`、`VariantObject`、`VariantArray`、`VariantPrimitive`）和序列化实现（如 `SerializedArray`、`SerializedObject`、`SerializedMetadata`、`SerializedPrimitive`、`SerializedShortString`）以及工具类（`VariantUtil`）都位于 `core` 模块中。

将这些类型迁移到 `api` 模块的动机是：`api` 模块是 Iceberg 最基础的模块，被所有其他模块（core、spark、flink 等）依赖。将 Variant 类型放在 `api` 中使得 Spark、Flink 等引擎模块可以直接依赖 Variant 接口，而无需引入整个 `core` 模块。同时，将序列化实现也一并迁移，使 Variant 的完整功能在 API 层即可使用。

此外，本次迁移还伴随着若干重构：提取了 `Serialized` 接口和 `SerializedValue` 接口为独立类型，提取了 `BasicType` 枚举，将工厂方法添加到公共接口上（`Variant.from()`、`VariantMetadata.from()`、`VariantValue.from()`），并调整了元数据缓冲区的格式。

## 如何达成设计目的

提交通过以下多层次修改来达成目标：

1. **文件迁移**：将 15 个 Variant 相关的源文件和 6 个测试文件从 `core` 模块重命名/移动到 `api` 模块的对应包路径下（`org.apache.iceberg.variants`）。

2. **接口重构**：将原先嵌套在 `Variants` 类中的 `Serialized` 接口、`SerializedValue` 抽象类、`BasicType` 枚举和 `HEADER_SIZE` 常量提取为独立的 API 层类型，消除对 `Variants` 外部类的依赖。

3. **公共 API 扩展**：在 `Variant`、`VariantMetadata`、`VariantValue` 等公共接口上添加静态工厂方法（`from()`、`of()`），使这些类型可以自包含地创建实例，不再依赖 `Variants` 工具类。

4. **依赖解耦**：将 `SerializedObject` 中对 core 模块 `Pair` 类的依赖替换为 JDK 自带的 `Map.Entry`，使 API 模块不依赖 core。

5. **类型系统更新**：将 `Type.TypeID.VARIANT` 的 Java 类型从 `Object.class` 改为 `Variant.class`，使类型系统正确关联 Variant 类型。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Type.java`（修改, +2/-1 lines）

**修改目的**：将 VARIANT 类型的 Java 类型关联到 Variant 接口。

**工作逻辑**：
- 添加 `import org.apache.iceberg.variants.Variant;`。
- 将 `VARIANT(Object.class)` 修改为 `VARIANT(Variant.class)`，使 Type 系统中 VARIANT 类型对应的 Java 类从通用的 Object 改为具体的 Variant 接口。

### `api/src/main/java/org/apache/iceberg/variants/Variant.java`（新增, +53 lines）

**修改目的**：在 API 层定义公共的 Variant 接口，包含元数据和值的配对及工厂方法。

**工作逻辑**：
- 定义 `public interface Variant`，包含 `metadata()` 和 `value()` 两个方法。
- 提供 `static Variant of(VariantMetadata metadata, VariantValue value)` 工厂方法，通过匿名内部类创建 Variant 实例。
- 提供 `static Variant from(ByteBuffer buffer)` 工厂方法，从序列化字节缓冲区解析出 metadata 和 value，构造完整的 Variant 对象。该方法先解析 metadata，再根据 metadata 大小切片出 value 部分。

### `api/src/main/java/org/apache/iceberg/variants/Serialized.java`（新增, 由原 Variant.java 改造, +9/-4 lines）

**修改目的**：定义序列化类型的接口，从原 `Variants.Serialized` 提取为独立接口。

**工作逻辑**：
- 原 `core` 中的 `Variant.java` 接口被改造为 `Serialized` 接口，定义 `ByteBuffer buffer()` 方法，用于获取序列化字节缓冲区。

### `api/src/main/java/org/apache/iceberg/variants/SerializedValue.java`（新增, +35 lines）

**修改目的**：定义序列化值的基础接口，从原 `Variants.SerializedValue` 提取。

**工作逻辑**：
- 定义 `interface SerializedValue extends VariantValue, Serialized`，提供 `sizeInBytes()` 和 `writeTo(ByteBuffer, int)` 的默认实现，基于 `buffer()` 方法。

### `api/src/main/java/org/apache/iceberg/variants/BasicType.java`（新增, +26 lines）

**修改目的**：定义 Variant 的基本类型枚举，从原 `Variants.BasicType` 提取。

**工作逻辑**：
- 定义 `enum BasicType`，包含 `PRIMITIVE`、`SHORT_STRING`、`OBJECT`、`ARRAY` 四种基本类型。

### `api/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java`（迁移+修改, +19/-7 lines）

**修改目的**：迁移到 API 层并调整元数据解析逻辑。

**工作逻辑**：
- 改为实现 `Serialized` 接口（而非 `Variants.Serialized`），添加独立的 `HEADER_SIZE = 1` 常量。
- `EMPTY_V1_BUFFER` 从 2 字节改为 3 字节（增加一个 0x00 字节），与元数据格式更新保持一致。
- 构造函数中新增了对元数据缓冲区结尾的处理：计算 endOffset，如果 endOffset 小于缓冲区 limit，则切片到 endOffset，确保只使用有效部分。

### `api/src/main/java/org/apache/iceberg/variants/SerializedObject.java`（迁移+修改, +28/-22 lines）

**修改目的**：迁移到 API 层，移除对 core 模块 Pair 的依赖。

**工作逻辑**：
- 改为实现 `VariantObject, SerializedValue` 接口（而非继承 `Variants.SerializedValue`），添加独立的 `HEADER_SIZE` 常量。
- `fields()` 方法返回类型从 `Iterable<Pair<String, Integer>>` 改为 `Iterable<Map.Entry<String, Integer>>`，使用 JDK 自带的 `Map.entry()` 代替 core 的 `Pair.of()`，消除对 core 模块的依赖。
- 移除了 `sizeInBytes()` 方法（现在由 `SerializedValue` 默认实现提供）。
- 工厂调用从 `Variants.value(...)` 改为 `VariantValue.from(...)`。

### `api/src/main/java/org/apache/iceberg/variants/SerializedArray.java`（迁移+修改, +14/-10 lines）

**修改目的**：迁移到 API 层，更新类型引用。

**工作逻辑**：
- 改为实现 `VariantArray, SerializedValue` 接口，添加独立的 `HEADER_SIZE` 常量。
- `BasicType` 引用从 `Variants.BasicType` 改为直接的 `BasicType`。
- 值创建从 `Variants.value(...)` 改为 `VariantValue.from(...)`。

### `api/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java`（迁移+修改, +8/-6 lines）

**修改目的**：迁移到 API 层，更新类型引用。

**工作逻辑**：
- 改为实现 `VariantPrimitive<Object>, SerializedValue` 接口。
- `PRIMITIVE_OFFSET` 从 `Variants.HEADER_SIZE` 改为直接使用 `1`。
- `BasicType` 引用更新为直接引用。

### `api/src/main/java/org/apache/iceberg/variants/SerializedShortString.java`（迁移+修改, +10/-6 lines）

**修改目的**：迁移到 API 层，更新类型引用。

**工作逻辑**：
- 改为实现 `VariantPrimitive<String>, SerializedValue` 接口，添加独立的 `HEADER_SIZE` 常量。
- `BasicType` 引用更新，字符串读取的起始位置使用本地 `HEADER_SIZE`。

### `api/src/main/java/org/apache/iceberg/variants/VariantMetadata.java`（迁移+修改, +4/-0 lines）

**修改目的**：迁移到 API 层，添加公共工厂方法。

**工作逻辑**：
- 新增 `static VariantMetadata from(ByteBuffer buffer)` 工厂方法，委托给 `SerializedMetadata.from(buffer)`，使公共接口可自包含创建实例。

### `api/src/main/java/org/apache/iceberg/variants/VariantValue.java`（迁移+修改, +17/-0 lines）

**修改目的**：迁移到 API 层，添加公共工厂方法。

**工作逻辑**：
- 新增 `static VariantValue from(VariantMetadata metadata, ByteBuffer value)` 工厂方法，根据 header 字节判断 BasicType，分别创建对应的 Serialized 实现类。该逻辑从 `Variants.value()` 方法迁移而来。

### `api/src/main/java/org/apache/iceberg/variants/VariantUtil.java`（迁移+修改, +10/-10 lines）

**修改目的**：迁移到 API 层，更新 BasicType 引用。

**工作逻辑**：
- `basicType()` 方法的返回类型从 `Variants.BasicType` 改为 `BasicType`，内部 switch 的返回值也更新为直接引用 `BasicType` 枚举值。

### `api/src/main/java/org/apache/iceberg/variants/VariantArray.java`、`VariantObject.java`、`VariantPrimitive.java`、`LogicalType.java`、`PhysicalType.java`、`Primitives.java`（迁移, 无内容变更）

**修改目的**：从 core 迁移到 API，保持接口/类定义不变。

### `core/src/main/java/org/apache/iceberg/variants/Variants.java`（修改, +2/-39 lines）

**修改目的**：移除已迁移到 API 层的内部类型定义和工厂逻辑。

**工作逻辑**：
- 移除了 `Serialized` 接口、`SerializedValue` 抽象类、`HEADER_SIZE` 常量和 `BasicType` 枚举（已迁移到 API 层独立类型）。
- `value()` 方法简化为委托给 `VariantValue.from(metadata, value)`。
- 保留了 `emptyMetadata()`、`object()` 等仍需在 core 中的方法。

### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java`（修改, +7/-4 lines）

**修改目的**：更新对 SerializedObject.fields() 返回类型的引用。

**工作逻辑**：
- 将 `Pair<String, Integer>` 遍历改为 `Map.Entry<String, Integer>`，对应 SerializedObject.fields() 的返回类型变更。
- `field.first()` 改为 `field.getKey()`，`field.second()` 改为 `field.getValue()`。

### 测试文件迁移与修改

**`TestSerializedMetadata.java`**（迁移+修改）：更新测试中的字节数组以匹配新的元数据格式（3 字节而非 2 字节），补充了偏移量末尾的字节。

**`TestSerializedObject.java`**（迁移+修改）：将 `Variants.metadata(meta)` 改为 `VariantMetadata.from(meta)`，将 `Variants.of(...)` 改为 `VariantTestUtil.createString(...)`，类型声明从 `VariantPrimitive<String>` 改为 `SerializedPrimitive`。

**`VariantTestUtil.java`**（迁移+修改）：新增 `variantBuffer()` 和 `variant()` 辅助方法，`createObject()` 重载接受 `VariantMetadata` 参数，`createArray()` 参数类型从 `Variants.Serialized` 改为 `Serialized`。

**`TestSerializedArray.java`、`TestSerializedPrimitives.java`、`TestVariantUtil.java`**（迁移, 无内容变更）。

## 小结

- **成效**：成功将 Variant 接口和序列化实现从 core 模块迁移到 api 模块，使 Variant 类型成为公共 API 的一部分。Spark、Flink 等引擎模块现在可以直接依赖 api 模块使用 Variant 类型，无需引入 core。同时通过重构消除了对 core 模块工具类（Pair）的依赖，使 api 模块自包含。
- **影响范围**：涉及 api 和 core 两个模块的 variants 包，共 27 个文件。这是一次较大的模块间代码迁移，影响所有使用 Variant 类型的模块。变更包括公共接口扩展（新增工厂方法）、内部类型提取、元数据格式微调和依赖解耦。
- **回迁到 1.4.x 的注意事项**：需谨慎评估。此提交是 Variant 类型架构的重要调整，涉及大量文件迁移和接口变更。回迁前需确认 1.4.x 分支是否已有 Variant 类型的初始实现（Variant 是较新引入的类型）。如果 1.4.x 已有 core 模块中的 Variant 实现，则可回迁此重构。需注意元数据格式变更（EMPTY_V1_BUFFER 从 2 字节改为 3 字节）可能影响已有测试数据。此外需确保 1.4.x 的 api 模块不依赖 core 的 Pair 类。建议整体回迁而非部分回迁，以保持一致性。
