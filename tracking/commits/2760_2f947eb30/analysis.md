# 提交 2760：Parquet: Treat VARIANT like nested for eq/in in ParquetMetricsRowGroupFilter (#14279)

## 提交信息

- **序号**：2760 / 4088
- **哈希**：2f947eb30222cbd4a52074322a54531c95368ffa
- **短哈希**：2f947eb30
- **日期**：2025-10-17 17:03:55 -0700
- **作者**：Huaxin Gao
- **提交说明**：Parquet: Treat VARIANT like nested for eq/in in ParquetMetricsRowGroupFilter (#14279)
- **PR/Issue**：#14279

## 总体目的

本提交修复了 `ParquetMetricsRowGroupFilter` 中对 Variant 类型列应用 `eq`（等于）和 `in`（包含）谓词时的过滤行为，使 Variant 类型与嵌套类型（nested type）一样，不被下推到行组级别过滤，而是留到扫描后（post scan）再评估。

背景在于：Iceberg 的 v3 格式引入了 Variant 类型（半结构化数据）。在 Parquet 中，Variant 存储为一种特殊类型。`ParquetMetricsRowGroupFilter` 用于基于行组的统计信息（min/max）来决定是否可以跳过整个行组。对于嵌套类型（如 struct、list、map），由于 Parquet 的行组统计信息无法有效表示这些复杂类型的边界，过滤器在 `eq` 和 `in` 谓词中直接返回 `ROWS_MIGHT_MATCH`（即不跳过），将过滤留到扫描后。

此前，Variant 类型未被纳入这一处理。当用户对 Variant 列使用 `equal` 或 `in` 谓词时，过滤器会尝试基于 Variant 的统计信息进行匹配，但 Variant 的二进制表示（metadata + value 的拼接）使得基于 min/max 的比较没有意义，甚至可能错误地跳过本应读取的行组，导致查询结果不正确。

本提交与 2666（Variant 在 Parquet 过滤中的处理）以及 2761（Variant 在 bounds 中的字节缓冲转换修复）属于同一 Variant 类型支持系列。本提交聚焦于 `eq`/`in` 谓词的行组过滤行为。

## 如何达成设计目的

修改方案简洁：在 `ParquetMetricsRowGroupFilter` 的 `eq` 和 `in` 两个内部方法中，将判断条件从"`schema.findType(id) instanceof Type.NestedType`"扩展为"`type instanceof Type.NestedType || type.isVariantType()`"。这样 Variant 列的 `eq`/`in` 谓词也会直接返回 `ROWS_MIGHT_MATCH`，不尝试基于统计信息跳过行组，保证正确性。

同时重构了测试：将原有两个 Variant notNull 测试中重复的"写 Parquet 文件 → 打开 reader → 构造过滤器 → 断言"逻辑抽取为 `shouldReadVariant` 辅助方法，并新增 `testVariantFieldEq` 和 `testVariantFieldIn` 两个测试用例，验证 Variant 列上的 `equal` 和 `in` 谓词都返回 `shouldRead=true`（即不跳过行组）。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetricsRowGroupFilter.java` (+12/-6 lines, 实际各 +3/-3)

**修改目的**：在 `eq` 和 `in` 谓词评估中，将 Variant 类型与嵌套类型同等对待，不进行行组级下推过滤。

**工作逻辑**：
- 在 `eq(BoundReference, Literal)` 方法中：原条件 `if (schema.findType(id) instanceof Type.NestedType)` 改为先取出 `Type type = schema.findType(id)`，再判断 `if (type instanceof Type.NestedType || type.isVariantType())`。命中则返回 `ROWS_MIGHT_MATCH`。
- 在 `in(BoundReference, Set)` 方法中：做相同改动。
- 注释从"When filtering nested types notNull() is implicit filter passed even though complex filters aren't pushed down in Parquet. Leave all nested column type filters to be evaluated post scan."更新为"Leave all nested column type and variant type filters to be evaluated post scan."

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java` (+82/-28 lines)

**修改目的**：重构 Variant 测试并新增 `eq`/`in` 谓词的测试覆盖。

**工作逻辑**：
- 抽取 `shouldReadVariant(Expression, List<GenericRecord>)` 辅助方法：写入 Parquet 文件，打开 reader，构造 `ParquetMetricsRowGroupFilter`（第三个参数 `true` 表示按嵌套处理），返回 `shouldRead` 结果。该方法假设格式为 Parquet。
- 原有 `testVariantFieldNotNull` 和 `testVariantFieldAllNulls` 改为调用 `shouldReadVariant`，简化代码。
- 新增 `testVariantFieldEq`：构造一个含键值对 `{"k":"v0"}` 的 Variant，写入两条记录（一条非空、一条 null），对 `variant_field` 应用 `equal("variant_field", v0)`，断言 `shouldRead=true`（eq 谓词留到扫描后评估）。
- 新增 `testVariantFieldIn`：构造两个 Variant（v0、v1），数据中只含 v0，对 `variant_field` 应用 `in("variant_field", v0, v1)`，断言 `shouldRead=true`（in 谓词留到扫描后评估）。
- 新增 `createVariantWithKey(VariantMetadata, String)` 辅助方法：创建含单个键值对的 Variant 对象。
- 新增 `createVariantRecords(Variant)` 辅助方法：创建两条记录（id=0 含 Variant，id=1 为 null）。

## 总结

本提交修复了 `ParquetMetricsRowGroupFilter` 对 Variant 类型列 `eq`/`in` 谓词的处理：将 Variant 与嵌套类型同等对待，直接返回 `ROWS_MIGHT_MATCH` 而不尝试基于统计信息跳过行组。这避免了因 Variant 二进制表示的 min/max 比较无意义而导致的错误跳过，保证查询正确性。核心改动仅两处条件判断，配合测试重构和新增的 `eq`/`in` 测试用例，确保 Variant 列上的等值和包含谓词都会留到扫描后评估。这是 Iceberg v3 Variant 类型支持（2666 系列）在 Parquet 行组过滤方面的完善。
