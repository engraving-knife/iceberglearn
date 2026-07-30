# 提交 3607：Spark, Hive: Fix snapshot procedure for tables with Variant columns (#15964)

## 提交信息

- **序号**：3607 / 4088
- **哈希**：d71583f58859131668cbf85b0df293cd32ef165a
- **短哈希**：d71583f58
- **日期**：2026-04-28 15:21:17 +0200
- **作者**：Neelesh Salian
- **提交说明**：Spark, Hive: Fix snapshot procedure for tables with Variant columns (#15964)
- **PR/Issue**：#15964

## 总体目的

这个提交修复了 Spark 和 Hive 中 snapshot（快照）迁移过程在处理包含 Variant 类型列的表时失败的问题。

Iceberg 的 snapshot 过程用于将外部表（如 Parquet/ORCA/Avro 格式的 Hive 表）迁移为 Iceberg 表。在迁移过程中，需要确定源表的文件格式。之前的逻辑直接使用分区的 serde（序列化/反序列化器）或表的 provider 作为文件格式，但当表包含 Variant 类型列时，serde 可能不是一个标准的文件格式名称（如 Parquet/ORC/Avro），导致迁移失败。

此外，Hive schema 转换工具在遇到 Variant 类型时也没有对应的映射，会抛出异常。

## 如何达成设计目的

1. 在 `SparkTableUtil` 中新增 `resolveFileFormat()` 方法，通过多级回退策略解析文件格式：先检查分区 serde，再检查表级 serde，最后使用表 provider。每个级别都通过 `isKnownFileFormat()` 验证是否为已知的文件格式（parquet/avro/orc），只有验证通过才使用。

2. 在 `HiveSchemaUtil` 中为 Variant 类型添加映射，返回 "unknown" 而不是抛出异常。

3. 在 Spark 4.0 和 4.1 中都应用了相同的修改，并添加了测试用例。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveSchemaUtil.java` (+2/-0 lines)

**修改目的**：为 Variant 类型添加 Hive 类型映射。

**工作逻辑**：
在 `hiveTypeFromIcebergType` 方法中添加 Variant 类型的处理：
```java
case VARIANT:
  return "unknown";
```
Hive 没有 Variant 类型，返回 "unknown" 避免抛出异常，使 schema 转换能继续进行。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+30/-10 lines)

**修改目的**：改进文件格式解析逻辑，支持 Variant 列的表。

**工作逻辑**：

1. **新增 `resolveFileFormat()` 方法**：
```java
private static String resolveFileFormat(String partitionSerde, CatalogTable table) {
  if (partitionSerde != null && isKnownFileFormat(partitionSerde)) {
    return partitionSerde;
  }
  Option<String> serde = table.storage().serde();
  if (serde.nonEmpty() && isKnownFileFormat(serde.get())) {
    return serde.get();
  }
  Preconditions.checkArgument(
      table.provider().nonEmpty(),
      "Could not determine table format from serde %s and no provider set",
      serde.getOrElse(() -> "unknown"));
  return table.provider().get();
}
```
多级回退：分区 serde → 表级 serde → 表 provider，每级都验证是否为已知文件格式。

2. **新增 `isKnownFileFormat()` 方法**：
```java
private static boolean isKnownFileFormat(String serde) {
  String lowerSerde = serde.toLowerCase(Locale.ROOT);
  return lowerSerde.contains("parquet")
      || lowerSerde.contains("avro")
      || lowerSerde.contains("orc");
}
```
通过检查 serde 名称是否包含 parquet/avro/orc 来判断是否为已知文件格式。

3. **修改 `toSparkPartition()` 和 `importUnpartitionedSparkTable()` 方法**：使用 `resolveFileFormat()` 替代原来直接使用 serde 或 provider 的逻辑。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+30/-10 lines)

**修改目的**：在 Spark 4.0 中应用相同的修复。

**工作逻辑**：与 Spark 4.1 完全相同的修改。

### 测试文件 (spark 4.0 和 4.1)

**修改目的**：添加包含 Variant 列的表的 snapshot 测试。

**工作逻辑**：
在 `TestSnapshotTableProcedure.java` 中添加测试，验证包含 Variant 列的表可以正确进行 snapshot 迁移。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveSchemaUtil.java` (+7/-0 lines)

**修改目的**：测试 Variant 类型的 Hive 映射。

## 总结

这个提交修复了 snapshot 迁移过程在处理包含 Variant 类型列的表时失败的问题。通过改进文件格式解析逻辑（多级回退+格式验证）和为 Variant 类型添加 Hive 映射，使 Iceberg 能够正确迁移包含 Variant 列的表。修复同时应用于 Spark 4.0 和 4.1，并配有完整的测试覆盖。
