# 提交 2453：API, Core: Preserve original Type for upper/lower bounds in Metrics (#13695)

## 提交信息

- **序号**：2453 / 4088
- **哈希**：32f469b985ee8b886d02df9f37c27bcd2a861a14
- **短哈希**：32f469b985
- **日期**：2025-08-05 08:14:46 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Preserve original Type for upper/lower bounds in Metrics (#13695)
- **PR/Issue**：#13695

## 总体目的

该提交在 Iceberg 的文件指标（Metrics）系统中引入了一个新的 `originalTypes` 字段，用于保留上下界（upper/lower bounds）值的原始类型信息。

在 Iceberg 中，文件的 Metrics 包含每个字段的上下界统计信息，这些边界值以 `ByteBuffer` 的形式存储。然而，`ByteBuffer` 本身不携带类型信息——例如，一个 `ByteBuffer` 可能包含的是 Integer、Long、Double 或其他类型的二进制表示。当某些类型的值在序列化/反序列化或格式转换过程中发生类型转换时（例如某些 Parquet/ORC 的类型映射），原始类型信息会丢失，这可能导致后续在读取这些边界值进行谓词下推（predicate pushdown）时产生错误的解释。

该提交通过在 Metrics 中额外存储 `Map<Integer, Type> originalTypes`（字段 ID 到原始类型的映射），确保即使边界值的 `ByteBuffer` 表示发生了转换，消费方仍能通过 `originalTypes` 获知原始类型，从而正确地解析和解释边界值。

## 如何达成设计目的

整体设计思路如下：

1. **API 层扩展 Metrics 类**：在 `Metrics` 类中添加 `originalTypes` 字段（`Map<Integer, Type>`），并添加新的构造函数和访问方法 `originalTypes()`。该字段标记为不随其他字段一起序列化（注释说明 "this is not serialized with all the other fields"），意味着它是一个运行时辅助字段。

2. **Core 层扩展 FieldMetrics 类**：在 `FieldMetrics<T>` 中添加 `originalType` 字段和对应的构造函数重载，使字段级别的指标也能携带原始类型信息。`DoubleFieldMetrics` 和 `FloatFieldMetrics` 分别在构造时传入 `Types.DoubleType.get()` 和 `Types.FloatType.get()`。

3. **传递类型信息**：在 `DataFiles` 和 `FileMetadata` 的 Builder 中，从 Metrics 读取 `originalTypes()` 并在构建新 Metrics 时传递。`MetricsUtil` 的过滤方法也相应地传递 `originalTypes`。

4. **格式适配**：Parquet 和 ORC 的指标收集器在构建 Metrics 时传入原始类型信息。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Metrics.java` (+36/-3 lines)

**修改目的**：在 API 层的 Metrics 类中添加 originalTypes 字段和相关方法。

**工作逻辑**：
- 新增 `private Map<Integer, Type> originalTypes` 字段
- 新增一个 8 参数的构造函数，接受 `Map<Integer, Type> originalTypes`
- 原有的 7 参数构造函数委托给新构造函数，传入 `null`
- 原有的 5 参数构造函数也委托给新构造函数
- 新增 `Map<Integer, Type> originalTypes()` 访问方法
- 导入 `org.apache.iceberg.types.Type`

### `core/src/main/java/org/apache/iceberg/FieldMetrics.java` (+27/-4 lines)

**修改目的**：在字段级别指标类中添加 originalType 字段。

**工作逻辑**：
- 新增 `private final Type originalType` 字段
- 新增两个构造函数重载：一个接受 `Type originalType` 参数（6 参数版本），一个接受完整的 7 参数版本
- 原有构造函数委托给新版本，传入 `null` 作为 originalType
- 新增 `public Type originalType()` 访问方法

### `core/src/main/java/org/apache/iceberg/DoubleFieldMetrics.java` (+3/-1 lines)

**修改目的**：Double 字段指标在构造时传入 DoubleType。

**工作逻辑**：私有构造函数调用改为 `super(id, valueCount, 0L, nanValueCount, lowerBound, upperBound, Types.DoubleType.get())`。

### `core/src/main/java/org/apache/iceberg/FloatFieldMetrics.java` (+3/-1 lines)

**修改目的**：Float 字段指标在构造时传入 FloatType。

**工作逻辑**：私有构造函数调用改为 `super(id, valueCount, 0L, nanValueCount, lowerBound, upperBound, Types.FloatType.get())`。

### `core/src/main/java/org/apache/iceberg/DataFiles.java` (+5/-1 lines)

**修改目的**：在 DataFiles.Builder 中传递 originalTypes。

**工作逻辑**：
- Builder 中新增 `private Map<Integer, Type> originalTypes` 字段
- `copyFrom(Metrics metrics)` 方法中添加 `this.originalTypes = metrics.originalTypes()`
- 构建 Metrics 时传入 `originalTypes` 参数

### `core/src/main/java/org/apache/iceberg/FileMetadata.java` (+5/-1 lines)

**修改目的**：在 FileMetadata.Builder 中传递 originalTypes。

**工作逻辑**：与 DataFiles 类似的修改模式——新增字段、从 Metrics 复制、构建时传入。

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+4/-2 lines)

**修改目的**：在指标过滤工具方法中传递 originalTypes。

**工作逻辑**：两个 `copyWithoutKeys` 相关方法在构建新 Metrics 时传入 `metrics.originalTypes()` 或 `copyWithoutKeys(metrics.originalTypes(), excludedFieldIds)`。

### `core/src/main/java/org/apache/iceberg/FileGenerationUtil.java` (+8/-3 lines)

**修改目的**：文件生成工具中传递原始类型信息。

### `api/src/test/java/org/apache/iceberg/TestMetricsSerialization.java` (+10/-2 lines)

**修改目的**：更新指标序列化测试以验证 originalTypes 的行为。

### 其他修改文件

- `core/src/main/java/org/apache/iceberg/data/orc/GenericOrcWriters.java` (+4/-2 lines)：ORC 写入器适配
- `core/src/main/java/org/apache/iceberg/orc/OrcMetrics.java` (+5/-1 lines)：ORC 指标收集适配
- `core/src/main/java/org/apache/iceberg/parquet/ParquetMetrics.java` (+12/-4 lines)：Parquet 指标收集适配
- `core/src/main/java/org/apache/iceberg/parquet/ParquetValueWriters.java` (+2/-1 lines)：Parquet 值写入器适配
- `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+3/-1 lines)：更新测试
- `core/src/main/java/org/apache/iceberg/parquet/TestVariantMetrics.java` (+24/-6 lines)：Variant 指标测试

## 总结

该提交在 Iceberg 的 Metrics 系统中引入了原始类型（originalType）信息的保存机制。通过在文件级指标（Metrics）和字段级指标（FieldMetrics）中新增类型字段，并在数据文件构建、指标过滤、Parquet/ORC 格式收集器等环节传递该信息，确保了上下界值的原始类型在后续使用中不会丢失。这对于正确解释边界值进行谓词下推等优化至关重要，特别是在涉及类型转换的场景下。该修改涉及 API 和 Core 两个模块，共 15 个文件，是一个较为系统性的增强。
