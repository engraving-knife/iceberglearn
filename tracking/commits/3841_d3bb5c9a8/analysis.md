# 提交 3841：Data: Add comprehensive data type tests to Format Model TCK (#15795)

## 提交信息

- **序号**：3841 / 4088
- **哈希**：d3bb5c9a840b1be2013f6b40f01561a0b3e028e4
- **短哈希**：d3bb5c9a8
- **日期**：2026-06-08 13:50:19 +0200
- **作者**：Alex Stephen
- **提交说明**：Data: Add comprehensive data type tests to Format Model TCK (#15795)
- **PR/Issue**：#15795

## 总体目的

本提交为 Iceberg 的 Format Model TCK（Technology Compatibility Kit，技术兼容性套件）大幅扩展了数据类型测试覆盖。TCK 是 Iceberg 项目用于验证不同引擎（Spark、Flink 等）实现是否正确遵循 Iceberg 读写规范的核心测试集。在此之前，Format Model TCK 只覆盖了 `StructOfPrimitive` 和 `Decimals` 两种数据生成器，类型覆盖非常有限，无法发现各引擎在处理其他数据类型（如 UUID、Fixed、Binary、List、Map、TimestampNano 等）时的兼容性问题。

本提交通过引入大量新的数据生成器覆盖更多数据类型，同时为各引擎添加了"不支持类型"的声明机制，让测试能优雅地跳过引擎不支持的数据类型而非直接失败。这显著增强了 TCK 的发现能力，确保各引擎实现之间的互操作性。

## 如何达成设计目的

整体设计思路分三部分：

1. **扩展数据生成器**：在 `DataGenerators` 中新增 8 个数据生成器（Primitives、UUID、Fixed、Binary、ListOfPrimitive、MapOfPrimitive、TimestampNano 等），将 `ALL` 数组从 2 个扩展到 10 个，覆盖几乎全部 Iceberg 数据类型。

2. **不支持类型机制**：在 `BaseFormatModelTests` 中引入 `unsupportedTypeIds()` 抽象方法和 `filterUnsupported()` / `supportedSchema()` 辅助方法。各引擎子类可以声明其不支持的数据类型 ID 集合，测试框架在运行时会自动过滤掉包含这些类型的列。如果某生成器的 schema 全部列被过滤掉，则使用 `assumeThat` 跳过该测试。

3. **投影辅助方法**：引入统一的 `project()` 方法替换分散的 `copy()` 调用，用于将记录按目标 schema 投影。同时改进了 `assertBounds` 方法，使其能正确处理字符串类型的截断边界（使用包含性断言而非等值断言）。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+164/-56 lines)

**修改目的**：添加不支持类型过滤机制和投影辅助方法，改进边界断言逻辑。

**工作逻辑**：

1. 新增 `unsupportedTypeIds()` 方法（默认返回空集合），子类可重写声明不支持类型：
```java
protected Collection<Type.TypeID> unsupportedTypeIds() {
  return Set.of();
}
```

2. `filterUnsupported(Schema)` 使用 `TypeUtil.find()` 递归检查 schema 中是否包含不支持类型，过滤掉相关列。

3. `supportedSchema(DataGenerator)` 调用 `filterUnsupported`，若结果为空则用 `assumeThat` 跳过测试。

4. 统一 `project(List<Record>, Schema)` 方法替换分散的 `copy()` 调用，按目标 schema 创建新记录。

5. 各测试方法中将 `dataGenerator.schema()` 改为 `supportedSchema(dataGenerator)`，并将 `dataGenerator.generateRecords()` 改为 `project(dataGenerator.generateRecords(), schema)`。

6. `assertBounds` 方法改进：对 STRING 类型使用包含性断言（`<=` / `>=`），因为写入器可能截断字符串边界；使用 `InternalRecordWrapper` 和 `StructLike` 提高性能；新增空记录校验。

### `data/src/test/java/org/apache/iceberg/data/DataGenerators.java` (+169/-1 lines)

**修改目的**：新增多个数据生成器覆盖更多数据类型。

**工作逻辑**：
将 `ALL` 数组从 2 个生成器扩展到 10 个：
- `Primitives`：覆盖 String、Integer、Long、Float、Double、Boolean、Decimal、Date、Time、Timestamp、TimestampTz
- `UUID`：覆盖 UUID 类型
- `Fixed`：覆盖 Fixed(16) 类型
- `Binary`：覆盖 Binary 类型
- `StructOfPrimitive`（保留）：结构体类型
- `Decimals`（保留）：Decimal 类型
- `ListOfPrimitive`：覆盖 List 类型
- `MapOfPrimitive`：覆盖 Map 类型
- `TimestampNano`：覆盖纳秒精度时间戳类型

`DefaultSchema` 被从 `ALL` 中移除（被 `Primitives` 覆盖），但保留为独立生成器供其他测试使用。所有生成器都新增了 `toString()` 方法用于测试输出。

### `flink/v1.20/flink/src/test/java/.../TestFlinkFormatModel.java` (+11/-0 lines)

**修改目的**：声明 Flink 1.20 引擎不支持的数据类型。

**工作逻辑**：
```java
private static final Set<Type.TypeID> UNSUPPORTED_TYPE_IDS =
    Set.of(Type.TypeID.TIME, Type.TypeID.TIMESTAMP_NANO, Type.TypeID.VARIANT, Type.TypeID.UNKNOWN);
```

### `flink/v2.0/flink/src/test/java/.../TestFlinkFormatModel.java` (+11/-0 lines)

**修改目的**：同上，为 Flink 2.0 声明不支持类型（与 1.20 相同）。

### `flink/v2.1/flink/src/test/java/.../TestFlinkFormatModel.java` (+10/-0 lines)

**修改目的**：为 Flink 2.1 声明不支持类型。

**工作逻辑**：Flink 2.1 支持更多类型，不支持列表缩减为 `TIME` 和 `VARIANT`（不再包含 `TIMESTAMP_NANO` 和 `UNKNOWN`）。

### `spark/v3.5/spark/src/test/java/.../TestSparkFormatModel.java` (+14/-0 lines)

**修改目的**：声明 Spark 3.5 不支持的数据类型。

**工作逻辑**：
```java
private static final Set<Type.TypeID> UNSUPPORTED_TYPE_IDS =
    Set.of(Type.TypeID.TIME, Type.TypeID.TIMESTAMP_NANO, Type.TypeID.FIXED);
```
其中 FIXED 标注了 TODO 注释，待 TCK 修复后移除。

### `spark/v4.0/spark/src/test/java/.../TestSparkFormatModel.java` (+14/-0 lines)

**修改目的**：同上，为 Spark 4.0 声明不支持类型（与 3.5 相同）。

### `spark/v4.1/spark/src/test/java/.../TestSparkFormatModel.java` (+14/-0 lines)

**修改目的**：同上，为 Spark 4.1 声明不支持类型（与 3.5 相同）。

## 总结

本提交是 TCK 测试质量的重要提升，通过引入全面的类型覆盖和优雅的不支持类型处理机制，大幅增强了格式兼容性测试的发现能力。统一的投影辅助方法和改进的边界断言逻辑也提升了测试代码的可维护性和正确性。这对于保证 Iceberg 在不同引擎间互操作的可靠性具有重要意义。
