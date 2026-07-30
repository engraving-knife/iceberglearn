# 提交 3646：Fix for vectorized builder variant handling (#16087)

## 提交信息

- **序号**：3646 / 4088
- **哈希**：da5ffce9a957130b562676680fefa2b8e2ac9d0a
- **短哈希**：da5ffce9a
- **日期**：2026-05-05 19:55:43 -0700
- **作者**：Neelesh Salian
- **提交说明**：Fix for vectorized builder variant handling (#16087)
- **PR/Issue**：#16087

## 总体目的

这个提交修复了 `VectorizedReaderBuilder` 在处理 Variant 类型字段时的问题。Variant 类型是 Iceberg v3 引入的新类型（用于存储半结构化数据），但向量化读取（vectorized read）目前尚不支持 Variant 字段。

此前，`VectorizedReaderBuilder` 没有为 Variant 类型定义专门的 visitor 方法。当 Parquet 文件包含 Variant 列且该列在读取投影中时，builder 会以不正确的方式处理（可能导致读取错误或异常行为），而非明确地抛出"不支持"的异常或回退到非向量化读取。本提交在 `VectorizedReaderBuilder` 中新增 `variant` 方法，当 Variant 字段在投影中时明确抛出 `UnsupportedOperationException`，从而使上层（如 Spark）能感知并回退到非向量化读取路径；当 Variant 字段不在投影中时则正常跳过，不影响其他列的向量化读取。

## 如何达成设计目的

在 `VectorizedReaderBuilder` 中重写 `variant` visitor 方法：当 Iceberg 的 `VariantType` 不为 null（即该字段在投影中）时，抛出 `UnsupportedOperationException`，提示向量化读取尚不支持 variant 字段；否则返回 null（跳过）。这样上层框架（如 Spark 的 MERGE INTO 等操作）在遇到 variant 列时会自动回退到非向量化读取，避免崩溃。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedReaderBuilder.java` (+10 lines)

**修改目的**：为 Variant 类型新增向量化读取 visitor 方法，明确不支持。

**工作逻辑**：
```java
@Override
public VectorizedReader<?> variant(
    Types.VariantType iVariant, GroupType variant, VectorizedReader<?> result) {
  if (iVariant != null) {
    throw new UnsupportedOperationException(
        "Vectorized reads are not supported yet for variant fields");
  }
  return null;
}
```
当 `iVariant` 不为 null 表示该 variant 字段在读取投影中，此时抛出异常触发回退；为 null 时（不在投影中）返回 null 跳过。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestVectorizedReaderBuilder.java` (+92 lines, new)

**修改目的**：新增 VectorizedReaderBuilder 对 Variant 处理的单元测试。

**工作逻辑**：
1. `testVariantNotSupportedInVectorizedReads`：构造含 Integer + Variant 列的 schema 和 Parquet schema，验证访问时抛出 `UnsupportedOperationException` 且消息包含 "Vectorized reads are not supported yet for variant fields"。
2. `testVariantSkippedWhenNotInProjection`：投影中只含 Integer 列（不含 Variant），验证访问不抛异常，说明 variant 不在投影时正常跳过。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkVariantRead.java` (+49 lines)

**修改目的**：新增 Spark 4.0 的 MERGE INTO 与 Variant 集成测试。

**工作逻辑**：`testMergeIntoWithVariant(boolean vectorized)` 参数化测试，分别在开启和关闭向量化的情况下执行 MERGE INTO 操作（涉及 variant 列），验证无论向量化设置如何，MERGE INTO 都不应崩溃（variant 列会回退到非向量化读取）。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkVariantRead.java` (+49 lines)

**修改目的**：同上，为 Spark 4.1 添加相同的 MERGE INTO 与 Variant 测试。

## 总结

这个提交修复了 `VectorizedReaderBuilder` 对 Variant 类型字段的处理：当 variant 列在读取投影中时明确抛出 `UnsupportedOperationException` 以触发非向量化回退，当不在投影中时正常跳过。这使得含 Variant 列的表在进行向量化读取或 MERGE INTO 等操作时不会崩溃，而是优雅地回退到非向量化路径。改动包含核心修复和针对 Spark 4.0/4.1 的集成测试及 Arrow 模块的单元测试。
