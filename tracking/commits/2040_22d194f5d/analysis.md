# 提交 2040：Parquet: Add variant array reader in Parquet

## 提交信息

- **序号**：2040 / 4088
- **哈希**：22d194f5d685fdf5bec17c6bcc92a69db4ae4957
- **短哈希**：22d194f5d
- **日期**：2025-04-25 16:17:10 -0700
- **作者**：Aihua Xu
- **提交说明**：Parquet: Add variant array reader in Parquet (#12512)
- **PR/Issue**：#12512

## 总体目的

Iceberg 的 Variant 类型支持 Parquet shredding（将 Variant 的嵌套字段拆解为 Parquet 原生列以提升查询性能）。此前 shredding 已支持对象（Object）类型的字段拆解，但对于数组（Array）类型的 Variant 字段，`VariantReaderBuilder.array()` 方法直接抛出 `UnsupportedOperationException("Array is not yet supported")`，即数组类型的 shred 字段无法被读取。

本提交为 Parquet 添加 Variant 数组的读取支持，使得当 Variant 类型中包含数组字段并被 shred 到 Parquet 列时，读取器能够正确解析数组元素。同时新增了 `ValueArray` 类作为可构建的 Variant 数组实现，支持将多个 Variant 值组装成数组并序列化为 Variant 二进制格式。

## 如何达成设计目的

整体设计分为两部分：

**1. Core 层 - 新增 `ValueArray` 类**：
- 实现 `VariantArray` 接口，提供可变的数组构建能力
- 内部使用 `List<VariantValue>` 存储元素，通过 `add()` 方法添加元素
- 使用 `SerializationState` 内部类缓存序列化计算结果（总数据大小、偏移量大小等），避免重复计算
- 支持将数组序列化为 Variant 二进制格式：header + 元素数量 + 偏移量列表 + 数据
- 在 `Variants` 工厂类中新增 `array()` 静态方法创建 `ValueArray` 实例

**2. Parquet 层 - 数组读取器**：
- 在 `ParquetVariantReaders` 中新增 `ArrayReader` 内部类和 `array()` 工厂方法
- `ArrayReader` 通过 definition level 和 repetition level 控制 Parquet 重复列的读取
- 在 `VariantReaderBuilder.array()` 中实现数组 shred 字段的读取逻辑：解析 value/typed_value 路径的 definition level，以及 typed_value.list 路径的 repetition level，构建 `ArrayReader`
- 在 `ParquetVariantVisitor` 中新增 `LIST` 常量用于路径构建

**关键协作关系**：
`VariantReaderBuilder` 解析 Parquet schema 结构 => 计算 definition/repetition levels => 创建 `ArrayReader` => `ArrayReader` 使用元素读取器逐个读取数组元素 => 组装为 `ValueArray` => 通过 `ParquetVariantReaders.shredded()` 与未 shred 的 value 合并

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/ValueArray.java` (新增, +130 lines)

**修改目的**：提供可构建的 Variant 数组实现。

**工作逻辑**：
`ValueArray` 实现 `VariantArray` 接口：
- **元素管理**：内部 `List<VariantValue> elements` 存储数组元素，`add()` 方法添加元素并清除缓存
- **序列化状态**：`SerializationState` 内部类在首次需要时计算并缓存：元素数量、是否大数组（>255 个元素需要 4 字节偏移量）、数据总大小、偏移量大小
- `sizeInBytes()`：1(header) + 元素数量大小 + (1+元素数)*偏移量大小 + 数据总大小
- `writeTo()`：写入 header（含 isLarge 和 offsetSize 标志）+ 元素数量 + 各元素偏移量列表 + 各元素数据。偏移量列表有 N+1 个条目（N 个元素 + 1 个最终大小），每个条目使用 offsetSize 字节的小端无符号整数

### `core/src/main/java/org/apache/iceberg/variants/Variants.java` (修改, +4 lines)

**修改目的**：提供创建 ValueArray 的工厂方法。

**工作逻辑**：
新增 `public static ValueArray array()` 方法，返回 `new ValueArray()`，使用户可以方便地创建可变的 Variant 数组。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantReaders.java` (修改, +61 lines)

**修改目的**：提供 Parquet 数组读取器。

**工作逻辑**：
- 新增工厂方法 `array(int repeatedDefinitionLevel, int repeatedRepetitionLevel, ParquetValueReader<?> elementReader)`，创建 `ArrayReader`
- 新增 `ArrayReader` 内部类实现 `VariantValueReader`：
  - 持有 definition level、repetition level、元素读取器和 TripleIterator
  - `read()` 方法：创建 `ValueArray`，通过 do-while 循环读取重复列：当当前 definition level > 本数组 definition level 时读取元素（null 元素转为 Variant null），否则消费空列表 triple 并退出循环。循环条件为当前 repetition level > 本数组 repetition level（同一数组内的重复元素）
  - `setPageSource()` 委托给元素读取器
  - `column()` 和 `columns()` 委托给元素读取器

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantVisitor.java` (修改, +1 line)

**修改目的**：定义数组元素列名常量。

**工作逻辑**：
新增 `static final String LIST = "list"` 常量，用于构建 Parquet schema 中数组元素列的路径。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantReaderBuilder.java` (修改, +12/-4 lines)

**修改目的**：实现数组 shred 字段的读取逻辑。

**工作逻辑**：
- `path()` 方法从 `path(String name)` 改为 `path(String... names)`，支持构建多段路径
- `array()` 方法从抛出 `UnsupportedOperationException` 改为实际实现：
  - 计算 value 路径的 definition level（valueReader 存在时）
  - 计算 typed_value 路径的 definition level
  - 计算 typed_value.list 路径的 definition level 和 repetition level（数组元素的重复级别）
  - 使用 `ParquetVariantReaders.array()` 创建元素读取器
  - 通过 `ParquetVariantReaders.shredded()` 将 shred 的 typed 读取器与未 shred 的 value 读取器合并

### 测试文件 (修改)

**修改目的**：覆盖数组读取和 ValueArray 序列化。

- `TestValueArray.java`（新增 +166 行）：ValueArray 的单元测试
- `TestShreddedObject.java`（修改 -65/+0 行）：重构现有测试以适配 ValueArray
- `TestVariantReaders.java`（修改 +498 行）：新增大量数组 shred 读取的集成测试

## 总结

本提交为 Iceberg 的 Parquet Variant shredding 添加了数组类型的读取支持。新增了 `ValueArray` 类作为可构建的 Variant 数组实现（支持序列化为 Variant 二进制格式），并在 Parquet 读取器中实现了 `ArrayReader` 来处理 shred 后的数组列。`VariantReaderBuilder.array()` 从抛出异常改为实际解析 Parquet schema 的 definition/repetition levels 并构建读取器，使得 Variant 中的数组字段可以被正确 shred 和读取。属于 Variant 类型支持的功能扩展。
