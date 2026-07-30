# 提交 4032：Spark 4.1: Test geometry and geography DML and fall back from vectorized reads (#17149)

## 提交信息

- **序号**：4032 / 4088
- **哈希**：8b26043abe450ef7fbef69cd8a53c5af30533ba4
- **短哈希**：8b26043ab
- **日期**：2026-07-14 13:54:22 -0700
- **作者**：Xin Huang
- **提交说明**：Spark 4.1: Test geometry and geography DML and fall back from vectorized reads (#17149)
- **PR/Issue**：#17149

## 总体目的

本提交完成两件事：
1. **向量化读取回退**：修改 `SparkBatch.supportsParquetBatchReads`，使 geometry/geography 类型列回退到非向量化读取。这两种类型虽然是 Iceberg 的原始类型（primitive type），但 Spark Arrow 目前还没有对应的 vector 类型，若走向量化批量读取会失败。需要显式排除，让它们走非向量化的 reader 路径（提交 4001 实现的 `SparkParquetReaders`）。
2. **DML 测试**：扩展 `TestSparkGeospatial` 测试，覆盖 geometry/geography 类型的 DML 操作（INSERT/UPDATE/DELETE 等）和读写端到端流程。

## 如何达成设计目的

1. `supportsParquetBatchReads` 中先检查是否为 metadata 列（允许），再判断是否为 GEOMETRY/GEOGRAPHY（不允许向量化），最后才是 `isPrimitiveType()`。注释更新说明 geometry/geography 是原始类型但无 Arrow vector。
2. 测试类新增 INSERT、UPDATE、DELETE 等 DML 场景的测试，验证地理空间类型在 Spark SQL 上的完整生命周期。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+11/-2 lines)

**修改目的**：让 geometry/geography 回退到非向量化读取。

**工作逻辑**：
```java
private boolean supportsParquetBatchReads(Types.NestedField field) {
  if (MetadataColumns.isMetadataColumn(field.fieldId())) {
    return true;
  }
  Type type = field.type();
  // Geometry and geography are primitive types but have no Arrow vector yet, so they must be
  // read through the non-vectorized reader.
  if (type.typeId() == Type.TypeID.GEOMETRY || type.typeId() == Type.TypeID.GEOGRAPHY) {
    return false;
  }
  return type.isPrimitiveType();
}
```
原实现 `field.type().isPrimitiveType() || MetadataColumns.isMetadataColumn(...)` 会让 geometry/geography 走向量化路径（因为它们是 primitive），但现在显式排除，使它们走 `SparkParquetReaders` 的非向量化 reader（4001 实现的 `GeometryReader`/`GeographyReader`）。

注释也更新：`only primitives or metadata columns are projected, excluding geometry and geography which are primitives with no Arrow vector yet`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkGeospatial.java` (+190/-4 lines)

**修改目的**：扩展地理空间类型 DML 和读写测试。

**工作逻辑**：新增测试覆盖：
- INSERT geometry/geography 值并读取验证。
- UPDATE/DELETE 含地理空间列的行。
- 向量化与非向量化读取路径的端到端验证。
- 与 4001 的 reader/writer 配合验证完整流程（具体见文件）。

## 总结

本提交补齐了 Spark 4.1 地理空间类型支持的读取路径细节：显式让 geometry/geography 回退到非向量化 reader（因无 Arrow vector），避免走向量化批量读取失败。同时扩展测试覆盖 DML 操作和端到端读写流程。这与 4001（读写实现）、4010（metrics 测试）共同构成了 Spark 4.1 地理空间类型的完整支持。
