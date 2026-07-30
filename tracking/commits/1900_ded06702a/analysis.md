# 提交 1900：Parquet: Implement Variant metrics (#12496)

## 提交信息

- **序号**：1900 / 4088
- **哈希**：ded06702a8c3bd6bd823969ab82d2e32669e04c5
- **短哈希**：ded06702a
- **日期**：2025-03-21 13:03:31 -0700
- **作者**：Ryan Blue
- **提交说明**：Parquet: Implement Variant metrics (#12496)
- **PR/Issue**：#12496

## 总体目的

这个提交为 Iceberg 的 Variant 类型实现了 Parquet 文件级别的指标（metrics）收集。Variant 是 Iceberg 引入的一种灵活数据类型（类似 JSON 的半结构化数据），用于存储可变结构的数据。与其他类型一样，Variant 列也需要收集文件级指标（如最小值、最大值、null 计数等），以支持查询引擎在文件级别进行数据跳过（data skipping）优化。

此前，Iceberg 的 Parquet 指标收集逻辑（`ParquetUtil`）不支持 Variant 类型。当 Parquet 文件包含 Variant 列时，无法收集有意义的统计信息，导致查询引擎无法对 Variant 列进行文件级过滤。本提交通过将指标收集逻辑从 `ParquetUtil` 重构到新的 `ParquetMetrics` 类，并新增 `ParquetVariantUtil` 来处理 Variant 类型的指标计算，实现了 Variant 指标收集功能。

Variant 指标收集的复杂性在于 Variant 是一种二进制序列化格式，需要解析 Variant 的内部结构来提取有意义的统计信息（如字段路径的最小/最大值）。

## 如何达成设计目的

整体设计思路是将 Parquet 指标收集逻辑重构为独立模块，并新增 Variant 专用的指标计算工具：

1. **`ParquetMetrics`（新类，639 行）**：从 `ParquetUtil` 中提取的指标收集逻辑，重构为独立类。负责从 Parquet 文件的 BlockMetaData 和 ColumnChunkMetaData 中收集行数、列大小、值计数、上下界等指标。

2. **`ParquetVariantUtil`（新类，476 行）**：专门处理 Variant 类型的指标计算。将 Parquet 原始类型转换为 Variant 物理类型，并提供 Variant 值的比较器，用于计算 min/max 边界值。

3. **`Metrics.java`**：新增只接收 rowCount 的构造函数。

4. **`ParquetUtil`**：大量代码被移出到 `ParquetMetrics`，简化为委托调用。

5. **API 层**：在 variants 包中新增多个辅助方法（`SerializedObject`、`VariantObject`、`VariantPrimitive` 等接口新增方法），以及 `Variants` 工具类新增 Variant 值比较和指标收集方法。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetrics.java` (新增, +639 lines)

**修改目的**：从 ParquetUtil 中提取的 Parquet 指标收集逻辑。

**工作逻辑**：`ParquetMetrics` 类负责从 Parquet 文件的元数据中收集列级指标。核心方法 `metrics()` 接收 Schema、MessageType、ParquetMetadata 和 MetricsConfig，遍历所有列，根据指标模式（full/truncate/none）收集行数、列大小、值计数、null 计数、NaN 计数、上下界等信息。对 Variant 类型列，使用 `ParquetVariantUtil` 计算专用指标。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantUtil.java` (新增, +476 lines)

**修改目的**：Variant 类型的 Parquet 指标计算工具。

**工作逻辑**：

1. `convert(PrimitiveType)` 方法：将 Parquet 原始类型映射到 Variant 的 `PhysicalType`，通过访问者模式处理各种逻辑类型注解（String、Decimal、Date、Time、Timestamp、UUID 等）。

2. 提供 Variant 值的比较器，用于计算 min/max 边界值。Variant 是二进制格式，需要解析其内部结构（metadata + value）来进行有意义的比较。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetUtil.java` (修改, +239/-511 lines)

**修改目的**：将指标收集逻辑移出到 ParquetMetrics，简化 ParquetUtil。

**工作逻辑**：大量指标收集代码被移出，`ParquetUtil` 的指标相关方法改为委托 `ParquetMetrics` 处理。

### `api/src/main/java/org/apache/iceberg/Metrics.java` (修改, +4 lines)

**修改目的**：新增只接收 rowCount 的构造函数。

### `api/src/main/java/org/apache/iceberg/variants/` 多个文件 (修改)

**修改目的**：为 Variant 类型添加指标收集所需的接口方法。

**工作逻辑**：在 `SerializedObject`、`SerializedPrimitive`、`SerializedShortString`、`VariantObject`、`VariantPrimitive`、`VariantUtil` 等接口/类中新增方法，支持 Variant 值的比较和指标提取。

### `core/src/main/java/org/apache/iceberg/FieldMetrics.java` (修改, +12 lines)

**修改目的**：为 FieldMetrics 添加 Variant 支持。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantMetrics.java` (新增, +503 lines)

**修改目的**：新增 Variant 指标的全面测试。

### 其他文件

包括 `OrcMetrics`（ORC 侧适配）、`ParquetConversions`、`BaseParquetReaders`、`BaseParquetWriter`、`ParquetWriteAdapter`、`ParquetWriter`、`PruneColumns`、`TypeWithSchemaVisitor` 等文件的适配性修改。

## 总结

本提交为 Iceberg 的 Variant 类型实现了 Parquet 文件级指标收集功能。通过新增 `ParquetMetrics` 和 `ParquetVariantUtil` 两个核心类，以及重构 `ParquetUtil`，使查询引擎能对 Variant 列进行文件级数据跳过优化。这是一个大型功能提交（约 2000 行新增代码），涉及 API、core、parquet、orc 多个模块的协调修改。
