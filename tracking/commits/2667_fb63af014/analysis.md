# 提交 2667：Parquet, Data, Spark: Fix variant type filtering in ParquetMetricsRowGroupFilter (#14081)

## 提交信息

- **序号**：2667 / 4088
- **哈希**：fb63af014c39f687f3016a1b3c8bde4a494e9a4a
- **短哈希**：fb63af014
- **日期**：2025-09-20 12:09:10 -0600
- **作者**：Drew Gallardo
- **提交说明**：Parquet, Data, Spark: Fix variant type filtering in ParquetMetricsRowGroupFilter (#14081)
- **PR/Issue**：#14081

## 总体目的

本提交修复了 `ParquetMetricsRowGroupFilter` 在处理 Variant 类型字段时的过滤逻辑缺陷。Variant 类型是 Iceberg 新引入的一种数据类型（用于存储半结构化数据，类似 JSON）。问题在于，当对 Variant 类型字段执行 `notNull` 过滤时，过滤器没有正确处理 Variant 类型，可能导致错误的行组跳过决策。

修复前的逻辑仅检查字段是否为 `Type.NestedType`（嵌套类型），如果是则跳过指标过滤（返回 ROWS_MIGHT_MATCH），将过滤推迟到扫描后（post-scan）评估。但 Variant 类型不是 NestedType，因此 `notNull` 过滤会尝试基于 Parquet 的列统计信息（如 null 计数）来做行组跳过决策。然而，Variant 类型作为复杂类型，其指标过滤不应在行组级别下推——即使所有值看起来都是 null，也应推迟到扫描后评估，因为 Variant 的统计信息可能不可靠。

修复后的逻辑将 Variant 类型与嵌套类型同等对待，对 Variant 类型字段的 `notNull` 过滤也返回 `ROWS_MIGHT_MATCH`，确保所有 Variant 类型的过滤都在扫描后评估。

此外，本提交还大幅重构了测试代码：将 Parquet 测试数据生成从 Avro GenericRecord 迁移到 Iceberg GenericRecord，新增了 Variant 类型字段的过滤测试，并在 Spark 4.0 中添加了端到端的 Variant 过滤测试。

## 如何达成设计目的

通过以下修改完成修复：

1. **核心修复**：在 `ParquetMetricsRowGroupFilter` 的 `notNull` 评估逻辑中，增加对 `type.isVariantType()` 的检查，使 Variant 类型与 NestedType 一样跳过指标过滤。
2. **测试重构**：将 `TestMetricsRowGroupFilter` 中的测试数据生成从 Avro `GenericRecordBuilder` 改为 Iceberg `GenericRecord`，提取通用的 `writeParquetFile` 方法。
3. **新增 Variant 测试**：在 `TestMetricsRowGroupFilter` 中新增两个测试验证 Variant 字段的 `notNull` 过滤行为。
4. **Spark 端到端测试**：在 `TestFilterPushDown` 中新增 `testVariantExtractFiltering` 测试，验证 Spark 4.0 中 Variant 字段的各种过滤操作。
5. **测试辅助**：在 `SparkTestHelperBase` 中添加 `VariantVal` 的比较逻辑。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetricsRowGroupFilter.java` (+6/-3 lines)

**修改目的**：修复 Variant 类型字段的 notNull 过滤逻辑。

**工作逻辑**：在 `notNull` 评估方法中，将原来的 `if (schema.findType(id) instanceof Type.NestedType)` 改为先获取 `Type type = schema.findType(id)`，然后检查 `if (type instanceof Type.NestedType || type.isVariantType())`。这使 Variant 类型字段与嵌套类型一样，直接返回 `ROWS_MIGHT_MATCH`，不基于列统计信息做行组跳过决策，将过滤推迟到扫描后评估。注释也相应更新，说明嵌套类型和 Variant 类型的 notNull 过滤都是隐式过滤，不在 Parquet 层下推。

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java` (+147/-41 lines)

**修改目的**：重构测试数据生成并新增 Variant 类型过滤测试。

**工作逻辑**：
- **导入变更**：移除 Avro 相关导入（`AvroSchemaUtil`、`GenericRecordBuilder`、`Record`），新增 Iceberg `GenericParquetWriter`、`Variant` 相关类导入。
- **新增 VARIANT_SCHEMA**：定义包含 `id`（int）和 `variant_field`（Variant 类型）的测试 schema。
- **重构 `createParquetInputFile`**：将 Avro `GenericRecordBuilder` 改为 Iceberg `GenericRecord.create()`，使用 `setField` 代替 `set`，提取通用的 `writeParquetFile` 方法。
- **新增 `writeParquetFile` 方法**：通用的 Parquet 文件写入方法，使用 `GenericParquetWriter` 作为写入器。
- **新增 `testVariantFieldMixedValuesNotNull`**：创建包含混合 Variant 值（部分为 null，部分非 null）的 Parquet 文件，验证 `notNull` 过滤返回 `true`（应读取），因为 Variant 过滤必须在扫描后评估。
- **新增 `testVariantFieldAllNullsNotNull`**：创建所有 Variant 值均为 null 的 Parquet 文件，验证即使全部为 null，`notNull` 过滤仍返回 `true`（应读取），因为 Variant 的 notNull 过滤不能基于行组统计信息跳过。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/SparkTestHelperBase.java` (+8/-0 lines)

**修改目的**：添加 VariantVal 类型的比较逻辑。

**工作逻辑**：在 `assertEquals` 方法中新增对 `VariantVal` 类型的处理分支。由于 Spark 的 `VariantVal` 基于原始 `byte[]` 比较，可能因尾部 null 字节导致比较失败，因此改为比较其 JSON 字符串表示（`toString()`）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java` (+89/-2 lines)

**修改目的**：新增 Spark 4.0 Variant 字段过滤的端到端测试。

**工作逻辑**：
- **新增 `testVariantExtractFiltering`**：创建包含 VARIANT 列的 Iceberg 表（format-version=3），插入包含 JSON 数据的行，验证多种过滤条件：
  - `try_variant_get(data, '$.num', 'int') IS NOT NULL`：验证非 null 过滤下推为 `data IS NOT NULL`
  - `IS NULL`：验证 null 过滤不下推
  - `> 30`：验证大于过滤下推为 `data IS NOT NULL`
  - `= 30`：验证等于过滤下推为 `data IS NOT NULL`
  - `IN (25, 35)`：验证 IN 过滤不下推
  - `!= 25`：验证不等于过滤下推为 `data IS NOT NULL`
- **修改 `checkFilters`**：将 post-scan filter 的断言从 `contains("Filter (" + sparkFilter + ")")` 改为 `containsAnyOf("Filter (" + sparkFilter + ")", "Filter " + sparkFilter)`，以兼容 Spark 不同版本生成的计划格式（带括号或不带括号）。
- **新增 `toSparkVariantRow`**：辅助方法，使用 Iceberg 的 Variants API 构建 Variant 对象并序列化为 Spark 的 `VariantVal`（metadata 和 value 两个 byte 数组）。

## 总结

本提交修复了 `ParquetMetricsRowGroupFilter` 中 Variant 类型字段 notNull 过滤的缺陷，使 Variant 类型与嵌套类型一样跳过行组级别的指标过滤，推迟到扫描后评估。核心修改仅一行条件判断的扩展（增加 `type.isVariantType()` 检查），但配套的测试工作较为充分——重构了测试数据生成方式以支持 Variant 类型，新增了 Data 模块和 Spark 4.0 的端到端测试，覆盖了各种 Variant 过滤场景。这确保了 Variant 类型字段的过滤行为正确且一致。
