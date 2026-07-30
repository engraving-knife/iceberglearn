# 提交 2751：Data, Parquet: Fix UUID ClassCastException when reading Parquet files with UUIDs (#14027)

## 提交信息

- **序号**：2751 / 4088
- **哈希**：ef4007964e364566d6b8ee4951dd3eb7ea70a8c9
- **短哈希**：ef4007964
- **日期**：2025-10-15 17:05:40 +0200
- **作者**：Andre Luis Anastacio
- **提交说明**：Data, Parquet: Fix UUID ClassCastException when reading Parquet files with UUIDs (#14027)
- **PR/Issue**：#14027

## 总体目的

本提交修复了在读取包含 UUID 列的 Parquet 文件时，对 UUID 列应用行组过滤器（RowGroupFilter / DictionaryRowGroupFilter）会抛出 `ClassCastException` 的缺陷。

背景在于：Iceberg 在 Parquet 中将 UUID 存储为 16 字节的 `FIXED_LEN_BYTE_ARRAY`（对应 Parquet 的 `Binary` 类型）。当用户对 UUID 列使用谓词（如 `equal`、`in`、`lessThan` 等）进行过滤时，过滤器需要将 Parquet 统计信息中的 `Binary` 值转换为 Iceberg 的 `UUID` 类型，以便与表达式中的常量进行比较。

问题根因在于 `ParquetConversions` 中缺少对 `UUID` 类型的转换分支。当过滤器尝试从 Parquet 的列统计信息中读取值时，由于没有对应的转换函数，会沿用默认逻辑，导致类型不匹配，抛出 `ClassCastException`。这会让任何针对 UUID 列的下推过滤失败，影响查询的正确性与可用性。

## 如何达成设计目的

修改方案分为两部分：

1. **核心修复**：在 `ParquetConversions.fromParquetNative` 方法中新增一个分支，处理 Iceberg 类型为 `UUID` 的情况。当 Parquet 列的原始类型是 `BINARY` 或 `FIXED_LEN_BYTE_ARRAY`（即 Parquet 的 `Binary`）时，通过 `UUIDUtil.convert(((Binary) binary).toByteBuffer())` 将字节数组转换为 `java.util.UUID`，从而让过滤器后续的比较逻辑能够正确进行。

2. **测试覆盖**：在 `TestMetricsRowGroupFilter` 和 `TestDictionaryRowGroupFilter` 两个测试类中新增 `testUUID` 测试用例，覆盖 UUID 列上的各种谓词（`equal`、`notEqual`、`lessThan`、`lessThanOrEqual`、`greaterThan`、`greaterThanOrEqual`、`isNull`、`notNull`、`in`、`notIn`），并新增了 `uuid_col` 字段到测试 schema 与数据生成逻辑中，确保过滤器在统计信息和字典两种场景下都能正确处理 UUID。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetConversions.java` (+2/-0 lines)

**修改目的**：为核心修复点，新增 UUID 类型从 Parquet `Binary` 到 Iceberg `UUID` 的转换逻辑。

**工作逻辑**：在 `fromParquetNative` 方法中，已有针对 DOUBLE/FLOAT、LONG/INT32 等类型不匹配情况的转换分支。本次在末尾新增一个 `else if` 分支：当 Iceberg 类型为 `Type.TypeID.UUID` 时，返回一个 lambda `binary -> UUIDUtil.convert(((Binary) binary).toByteBuffer())`，将 Parquet 的 `Binary` 对象转换为 `ByteBuffer`，再由 `UUIDUtil` 转为 `java.util.UUID`。这样过滤器读取统计信息时就能拿到正确类型的对象。

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java` (+65/-1 lines)

**修改目的**：为基于统计信息的行组过滤器新增 UUID 列的测试覆盖。

**工作逻辑**：
- 在测试 schema（`SCHEMA` 与下划线版本 `UNDERSCORE_SCHEMA`）中新增字段 `uuid_col`（field id 18）。
- 新增常量 `UUID_WITH_ZEROS = "00000000-0000-0000-0000-000000000000"`，用于测试数据。
- 在数据写入逻辑中，偶数行写入 `UUID_WITH_ZEROS`，奇数行写入 `null`，以同时覆盖非空与空值场景。
- 新增 `testUUID` 测试方法（仅适用于 Parquet 格式），覆盖了 13 种谓词场景，验证过滤器的 `shouldRead` 返回结果符合预期：例如列中存在目标 UUID 时 `equal` 应返回 true；列中不存在 `nonExistentUuid` 时 `equal` 应返回 false（可跳过该行组）；`lessThan`/`greaterThan` 对全零 UUID 的边界判定等。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestDictionaryRowGroupFilter.java` (+94/-1 lines)

**修改目的**：为基于字典的行组过滤器新增 UUID 列的测试覆盖。

**工作逻辑**：与 `TestMetricsRowGroupFilter` 类似，在 schema 中新增 `uuid_col` 字段（field id 16），新增 `UUID_WITH_ZEROS` 常量与数据生成逻辑。新增的 `testUUID` 方法首先假设该列使用 `RLE_DICTIONARY` 编码，然后逐一验证 13 种谓词场景。测试通过构造 `ParquetDictionaryRowGroupFilter` 实例并调用 `shouldRead` 来检查字典中是否包含目标 UUID，从而决定是否可跳过行组。

## 总结

本提交修复了一个影响 UUID 列下推过滤的实际缺陷：由于 `ParquetConversions` 缺少 UUID 转换分支，导致对 UUID 列应用行组过滤时抛出 `ClassCastException`。修复方式简洁（核心改动仅 2 行），并通过新增的两组测试（统计信息过滤与字典过滤）全面覆盖了 UUID 列上各类谓词的过滤行为，确保过滤器能够正确地基于 UUID 统计信息或字典内容决定是否跳过行组，提升了查询效率与正确性。
