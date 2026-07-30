# 提交 2305：Spark 3.5, 4.0: Prevent unnecessary failure when executing DML queries with identifier fields (#13435)

## 提交信息

- **序号**：2305 / 4088
- **哈希**：f24f0c093e55743c291b5835cd19931d1ca787d0
- **短哈希**：f24f0c093
- **日期**：2025-07-01 19:21:19 -0600
- **作者**：Szehon Ho
- **提交说明**：Spark 3.5, 4.0: Prevent unnecessary failure when executing DML queries with identifier fields (#13435)
- **PR/Issue**：#13435

## 总体目的

本提交修复了当表有标识符字段（identifier fields）时，执行 DML（数据操作语言）查询可能不必要地失败的问题。

Iceberg 表可以定义标识符字段（identifier fields），用于唯一标识表中的行。当 `SparkScanBuilder` 构建元数据列的 Schema 时，它会将原表的标识符字段 ID 传递到新的 Schema 中。然而，元数据列 Schema 中的字段 ID 是重新分配的，与原表的字段 ID 不对应。这导致标识符字段 ID 在元数据 Schema 中引用了不存在的字段，从而在 Spark 执行 DML 查询（如 UPDATE、DELETE、MERGE）时引发验证失败。

修复方案是在构建元数据列 Schema 时，不传递原表的标识符字段 ID（使用空集合代替），因为元数据列 Schema 本身不需要标识符字段约束。

## 如何达成设计目的

在 `SparkScanBuilder` 中，当构建元数据列 Schema 时，将 `table.schema().identifierFieldIds()` 替换为 `ImmutableSet.of()`（空集合）。这样，元数据列 Schema 不会携带任何标识符字段约束，避免了 Spark 在验证时因找不到对应字段而失败。

修改同时应用于 Spark 3.5 和 Spark 4.0 两个版本，保持一致性。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+2/-1 lines)

**修改目的**：在构建元数据列 Schema 时移除标识符字段约束。

**工作逻辑**：在 `metaColumnsSchema` 方法（约第 382 行）中，`new Schema(metaColumnFields, table.schema().identifierFieldIds(), ...)` 修改为 `new Schema(metaColumnFields, ImmutableSet.of(), ...)`。添加了 `ImmutableSet` 的导入。这样元数据列 Schema 不再包含标识符字段 ID，Spark 不会尝试验证这些字段的存在性。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java` (+13/-1 lines)

**修改目的**：添加测试验证有标识符字段时 DML 查询能正常工作。

**工作逻辑**：
- 将测试 schema 中的 `id` 字段从 `optional` 改为 `required`（标识符字段必须是必需的）
- 新增 `testIdentifierFields` 测试：先设置 `id` 为标识符字段，然后执行 INSERT 操作，再查询验证结果。如果修复未生效，INSERT 操作会因标识符字段验证而失败。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+2/-1 lines)

**修改目的**：与 Spark 3.5 相同的修复。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java` (+13/-1 lines)

**修改目的**：与 Spark 3.5 相同的测试。

## 总结

本提交修复了标识符字段导致 DML 查询不必要失败的 bug。根本原因是元数据列 Schema 错误地携带了原表的标识符字段约束，而这些字段 ID 在元数据 Schema 中不存在。修复方案简洁有效：在构建元数据列 Schema 时使用空集合代替标识符字段 ID。修改同时应用于 Spark 3.5 和 4.0，并附带了验证测试。
